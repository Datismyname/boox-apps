package com.locked.launcher

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipFile

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var tvHeaderTitle: TextView
    private lateinit var gridBooksOrApps: GridView
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
    private val coverCache = mutableMapOf<String, Bitmap>()
    private val progressMap = Properties()

    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        if (isUnlocked) {
            isUnlocked = false
            showingAppsMode = false
            applyLockState()
            Toast.makeText(this, "تم القفل التلقائي", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        requestStoragePermission()
        setupKioskPolicyIfOwner()

        prefs = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        gridBooksOrApps = findViewById(R.id.gridBooksOrApps)
        topControlBar = findViewById(R.id.topControlBar)
        searchBar = findViewById(R.id.searchBar)
        btnToggleAppsView = findViewById(R.id.btnToggleAppsView)
        btnSystemSettings = findViewById(R.id.btnSystemSettings)
        btnWifi = findViewById(R.id.btnWifi)
        btnLockToggle = findViewById(R.id.btnLockToggle)

        loadBooks()
        loadProgressData()
        loadInstalledApps()
        applyLockState()

        btnLockToggle.setOnClickListener {
            resetAutoLockTimer()
            if (isUnlocked) {
                isUnlocked = false
                showingAppsMode = false
                applyLockState()
                Toast.makeText(this, "تم قفل اللانشر", Toast.LENGTH_SHORT).show()
            } else {
                promptForPin()
            }
        }

        btnToggleAppsView.setOnClickListener {
            resetAutoLockTimer()
            showingAppsMode = !showingAppsMode
            btnToggleAppsView.text = if (showingAppsMode) "عرض الكتب" else "التطبيقات"
            tvHeaderTitle.text = if (showingAppsMode) "التطبيقات" else "المكتبة"
            gridBooksOrApps.numColumns = if (showingAppsMode) 2 else 3
            refreshGrid("")
        }

        btnSystemSettings.setOnClickListener {
            resetAutoLockTimer()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }

        btnWifi.setOnClickListener {
            resetAutoLockTimer()
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                resetAutoLockTimer()
                refreshGrid(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadBooks()
        loadProgressData()
        if (!showingAppsMode) refreshGrid(searchBar.text.toString())
        updateKioskLockState()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (isUnlocked) resetAutoLockTimer()
    }

    private fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        if (isUnlocked) {
            autoLockHandler.postDelayed(autoLockRunnable, 120_000L) // دقيقتان
        }
    }

    private fun setupKioskPolicyIfOwner() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, AdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(admin, arrayOf(packageName, "com.locked.reader"))
            }
        } catch (_: Exception) {}
    }

    private fun updateKioskLockState() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(packageName)) {
                if (isUnlocked) {
                    stopLockTask()
                } else {
                    startLockTask()
                }
            }
        } catch (_: Exception) {}
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
        gridBooksOrApps.numColumns = 3

        if (isUnlocked) {
            btnLockToggle.setImageResource(android.R.drawable.ic_partial_secure)
            topControlBar.visibility = View.VISIBLE
            searchBar.visibility = View.VISIBLE
            btnToggleAppsView.visibility = View.VISIBLE
            btnSystemSettings.visibility = View.VISIBLE
            btnWifi.visibility = View.VISIBLE
            resetAutoLockTimer()
        } else {
            autoLockHandler.removeCallbacks(autoLockRunnable)
            btnLockToggle.setImageResource(android.R.drawable.ic_lock_lock)
            tvHeaderTitle.text = "المكتبة"
            searchBar.visibility = if (allowSearch) View.VISIBLE else View.GONE
            btnWifi.visibility = if (allowWifi) View.VISIBLE else View.GONE
            btnToggleAppsView.visibility = View.GONE
            btnSystemSettings.visibility = View.GONE
            topControlBar.visibility = if (allowSearch || allowWifi) View.VISIBLE else View.GONE
        }
        updateKioskLockState()
        refreshGrid("")
    }

    private fun loadBooks() {
        val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
        if (!booksDir.exists()) booksDir.mkdirs()
        allBooks = booksDir.listFiles { f ->
            f.extension.lowercase() in listOf("pdf", "epub", "txt")
        }?.sortedBy { it.name } ?: emptyList()
    }

    private fun loadProgressData() {
        progressMap.clear()
        val stateFile = File(Environment.getExternalStorageDirectory(), "MyBooks/.reader_state.properties")
        if (stateFile.exists()) {
            try {
                FileInputStream(stateFile).use { progressMap.load(it) }
            } catch (_: Exception) {}
        }
    }

    private fun loadInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        allApps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }
    }

    private fun refreshGrid(query: String) {
        if (showingAppsMode && isUnlocked) {
            val appNames = allApps.map { it.loadLabel(packageManager).toString() }
            gridBooksOrApps.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, appNames)
            gridBooksOrApps.setOnItemClickListener { _, _, pos, _ ->
                val launch = packageManager.getLaunchIntentForPackage(allApps[pos].activityInfo.packageName)
                if (launch != null) startActivity(launch)
            }
            gridBooksOrApps.setOnItemLongClickListener(null)
            return
        }

        val hiddenBooks = prefs.getStringSet("HIDDEN_BOOKS", emptySet()) ?: emptySet()
        val visibleBooks = if (isUnlocked) allBooks else allBooks.filter { it.name !in hiddenBooks }
        val filteredBooks = if (query.isBlank()) visibleBooks
        else visibleBooks.filter { it.nameWithoutExtension.contains(query, ignoreCase = true) }

        gridBooksOrApps.adapter = BookCoverAdapter(filteredBooks, hiddenBooks)

        gridBooksOrApps.setOnItemClickListener { _, _, position, _ ->
            if (filteredBooks.isNotEmpty()) {
                openBookInReader(filteredBooks[position])
            }
        }

        gridBooksOrApps.setOnItemLongClickListener { _, _, position, _ ->
            if (isUnlocked && filteredBooks.isNotEmpty()) {
                resetAutoLockTimer()
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
                refreshGrid(searchBar.text.toString())
            }
            true
        }
    }

    inner class BookCoverAdapter(
        private val books: List<File>,
        private val hiddenBooks: Set<String>
    ) : BaseAdapter() {
        override fun getCount(): Int = books.size
        override fun getItem(position: Int): Any = books[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_book_grid, parent, false)

            val book = books[position]
            val ivCover = view.findViewById<ImageView>(R.id.ivBookCover)
            val tvFormat = view.findViewById<TextView>(R.id.tvBookFormat)
            val tvProgress = view.findViewById<TextView>(R.id.tvBookProgress)
            val tvTitle = view.findViewById<TextView>(R.id.tvBookTitle)

            val border = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(3, Color.BLACK)
            }
            (ivCover.parent as View).background = border

            val badgeBorder = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(2, Color.BLACK)
            }
            tvFormat.background = badgeBorder
            tvFormat.text = book.extension.lowercase()

            tvProgress.background = badgeBorder
            val pct = progressMap.getProperty("pct_${book.name}", "0")
            tvProgress.text = "$pct%"

            val prefix = if (isUnlocked && book.name in hiddenBooks) "[مخفي] " else ""
            tvTitle.text = "$prefix${book.nameWithoutExtension}"

            ivCover.setImageBitmap(getBookCoverBitmap(book))
            return view
        }
    }

    private fun getBookCoverBitmap(file: File): Bitmap {
        coverCache[file.absolutePath]?.let { return it }
        val bmp: Bitmap? = when (file.extension.lowercase()) {
            "pdf" -> extractPdfCover(file)
            "epub" -> extractEpubCover(file)
            else -> null
        }
        val finalBmp = bmp ?: createFallbackCover(file.nameWithoutExtension)
        coverCache[file.absolutePath] = finalBmp
        return finalBmp
    }

    private fun extractPdfCover(file: File): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } else {
                renderer.close(); pfd.close(); null
            }
        } catch (_: Exception) { null }
    }

    private fun extractEpubCover(file: File): Bitmap? {
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                val coverEntry = entries.firstOrNull {
                    !it.isDirectory && it.name.contains("cover", ignoreCase = true) &&
                            (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true))
                } ?: entries.firstOrNull {
                    !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true))
                }
                if (coverEntry != null) zip.getInputStream(coverEntry).use { BitmapFactory.decodeStream(it) } else null
            }
        } catch (_: Exception) { null }
    }

    private fun createFallbackCover(title: String): Bitmap {
        val bmp = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F4F4F4"))
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(title.take(16), 150f, 200f, paint)
        return bmp
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
            .setTitle("تخصيص وضع القفل (اضغط مطولاً على كتاب لإخفائه)")
            .setMultiChoiceItems(features, checked) { _, which, isChecked ->
                resetAutoLockTimer()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                gridBooksOrApps.smoothScrollBy(450, 0); true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                gridBooksOrApps.smoothScrollBy(-450, 0); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {}
}
