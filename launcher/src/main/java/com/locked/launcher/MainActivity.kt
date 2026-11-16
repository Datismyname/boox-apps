package com.locked.launcher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var tvHeaderTitle: TextView
    private lateinit var listBooksOrApps: ListView
    private lateinit var topControlBar: LinearLayout
    private lateinit var searchBar: EditText
    private lateinit var btnToggleAppsView: Button
    private lateinit var btnSystemSettings: Button
    private lateinit var btnWifi: Button
    private lateinit var btnLockToggle: ImageButton

    private var isUnlocked = false
    private var showingAppsMode = false
    private var allBooks: List<File> = emptyList()
    private var allApps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        requestStoragePermission()

        prefs = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        listBooksOrApps = findViewById(R.id.listBooksOrApps)
        topControlBar = findViewById(R.id.topControlBar)
        searchBar = findViewById(R.id.searchBar)
        btnToggleAppsView = findViewById(R.id.btnToggleAppsView)
        btnSystemSettings = findViewById(R.id.btnSystemSettings)
        btnWifi = findViewById(R.id.btnWifi)
        btnLockToggle = findViewById(R.id.btnLockToggle)

        loadBooks()
        loadInstalledApps()
        applyLockState()

        btnLockToggle.setOnClickListener {
            if (isUnlocked) {
                isUnlocked = false
                showingAppsMode = false
                applyLockState()
                Toast.makeText(this, "تم قفل اللانشر على الكتب", Toast.LENGTH_SHORT).show()
            } else {
                promptForPin()
            }
        }

        btnToggleAppsView.setOnClickListener {
            showingAppsMode = !showingAppsMode
            btnToggleAppsView.text = if (showingAppsMode) "عرض الكتب" else "التطبيقات"
            tvHeaderTitle.text = if (showingAppsMode) "التطبيقات المثبتة" else "مكتبتي"
            refreshList("")
        }

        btnSystemSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        btnWifi.setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refreshList(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadBooks()
        if (!showingAppsMode) refreshList(searchBar.text.toString())
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun applyLockState() {
        val allowSearch = prefs.getBoolean("ALLOW_SEARCH", false)
        val allowWifi = prefs.getBoolean("ALLOW_WIFI", false)

        if (isUnlocked) {
            btnLockToggle.setImageResource(android.R.drawable.ic_partial_secure)
            topControlBar.visibility = View.VISIBLE
            searchBar.visibility = View.VISIBLE
            btnToggleAppsView.visibility = View.VISIBLE
            btnSystemSettings.visibility = View.VISIBLE
            btnWifi.visibility = View.VISIBLE
        } else {
            btnLockToggle.setImageResource(android.R.drawable.ic_lock_lock)
            tvHeaderTitle.text = "مكتبتي"
            searchBar.visibility = if (allowSearch) View.VISIBLE else View.GONE
            btnWifi.visibility = if (allowWifi) View.VISIBLE else View.GONE
            btnToggleAppsView.visibility = View.GONE
            btnSystemSettings.visibility = View.GONE
            topControlBar.visibility = if (allowSearch || allowWifi) View.VISIBLE else View.GONE
        }
        refreshList("")
    }

    private fun loadBooks() {
        val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
        if (!booksDir.exists()) booksDir.mkdirs()
        allBooks = booksDir.listFiles { f ->
            f.extension.lowercase() in listOf("pdf", "epub", "txt")
        }?.sortedBy { it.name } ?: emptyList()
    }

    private fun loadInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        allApps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }
    }

    private fun refreshList(query: String) {
        if (showingAppsMode && isUnlocked) {
            val appNames = allApps.map { it.loadLabel(packageManager).toString() }
            listBooksOrApps.adapter = createLargeTextAdapter(appNames)
            listBooksOrApps.setOnItemClickListener { _, _, pos, _ ->
                val launch = packageManager.getLaunchIntentForPackage(allApps[pos].activityInfo.packageName)
                if (launch != null) startActivity(launch)
            }
            listBooksOrApps.setOnItemLongClickListener(null)
            return
        }

        // وضع عرض الكتب (الوضع الافتراضي)
        val hiddenBooks = prefs.getStringSet("HIDDEN_BOOKS", emptySet()) ?: emptySet()
        val visibleBooks = if (isUnlocked) allBooks else allBooks.filter { it.name !in hiddenBooks }
        val filteredBooks = if (query.isBlank()) visibleBooks
        else visibleBooks.filter { it.nameWithoutExtension.contains(query, ignoreCase = true) }

        val bookTitles = if (filteredBooks.isEmpty()) {
            listOf("لا توجد كتب في مجلد MyBooks")
        } else {
            filteredBooks.map { file ->
                val prefix = if (isUnlocked && file.name in hiddenBooks) "[مخفي] " else "📖 "
                "$prefix${file.nameWithoutExtension}"
            }
        }

        listBooksOrApps.adapter = createLargeTextAdapter(bookTitles)

        listBooksOrApps.setOnItemClickListener { _, _, position, _ ->
            if (filteredBooks.isEmpty()) return@setOnItemClickListener
            val selectedBook = filteredBooks[position]
            openBookInReader(selectedBook)
        }

        // الضغط المطول على الكتاب أثناء فتح القفل لإخفائه أو إظهاره للقارئ
        listBooksOrApps.setOnItemLongClickListener { _, _, position, _ ->
            if (isUnlocked && filteredBooks.isNotEmpty()) {
                val bookName = filteredBooks[position].name
                val currentHidden = hiddenBooks.toMutableSet()
                if (bookName in currentHidden) {
                    currentHidden.remove(bookName)
                    Toast.makeText(this, "تم إظهار الكتاب", Toast.LENGTH_SHORT).show()
                } else {
                    currentHidden.add(bookName)
                    Toast.makeText(this, "تم إخفاء الكتاب في وضع القفل", Toast.LENGTH_SHORT).show()
                }
                prefs.edit().putStringSet("HIDDEN_BOOKS", currentHidden).apply()
                refreshList(searchBar.text.toString())
            }
            true
        }
    }

    private fun openBookInReader(bookFile: File) {
        try {
            val intent = Intent().apply {
                component = ComponentName("com.locked.reader", "com.locked.reader.ReaderMainActivity")
                putExtra("BOOK_PATH", bookFile.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "تأكد من تثبيت تطبيق القارئ البسيط أولاً", Toast.LENGTH_LONG).show()
        }
    }

    private fun createLargeTextAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                view.setPadding(16, 28, 16, 28)
                view.setTextColor(android.graphics.Color.BLACK)
                return view
            }
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
        val features = arrayOf("إظهار بحث الكتب أثناء القفل", "إظهار الواي فاي أثناء القفل")
        val checked = booleanArrayOf(prefs.getBoolean("ALLOW_SEARCH", false), prefs.getBoolean("ALLOW_WIFI", false))
        AlertDialog.Builder(this)
            .setTitle("تخصيص وضع القفل (اضغط مطولاً على أي كتاب لإخفائه)")
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
