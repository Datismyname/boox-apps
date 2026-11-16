package com.locked.launcher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var gridApps: GridView
    private lateinit var topControlBar: LinearLayout
    private lateinit var searchBar: EditText
    private lateinit var btnSystemSettings: Button
    private lateinit var btnWifi: Button
    private lateinit var btnLockToggle: ImageButton

    private var isUnlocked = false
    private var allApps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
        gridApps = findViewById(R.id.gridApps)
        topControlBar = findViewById(R.id.topControlBar)
        searchBar = findViewById(R.id.searchBar)
        btnSystemSettings = findViewById(R.id.btnSystemSettings)
        btnWifi = findViewById(R.id.btnWifi)
        btnLockToggle = findViewById(R.id.btnLockToggle)

        loadInstalledApps()
        applyLockState()

        btnLockToggle.setOnClickListener {
            if (isUnlocked) {
                isUnlocked = false
                applyLockState()
                Toast.makeText(this, "تم قفل اللانشر", Toast.LENGTH_SHORT).show()
            } else {
                promptForPin()
            }
        }

        btnSystemSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        btnWifi.setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterAppsForDisplay(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applyLockState() {
        val allowSearch = prefs.getBoolean("ALLOW_SEARCH", false)
        val allowWifi = prefs.getBoolean("ALLOW_WIFI", false)

        if (isUnlocked) {
            btnLockToggle.setImageResource(android.R.drawable.ic_partial_secure)
            topControlBar.visibility = View.VISIBLE
            searchBar.visibility = View.VISIBLE
            btnSystemSettings.visibility = View.VISIBLE
            btnWifi.visibility = View.VISIBLE
        } else {
            btnLockToggle.setImageResource(android.R.drawable.ic_lock_lock)
            searchBar.visibility = if (allowSearch) View.VISIBLE else View.GONE
            btnWifi.visibility = if (allowWifi) View.VISIBLE else View.GONE
            btnSystemSettings.visibility = View.GONE
            topControlBar.visibility = if (allowSearch || allowWifi) View.VISIBLE else View.GONE
        }
        filterAppsForDisplay("")
    }

    private fun loadInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        allApps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }
    }

    private fun filterAppsForDisplay(query: String) {
        val allowedPackages = prefs.getStringSet("ALLOWED_APPS", emptySet()) ?: emptySet()
        val baseList = if (isUnlocked || allowedPackages.isEmpty()) allApps
        else allApps.filter { it.activityInfo.packageName in allowedPackages }

        val displayedApps = if (query.isBlank()) baseList
        else baseList.filter { it.loadLabel(packageManager).toString().contains(query, ignoreCase = true) }

        val appNames = displayedApps.map { it.loadLabel(packageManager).toString() }
        gridApps.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, appNames)

        gridApps.setOnItemClickListener { _, _, position, _ ->
            val launchIntent = packageManager.getLaunchIntentForPackage(displayedApps[position].activityInfo.packageName)
            if (launchIntent != null) startActivity(launchIntent)
        }

        gridApps.setOnItemLongClickListener { _, _, position, _ ->
            if (isUnlocked) {
                val pkg = displayedApps[position].activityInfo.packageName
                val currentSet = allowedPackages.toMutableSet()
                if (pkg in currentSet) {
                    currentSet.remove(pkg)
                    Toast.makeText(this, "تم إخفاء التطبيق في وضع القفل", Toast.LENGTH_SHORT).show()
                } else {
                    currentSet.add(pkg)
                    Toast.makeText(this, "تم تثبيت التطبيق ليظهر دائماً", Toast.LENGTH_SHORT).show()
                }
                prefs.edit().putStringSet("ALLOWED_APPS", currentSet).apply()
            }
            true
        }
    }

    private fun promptForPin() {
        val savedPin = prefs.getString("PIN_CODE", "1234")
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "الرمز السري (1234)"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 22f
        }
        AlertDialog.Builder(this)
            .setTitle("فتح قفل اللانشر")
            .setView(input)
            .setPositiveButton("دخول") { _, _ ->
                if (input.text.toString() == savedPin) {
                    isUnlocked = true
                    applyLockState()
                    showAdminFeaturePicker()
                } else {
                    Toast.makeText(this, "رمز خاطئ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showAdminFeaturePicker() {
        val features = arrayOf("إظهار البحث أثناء القفل", "إظهار الواي فاي أثناء القفل")
        val checked = booleanArrayOf(prefs.getBoolean("ALLOW_SEARCH", false), prefs.getBoolean("ALLOW_WIFI", false))
        AlertDialog.Builder(this)
            .setTitle("تخصيص وضع القفل (اضغط مطولاً على أي تطبيق لتثبيته)")
            .setMultiChoiceItems(features, checked) { _, which, isChecked ->
                if (which == 0) prefs.edit().putBoolean("ALLOW_SEARCH", isChecked).apply()
                if (which == 1) prefs.edit().putBoolean("ALLOW_WIFI", isChecked).apply()
            }
            .setNeutralButton("تغيير الرمز السري") { _, _ -> changePinDialog() }
            .setPositiveButton("حفظ", null)
            .show()
    }

    private fun changePinDialog() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "الرمز الجديد" }
        AlertDialog.Builder(this).setTitle("تغيير الرمز السري").setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                if (input.text.isNotBlank()) prefs.edit().putString("PIN_CODE", input.text.toString()).apply()
            }.show()
    }

    override fun onBackPressed() {}
}
