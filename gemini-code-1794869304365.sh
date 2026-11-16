cat << 'EOF' > ~/downloads/update_v6.py
import os

base_dir = os.path.expanduser("~/downloads/Boox_Locked_Apps")

files = {
    # ==========================================
    # 1. ملفات Kiosk Mode (Device Admin) للانشر
    # ==========================================
    "launcher/src/main/res/xml/device_admin_policies.xml": """<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies />
</device-admin>
""",

    "launcher/src/main/java/com/locked/launcher/AdminReceiver.kt": """package com.locked.launcher

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
""",

    "launcher/src/main/AndroidManifest.xml": """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <queries>
        <package android:name="com.locked.reader" />
    </queries>
    <application
        android:label="لانشر القراءة المقفل"
        android:requestLegacyExternalStorage="true"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".AdminReceiver"
            android:description="@string/app_name"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin_policies" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
""",

    "launcher/src/main/res/values/strings.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">لانشر القراءة المقفل</string>
</resources>
""",

    # 2. بطاقة الكتاب في اللانشر مع شارة نسبة الإنجاز أعلى الغلاف
    "launcher/src/main/res/layout/item_book_grid.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="4dp">

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="175dp"
        android:background="#FFFFFF"
        android:padding="2dp">

        <ImageView
            android:id="@+id/ivBookCover"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop"
            android:background="#F5F5F5" />

        <TextView
            android:id="@+id/tvBookProgress"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="top|center_horizontal"
            android:layout_marginTop="4dp"
            android:background="#FFFFFF"
            android:paddingHorizontal="8dp"
            android:paddingVertical="1dp"
            android:text="0%"
            android:textColor="#000000"
            android:textSize="13sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvBookFormat"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="6dp"
            android:background="#FFFFFF"
            android:paddingHorizontal="6dp"
            android:paddingVertical="2dp"
            android:text="PDF"
            android:textColor="#000000"
            android:textSize="13sp"
            android:textStyle="bold" />
    </FrameLayout>

    <TextView
        android:id="@+id/tvBookTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:maxLines="2"
        android:ellipsize="end"
        android:text="اسم الكتاب"
        android:textColor="#000000"
        android:textSize="16sp"
        android:textStyle="bold" />
</LinearLayout>
""",

    # 3. كود اللانشر الرئيسي مع Kiosk Mode + القفل التلقائي + نسب الإنجاز
    "launcher/src/main/java/com/locked/launcher/MainActivity.kt": """package com.locked.launcher

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
""",

    # ==========================================
    # 4. واجهة القارئ المحدثة (تقليب باللمس + إشارات مرجعية + شريط سفلي)
    # ==========================================
    "reader/src/main/res/layout/activity_reader_main.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#FFFFFF">

    <HorizontalScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="#F2F2F2"
        android:padding="6dp">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <ImageButton
                android:id="@+id/btnHomeLauncher"
                android:layout_width="50dp"
                android:layout_height="46dp"
                android:src="@drawable/ic_home_black"
                android:background="#E0E0E0"
                android:contentDescription="الصفحة الرئيسية" />

            <ImageButton
                android:id="@+id/btnReaderLock"
                android:layout_width="46dp"
                android:layout_height="46dp"
                android:layout_marginStart="6dp"
                android:src="@android:drawable/ic_lock_lock"
                android:contentDescription="قفل الإعدادات" />

            <Button
                android:id="@+id/btnZoomIn"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:text="تكبير +"
                android:textSize="19sp"
                android:textStyle="bold" />

            <Button
                android:id="@+id/btnZoomOut"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="تصغير -"
                android:textSize="19sp"
                android:textStyle="bold" />

            <Button
                android:id="@+id/btnSaveBookmark"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="حفظ علامة"
                android:textSize="17sp" />

            <Button
                android:id="@+id/btnGotoBookmark"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="الذهاب للعلامة"
                android:textSize="17sp" />

            <Button
                android:id="@+id/btnDarkMode"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="الوضع الليلي"
                android:textSize="17sp" />

            <Button
                android:id="@+id/btnLineSpacing"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="تباعد الأسطر"
                android:textSize="17sp" />

            <Button
                android:id="@+id/btnSpeak"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="قراءة صوتية"
                android:textSize="17sp" />
        </LinearLayout>
    </HorizontalScrollView>

    <!-- منطقة القراءة مع طبقة لمس جانبية لتقليب الصفحات بدون تمرير -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <WebView
            android:id="@+id/epubWebView"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <ImageView
            android:id="@+id/pdfImageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="fitCenter"
            android:visibility="gone" />

        <!-- منطقة اللمس اليمنى (الصفحة السابقة) -->
        <View
            android:id="@+id/touchZoneRight"
            android:layout_width="90dp"
            android:layout_height="match_parent"
            android:layout_gravity="end"
            android:background="@android:color/transparent" />

        <!-- منطقة اللمس اليسرى (الصفحة التالية) -->
        <View
            android:id="@+id/touchZoneLeft"
            android:layout_width="90dp"
            android:layout_height="match_parent"
            android:layout_gravity="start"
            android:background="@android:color/transparent" />
    </FrameLayout>

    <!-- شريط الحالة السفلي: رقم الصفحة + نسبة البطارية -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="#F2F2F2"
        android:paddingHorizontal="16dp"
        android:paddingVertical="6dp">

        <TextView
            android:id="@+id/tvPageStatus"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="صفحة 1 من 1"
            android:textColor="#000000"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvBatteryStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="البطارية: 100%"
            android:textColor="#000000"
            android:textSize="16sp"
            android:textStyle="bold" />
    </LinearLayout>
</LinearLayout>
""",

    # 5. كود القارئ المتكامل (حفظ تلقائي + إشارات مرجعية + لمس جانبي + بطارية + منع انطفاء الشاشة)
    "reader/src/main/java/com/locked/reader/ReaderMainActivity.kt": """package com.locked.reader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.Html
import android.text.InputType
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipFile

class ReaderMainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var epubWebView: WebView
    private lateinit var pdfImageView: ImageView
    private lateinit var touchZoneRight: View
    private lateinit var touchZoneLeft: View
    private lateinit var tvPageStatus: TextView
    private lateinit var tvBatteryStatus: TextView

    private lateinit var btnHomeLauncher: ImageButton
    private lateinit var btnReaderLock: ImageButton
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button
    private lateinit var btnSaveBookmark: Button
    private lateinit var btnGotoBookmark: Button
    private lateinit var btnDarkMode: Button
    private lateinit var btnLineSpacing: Button
    private lateinit var btnSpeak: Button

    private var isUnlocked = false
    private var fontSize = 26
    private var pdfZoomScale = 1.0f
    private var isDark = false
    private var lineSpacing = 1.4f
    private var currentHtmlContent = ""
    private var currentMode = "TEXT"
    private var currentBookFile: File? = null

    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPageIndex = 0
    private var pdfFileDescriptor: ParcelFileDescriptor? = null

    private var epubChapters: List<String> = emptyList()
    private var currentChapterIndex = 0
    private var currentTextPage = 0
    private var totalTextPages = 1

    private var tts: TextToSpeech? = null
    private val stateProps = Properties()

    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        if (isUnlocked) {
            isUnlocked = false
            updateToolbarVisibility()
            Toast.makeText(this, "تم القفل التلقائي لإعدادات القارئ", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_reader_main)

        requestStoragePermission()
        prefs = getSharedPreferences("BookReaderPrefs", Context.MODE_PRIVATE)
        applyKeepScreenOnPolicy()
        tts = TextToSpeech(this, this)

        epubWebView = findViewById(R.id.epubWebView)
        pdfImageView = findViewById(R.id.pdfImageView)
        touchZoneRight = findViewById(R.id.touchZoneRight)
        touchZoneLeft = findViewById(R.id.touchZoneLeft)
        tvPageStatus = findViewById(R.id.tvPageStatus)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        btnHomeLauncher = findViewById(R.id.btnHomeLauncher)
        btnReaderLock = findViewById(R.id.btnReaderLock)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnSaveBookmark = findViewById(R.id.btnSaveBookmark)
        btnGotoBookmark = findViewById(R.id.btnGotoBookmark)
        btnDarkMode = findViewById(R.id.btnDarkMode)
        btnLineSpacing = findViewById(R.id.btnLineSpacing)
        btnSpeak = findViewById(R.id.btnSpeak)

        epubWebView.settings.javaScriptEnabled = false
        epubWebView.isVerticalScrollBarEnabled = false
        fontSize = prefs.getInt("FONT_SIZE", 26)

        loadStateProps()
        updateBatteryDisplay()
        handleIncomingIntent(intent)
        updateToolbarVisibility()

        // مناطق اللمس الجانبية لتقليب الصفحات بسلاسة (اليسار = التالي، اليمين = السابق)
        touchZoneLeft.setOnClickListener { handleNextPage() }
        touchZoneRight.setOnClickListener { handlePrevPage() }

        btnHomeLauncher.setOnClickListener {
            saveCurrentReadingPosition()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        btnZoomIn.setOnClickListener {
            if (currentMode == "PDF") {
                if (pdfZoomScale < 2.2f) {
                    pdfZoomScale += 0.25f
                    renderPdfPage(currentPdfPageIndex)
                }
            } else if (fontSize < 60) {
                fontSize += 3
                prefs.edit().putInt("FONT_SIZE", fontSize).apply()
                refreshWebViewContent()
            }
        }

        btnZoomOut.setOnClickListener {
            if (currentMode == "PDF") {
                if (pdfZoomScale > 0.75f) {
                    pdfZoomScale -= 0.25f
                    renderPdfPage(currentPdfPageIndex)
                }
            } else if (fontSize > 16) {
                fontSize -= 3
                prefs.edit().putInt("FONT_SIZE", fontSize).apply()
                refreshWebViewContent()
            }
        }

        btnSaveBookmark.setOnClickListener { saveBookmarkForCurrentBook() }
        btnGotoBookmark.setOnClickListener { gotoBookmarkForCurrentBook() }

        btnDarkMode.setOnClickListener {
            isDark = !isDark
            if (currentMode == "PDF") renderPdfPage(currentPdfPageIndex)
            else refreshWebViewContent()
        }

        btnLineSpacing.setOnClickListener {
            lineSpacing = if (lineSpacing == 1.4f) 2.0f else 1.4f
            refreshWebViewContent()
        }

        btnSpeak.setOnClickListener {
            if (tts?.isSpeaking == true) tts?.stop()
            else {
                val plainText = Html.fromHtml(currentHtmlContent, Html.FROM_HTML_MODE_LEGACY).toString().take(3500)
                tts?.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "BOOK_TTS")
            }
        }

        btnReaderLock.setOnClickListener {
            if (isUnlocked) {
                isUnlocked = false
                updateToolbarVisibility()
                Toast.makeText(this, "تم قفل إعدادات القارئ", Toast.LENGTH_SHORT).show()
            } else {
                unlockReaderSettings()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        updateBatteryDisplay()
        if (isUnlocked) resetAutoLockTimer()
    }

    private fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        if (isUnlocked) autoLockHandler.postDelayed(autoLockRunnable, 120_000L)
    }

    private fun applyKeepScreenOnPolicy() {
        if (prefs.getBoolean("KEEP_SCREEN_ON", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateBatteryDisplay() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) tvBatteryStatus.text = "البطارية: $level%"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveCurrentReadingPosition()
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val path = intent?.getStringExtra("BOOK_PATH")
        if (path != null && File(path).exists()) {
            openBookFile(File(path))
            return
        }
        val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
        val first = booksDir.listFiles { f -> f.extension.lowercase() in listOf("pdf", "epub", "txt") }?.firstOrNull()
        if (first != null) openBookFile(first)
    }

    private fun openBookFile(file: File) {
        closePdfRenderer()
        currentBookFile = file
        loadStateProps()

        when (file.extension.lowercase()) {
            "pdf" -> {
                currentMode = "PDF"
                epubWebView.visibility = View.GONE
                pdfImageView.visibility = View.VISIBLE
                pdfZoomScale = 1.0f
                pdfFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(pdfFileDescriptor!!)
                val savedPage = stateProps.getProperty("page_${file.name}", "0").toIntOrNull() ?: 0
                renderPdfPage(savedPage.coerceIn(0, (pdfRenderer!!.pageCount - 1).coerceAtLeast(0)))
            }
            "epub" -> {
                currentMode = "EPUB"
                pdfImageView.visibility = View.GONE
                epubWebView.visibility = View.VISIBLE
                epubChapters = extractEpubChapters(file)
                val savedChap = stateProps.getProperty("chap_${file.name}", "0").toIntOrNull() ?: 0
                currentChapterIndex = savedChap.coerceIn(0, (epubChapters.size - 1).coerceAtLeast(0))
                currentTextPage = stateProps.getProperty("tpage_${file.name}", "0").toIntOrNull() ?: 0
                currentHtmlContent = epubChapters.getOrNull(currentChapterIndex) ?: "<p>تعذر قراءة الكتاب</p>"
                refreshWebViewContent()
            }
            else -> {
                currentMode = "TEXT"
                pdfImageView.visibility = View.GONE
                epubWebView.visibility = View.VISIBLE
                currentTextPage = stateProps.getProperty("tpage_${file.name}", "0").toIntOrNull() ?: 0
                val escaped = file.readText().replace("\\n", "<br/>")
                currentHtmlContent = "<p>$escaped</p>"
                refreshWebViewContent()
            }
        }
    }

    private fun renderPdfPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return
        currentPdfPageIndex = index

        val page = renderer.openPage(index)
        val width = (page.width * 2 * pdfZoomScale).toInt()
        val height = (page.height * 2 * pdfZoomScale).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        pdfImageView.setImageBitmap(bitmap)
        tvPageStatus.text = "صفحة ${index + 1} من ${renderer.pageCount}"
        saveCurrentReadingPosition()
    }

    private fun refreshWebViewContent() {
        val bg = if (isDark) "#000000" else "#FFFFFF"
        val fg = if (isDark) "#FFFFFF" else "#000000"
        val styledHtml = \"\"\"
            <html dir="rtl" lang="ar">
            <head>
            <meta charset="UTF-8">
            <style>
                body {
                    background-color: $bg;
                    color: $fg;
                    font-size: ${fontSize}px;
                    line-height: $lineSpacing;
                    padding: 20px 28px;
                    font-family: sans-serif;
                    word-wrap: break-word;
                }
                img { max-width: 100%; height: auto; }
            </style>
            </head>
            <body>$currentHtmlContent</body>
            </html>
        \"\"\".trimIndent()

        epubWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                epubWebView.postDelayed({
                    val pageH = epubWebView.height.coerceAtLeast(1)
                    val totalH = (epubWebView.contentHeight * epubWebView.scale).toInt().coerceAtLeast(pageH)
                    totalTextPages = ((totalH + pageH - 1) / pageH).coerceAtLeast(1)
                    if (currentTextPage >= totalTextPages) currentTextPage = totalTextPages - 1
                    epubWebView.scrollTo(0, currentTextPage * pageH)
                    updateWebPageStatus()
                }, 150)
            }
        }
        epubWebView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
    }

    private fun updateWebPageStatus() {
        val chapInfo = if (currentMode == "EPUB" && epubChapters.size > 1) " (فصل ${currentChapterIndex + 1}/${epubChapters.size})" else ""
        tvPageStatus.text = "صفحة ${currentTextPage + 1} من $totalTextPages$chapInfo"
        saveCurrentReadingPosition()
    }

    private fun handleNextPage() {
        if (currentMode == "PDF") {
            if (pdfRenderer != null && currentPdfPageIndex < pdfRenderer!!.pageCount - 1) {
                renderPdfPage(currentPdfPageIndex + 1)
            }
        } else {
            val pageH = epubWebView.height
            if (currentTextPage < totalTextPages - 1) {
                currentTextPage++
                epubWebView.scrollTo(0, currentTextPage * pageH)
                updateWebPageStatus()
            } else if (currentMode == "EPUB" && currentChapterIndex < epubChapters.size - 1) {
                currentChapterIndex++
                currentTextPage = 0
                currentHtmlContent = epubChapters[currentChapterIndex]
                refreshWebViewContent()
            }
        }
    }

    private fun handlePrevPage() {
        if (currentMode == "PDF") {
            if (currentPdfPageIndex > 0) renderPdfPage(currentPdfPageIndex - 1)
        } else {
            val pageH = epubWebView.height
            if (currentTextPage > 0) {
                currentTextPage--
                epubWebView.scrollTo(0, currentTextPage * pageH)
                updateWebPageStatus()
            } else if (currentMode == "EPUB" && currentChapterIndex > 0) {
                currentChapterIndex--
                currentTextPage = 0
                currentHtmlContent = epubChapters[currentChapterIndex]
                refreshWebViewContent()
            }
        }
    }

    private fun saveCurrentReadingPosition() {
        val file = currentBookFile ?: return
        var pct = 0
        if (currentMode == "PDF" && pdfRenderer != null && pdfRenderer!!.pageCount > 0) {
            stateProps.setProperty("page_${file.name}", currentPdfPageIndex.toString())
            pct = (((currentPdfPageIndex + 1) * 100) / pdfRenderer!!.pageCount).coerceIn(0, 100)
        } else {
            stateProps.setProperty("chap_${file.name}", currentChapterIndex.toString())
            stateProps.setProperty("tpage_${file.name}", currentTextPage.toString())
            pct = if (currentMode == "EPUB" && epubChapters.isNotEmpty()) {
                (((currentChapterIndex + 1) * 100) / epubChapters.size).coerceIn(0, 100)
            } else {
                (((currentTextPage + 1) * 100) / totalTextPages.coerceAtLeast(1)).coerceIn(0, 100)
            }
        }
        stateProps.setProperty("pct_${file.name}", pct.toString())
        persistStateProps()
    }

    private fun saveBookmarkForCurrentBook() {
        val file = currentBookFile ?: return
        if (currentMode == "PDF") {
            stateProps.setProperty("bm_page_${file.name}", currentPdfPageIndex.toString())
        } else {
            stateProps.setProperty("bm_chap_${file.name}", currentChapterIndex.toString())
            stateProps.setProperty("bm_tpage_${file.name}", currentTextPage.toString())
        }
        persistStateProps()
        Toast.makeText(this, "تم حفظ الإشارة المرجعية", Toast.LENGTH_SHORT).show()
    }

    private fun gotoBookmarkForCurrentBook() {
        val file = currentBookFile ?: return
        if (currentMode == "PDF") {
            val bmPage = stateProps.getProperty("bm_page_${file.name}")?.toIntOrNull()
            if (bmPage != null) {
                renderPdfPage(bmPage)
                Toast.makeText(this, "تم الانتقال للإشارة المحفوظة", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "لا توجد علامة محفوظة لهذا الكتاب", Toast.LENGTH_SHORT).show()
            }
        } else {
            val bmChap = stateProps.getProperty("bm_chap_${file.name}")?.toIntOrNull()
            val bmTpage = stateProps.getProperty("bm_tpage_${file.name}")?.toIntOrNull()
            if (bmTpage != null) {
                currentChapterIndex = bmChap ?: 0
                currentTextPage = bmTpage
                if (currentMode == "EPUB" && epubChapters.isNotEmpty()) {
                    currentHtmlContent = epubChapters[currentChapterIndex.coerceIn(0, epubChapters.size - 1)]
                }
                refreshWebViewContent()
                Toast.makeText(this, "تم الانتقال للإشارة المحفوظة", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "لا توجد علامة محفوظة لهذا الكتاب", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStateProps() {
        val stateFile = File(Environment.getExternalStorageDirectory(), "MyBooks/.reader_state.properties")
        if (stateFile.exists()) {
            try { FileInputStream(stateFile).use { stateProps.load(it) } } catch (_: Exception) {}
        }
    }

    private fun persistStateProps() {
        try {
            val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
            if (!booksDir.exists()) booksDir.mkdirs()
            val stateFile = File(booksDir, ".reader_state.properties")
            FileOutputStream(stateFile).use { stateProps.store(it, "Reader State") }
        } catch (_: Exception) {}
    }

    private fun extractEpubChapters(file: File): List<String> {
        val chapters = mutableListOf<String>()
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                    .filter { !it.isDirectory && (it.name.endsWith(".html", true) || it.name.endsWith(".xhtml", true) || it.name.endsWith(".htm", true)) }
                    .sortedBy { it.name }
                for (entry in entries) {
                    chapters.add(zip.getInputStream(entry).bufferedReader().use { it.readText() })
                }
            }
        } catch (e: Exception) {
            chapters.add("<p>خطأ في فتح الكتاب: ${e.message}</p>")
        }
        return chapters
    }

    private fun closePdfRenderer() {
        try { pdfRenderer?.close(); pdfFileDescriptor?.close() } catch (_: Exception) {}
        pdfRenderer = null; pdfFileDescriptor = null
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun updateToolbarVisibility() {
        btnHomeLauncher.visibility = View.VISIBLE
        btnZoomIn.visibility = View.VISIBLE
        btnZoomOut.visibility = View.VISIBLE

        if (isUnlocked) {
            btnReaderLock.setImageResource(android.R.drawable.ic_partial_secure)
            btnSaveBookmark.visibility = View.VISIBLE
            btnGotoBookmark.visibility = View.VISIBLE
            btnDarkMode.visibility = View.VISIBLE
            btnLineSpacing.visibility = View.VISIBLE
            btnSpeak.visibility = View.VISIBLE
            resetAutoLockTimer()
        } else {
            autoLockHandler.removeCallbacks(autoLockRunnable)
            btnReaderLock.setImageResource(android.R.drawable.ic_lock_lock)
            val showBm = prefs.getBoolean("SHOW_BOOKMARKS", true)
            btnSaveBookmark.visibility = if (showBm) View.VISIBLE else View.GONE
            btnGotoBookmark.visibility = if (showBm) View.VISIBLE else View.GONE
            btnDarkMode.visibility = if (prefs.getBoolean("SHOW_DARK", false)) View.VISIBLE else View.GONE
            btnLineSpacing.visibility = if (prefs.getBoolean("SHOW_SPACING", false)) View.VISIBLE else View.GONE
            btnSpeak.visibility = if (prefs.getBoolean("SHOW_TTS", false)) View.VISIBLE else View.GONE
        }
    }

    private fun unlockReaderSettings() {
        val savedPin = prefs.getString("READER_PIN", "1234")
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "الرمز السري (1234)"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 22f
        }
        AlertDialog.Builder(this)
            .setTitle("فتح إعدادات القارئ")
            .setView(input)
            .setPositiveButton("دخول") { _, _ ->
                if (input.text.toString() == savedPin) {
                    isUnlocked = true
                    updateToolbarVisibility()
                    showAllowedControlsDialog()
                } else {
                    Toast.makeText(this, "الرمز غير صحيح", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showAllowedControlsDialog() {
        val options = arrayOf(
            "إظهار أزرار الإشارة المرجعية",
            "إظهار الوضع الليلي",
            "إظهار تباعد الأسطر",
            "إظهار القراءة الصوتية",
            "منع انطفاء الشاشة أثناء القراءة"
        )
        val states = booleanArrayOf(
            prefs.getBoolean("SHOW_BOOKMARKS", true),
            prefs.getBoolean("SHOW_DARK", false),
            prefs.getBoolean("SHOW_SPACING", false),
            prefs.getBoolean("SHOW_TTS", false),
            prefs.getBoolean("KEEP_SCREEN_ON", true)
        )
        AlertDialog.Builder(this)
            .setTitle("تخصيص القارئ")
            .setMultiChoiceItems(options, states) { _, which, isChecked ->
                resetAutoLockTimer()
                when (which) {
                    0 -> prefs.edit().putBoolean("SHOW_BOOKMARKS", isChecked).apply()
                    1 -> prefs.edit().putBoolean("SHOW_DARK", isChecked).apply()
                    2 -> prefs.edit().putBoolean("SHOW_SPACING", isChecked).apply()
                    3 -> prefs.edit().putBoolean("SHOW_TTS", isChecked).apply()
                    4 -> {
                        prefs.edit().putBoolean("KEEP_SCREEN_ON", isChecked).apply()
                        applyKeepScreenOnPolicy()
                    }
                }
            }
            .setNeutralButton("تغيير الرمز") { _, _ -> changePinDialog() }
            .setPositiveButton("تم", null)
            .show()
    }

    private fun changePinDialog() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "الرمز الجديد" }
        AlertDialog.Builder(this).setTitle("تغيير الرمز السري").setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                if (input.text.isNotBlank()) prefs.edit().putString("READER_PIN", input.text.toString()).apply()
            }.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { handleNextPage(); true }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> { handlePrevPage(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        saveCurrentReadingPosition()
        super.onPause()
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ar") }
    override fun onDestroy() { closePdfRenderer(); tts?.shutdown(); super.onDestroy() }
}
"""
}

for path, content in files.items():
    full_path = os.path.join(base_dir, path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print("تم بناء النسخة المتكاملة v6 بنجاح!")
EOF
python ~/downloads/update_v6.py
cd ~/downloads/Boox_Locked_Apps
git add .
git commit -m "Add bookmarks, auto-save progress, tap pagination, battery bar, auto-lock, and Kiosk mode"
git push