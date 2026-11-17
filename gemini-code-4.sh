cat << 'EOF' > ~/downloads/update_v7.py
import os

base_dir = os.path.expanduser("~/downloads/Boox_Locked_Apps")

files = {
    # 1. إضافة صلاحية المايكروفون والإنترنت (للتعرف الصوتي) في Manifest
    "app/src/main/AndroidManifest.xml": """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="المكتبة الموحدة"
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

    # 2. إضافة مكتبة PDFBox-Android (لاستخراج النصوص المضمنة) و MLKit OCR للصور
    "app/build.gradle": """plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'com.unified.ereader'
    compileSdk 34
    defaultConfig {
        applicationId "com.unified.ereader"
        minSdk 26
        targetSdk 34
        versionCode 2
        versionName "4.0-VoiceOCR"
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
    packaging {
        resources {
            excludes += ['META-INF/DEPENDENCIES', 'META-INF/LICENSE', 'META-INF/NOTICE']
        }
    }
}
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
    implementation 'com.google.mlkit:text-recognition:16.0.0'
}
""",

    # 3. تحديث الواجهة لإضافة زر المايكروفون في المكتبة
    "app/src/main/res/layout/activity_main.xml": """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FFFFFF">

    <!-- 1. واجهة المكتبة واللانشر -->
    <LinearLayout
        android:id="@+id/libraryContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="12dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingBottom="10dp">

            <TextView
                android:id="@+id/tvHeaderTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="المكتبة"
                android:textSize="26sp"
                android:textStyle="bold"
                android:textColor="#000000" />

            <Button
                android:id="@+id/btnVoiceCommand"
                android:layout_width="wrap_content"
                android:layout_height="46dp"
                android:layout_marginEnd="8dp"
                android:background="#000000"
                android:textColor="#FFFFFF"
                android:paddingHorizontal="14dp"
                android:text="🎤 اطلب كتاباً"
                android:textSize="18sp"
                android:textStyle="bold" />

            <ImageButton
                android:id="@+id/btnLibraryLock"
                android:layout_width="46dp"
                android:layout_height="46dp"
                android:background="#EEEEEE"
                android:src="@android:drawable/ic_lock_lock"
                android:contentDescription="قفل المكتبة" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/libraryControlBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone"
            android:paddingBottom="10dp">

            <EditText
                android:id="@+id/searchBar"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="بحث في الكتب..."
                android:textSize="18sp"
                android:textColor="#000000" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal">
                <Button
                    android:id="@+id/btnToggleAppsView"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="التطبيقات"
                    android:textSize="15sp" />
                <Button
                    android:id="@+id/btnWifi"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="الواي فاي"
                    android:textSize="15sp" />
                <Button
                    android:id="@+id/btnSystemSettings"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="النظام"
                    android:textSize="15sp" />
            </LinearLayout>
        </LinearLayout>

        <GridView
            android:id="@+id/gridBooksOrApps"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:numColumns="3"
            android:verticalSpacing="14dp"
            android:horizontalSpacing="10dp"
            android:stretchMode="columnWidth" />
    </LinearLayout>

    <!-- 2. واجهة القارئ المدمجة -->
    <LinearLayout
        android:id="@+id/readerContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="#FFFFFF"
        android:visibility="gone">

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
                    android:contentDescription="الرئيسية" />

                <ImageButton
                    android:id="@+id/btnReaderLock"
                    android:layout_width="46dp"
                    android:layout_height="46dp"
                    android:layout_marginStart="6dp"
                    android:src="@android:drawable/ic_lock_lock"
                    android:contentDescription="قفل القارئ" />

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
                    android:layout_marginStart="4dp"
                    android:paddingHorizontal="12dp"
                    android:text="حفظ علامة"
                    android:textSize="17sp"
                    android:textStyle="bold" />

                <Button
                    android:id="@+id/btnGotoBookmark"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="4dp"
                    android:text="الذهاب للعلامة"
                    android:textSize="17sp" />

                <Button
                    android:id="@+id/btnSpeak"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="4dp"
                    android:text="🔊 استماع"
                    android:textSize="17sp"
                    android:textStyle="bold" />

                <Button
                    android:id="@+id/btnDarkMode"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="4dp"
                    android:text="الوضع الليلي"
                    android:textSize="17sp" />

                <Button
                    android:id="@+id/btnLineSpacing"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="4dp"
                    android:text="تباعد الأسطر"
                    android:textSize="17sp" />
            </LinearLayout>
        </HorizontalScrollView>

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

            <View
                android:id="@+id/touchZoneRight"
                android:layout_width="90dp"
                android:layout_height="match_parent"
                android:layout_gravity="end"
                android:background="@android:color/transparent" />

            <View
                android:id="@+id/touchZoneLeft"
                android:layout_width="90dp"
                android:layout_height="match_parent"
                android:layout_gravity="start"
                android:background="@android:color/transparent" />
        </FrameLayout>

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
</FrameLayout>
""",

    # 4. كود التطبيق المحدث (تلوين زر الحفظ بالأسود + مساعد صوتي لفتح الكتب + استخراج نص PDF وقراءته)
    "app/src/main/java/com/unified/ereader/MainActivity.kt": """package com.unified.ereader

import android.Manifest
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipFile

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var libraryContainer: LinearLayout
    private lateinit var readerContainer: LinearLayout

    private lateinit var tvHeaderTitle: TextView
    private lateinit var btnVoiceCommand: Button
    private lateinit var gridBooksOrApps: GridView
    private lateinit var libraryControlBar: LinearLayout
    private lateinit var searchBar: EditText
    private lateinit var btnToggleAppsView: Button
    private lateinit var btnSystemSettings: Button
    private lateinit var btnWifi: Button
    private lateinit var btnLibraryLock: ImageButton

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

    private var isInReaderMode = false
    private var isLibraryUnlocked = false
    private var isReaderUnlocked = false
    private var showingAppsMode = false

    private var allBooks: List<File> = emptyList()
    private var allApps: List<ResolveInfo> = emptyList()
    private val coverCache = mutableMapOf<String, Bitmap>()
    private val stateProps = Properties()

    private var fontSize = 26
    private var pdfZoomScale = 1.0f
    private var isDark = false
    private var lineSpacing = 1.4f
    private var currentHtmlContent = ""
    private var currentBookType = "TEXT"
    private var currentBookFile: File? = null
    private var currentPdfBitmap: Bitmap? = null

    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPageIndex = 0
    private var pdfFileDescriptor: ParcelFileDescriptor? = null

    private var epubChapters: List<String> = emptyList()
    private var currentChapterIndex = 0
    private var currentTextPage = 0
    private var totalTextPages = 1
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        if (isLibraryUnlocked || isReaderUnlocked) {
            isLibraryUnlocked = false
            isReaderUnlocked = false
            showingAppsMode = false
            applyLibraryLockState()
            applyReaderToolbarVisibility()
            Toast.makeText(this, "تم القفل التلقائي", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        PDFBoxResourceLoader.init(applicationContext)
        requestPermissionsIfNeeded()
        setupKioskPolicyIfOwner()

        prefs = getSharedPreferences("UnifiedReaderPrefs", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        bindViews()
        setupLibraryEvents()
        setupReaderEvents()

        loadBooks()
        loadStateProps()
        loadInstalledApps()
        applyLibraryLockState()
        applyReaderToolbarVisibility()
        showLibraryView()
    }

    private fun bindViews() {
        libraryContainer = findViewById(R.id.libraryContainer)
        readerContainer = findViewById(R.id.readerContainer)

        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        btnVoiceCommand = findViewById(R.id.btnVoiceCommand)
        gridBooksOrApps = findViewById(R.id.gridBooksOrApps)
        libraryControlBar = findViewById(R.id.libraryControlBar)
        searchBar = findViewById(R.id.searchBar)
        btnToggleAppsView = findViewById(R.id.btnToggleAppsView)
        btnSystemSettings = findViewById(R.id.btnSystemSettings)
        btnWifi = findViewById(R.id.btnWifi)
        btnLibraryLock = findViewById(R.id.btnLibraryLock)

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
    }

    private fun showLibraryView() {
        saveCurrentReadingPosition()
        tts?.stop()
        isInReaderMode = false
        readerContainer.visibility = View.GONE
        libraryContainer.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadBooks()
        loadStateProps()
        refreshGrid(searchBar.text.toString())
    }

    private fun showReaderView(bookFile: File) {
        isInReaderMode = true
        libraryContainer.visibility = View.GONE
        readerContainer.visibility = View.VISIBLE
        if (prefs.getBoolean("KEEP_SCREEN_ON", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        updateBatteryDisplay()
        openBookFile(bookFile)
    }

    // ==========================================
    // المساعد الصوتي لفتح الكتب بالأمر الصوتي
    // ==========================================
    private fun startVoiceBookAssistant() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "التعرف الصوتي غير مدعوم على هذا الجهاز", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        btnVoiceCommand.text = "🎙️ تفضل..."
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { btnVoiceCommand.text = "🎤 اطلب كتاباً" }
            override fun onError(error: Int) {
                btnVoiceCommand.text = "🎤 اطلب كتاباً"
                Toast.makeText(this@MainActivity, "لم أسمع بوضوح، حاول مرة أخرى", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                btnVoiceCommand.text = "🎤 اطلب كتاباً"
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: return
                findAndOpenBookByVoice(spokenText)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun findAndOpenBookByVoice(spokenCommand: String) {
        val cleanQuery = spokenCommand
            .replace("افتح", "")
            .replace("كتاب", "")
            .replace("اقرأ", "")
            .replace("شغل", "")
            .trim()

        if (cleanQuery.isBlank()) return

        val matchedBook = allBooks.firstOrNull {
            it.nameWithoutExtension.contains(cleanQuery, ignoreCase = true)
        } ?: allBooks.firstOrNull { book ->
            val words = cleanQuery.split(" ").filter { it.length > 2 }
            words.any { word -> book.nameWithoutExtension.contains(word, ignoreCase = true) }
        }

        if (matchedBook != null) {
            tts?.speak("تم فتح ${matchedBook.nameWithoutExtension}", TextToSpeech.QUEUE_FLUSH, null, "OPEN_MSG")
            showReaderView(matchedBook)
        } else {
            tts?.speak("لم أجد كتاباً باسم $cleanQuery", TextToSpeech.QUEUE_FLUSH, null, "ERR_MSG")
            Toast.makeText(this, "لم يتم العثور على: $cleanQuery", Toast.LENGTH_LONG).show()
        }
    }

    // ==========================================
    // أحداث المكتبة واللانشر
    // ==========================================
    private fun setupLibraryEvents() {
        btnVoiceCommand.setOnClickListener { startVoiceBookAssistant() }

        btnLibraryLock.setOnClickListener {
            resetAutoLockTimer()
            if (isLibraryUnlocked) {
                isLibraryUnlocked = false
                showingAppsMode = false
                applyLibraryLockState()
                Toast.makeText(this, "تم قفل المكتبة", Toast.LENGTH_SHORT).show()
            } else {
                promptForPin(isForReader = false)
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

        btnSystemSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        btnWifi.setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refreshGrid(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applyLibraryLockState() {
        val allowSearch = prefs.getBoolean("ALLOW_SEARCH", false)
        val allowWifi = prefs.getBoolean("ALLOW_WIFI", false)
        val allowVoice = prefs.getBoolean("ALLOW_VOICE", true)
        gridBooksOrApps.numColumns = 3

        if (isLibraryUnlocked) {
            btnLibraryLock.setImageResource(android.R.drawable.ic_partial_secure)
            libraryControlBar.visibility = View.VISIBLE
            btnVoiceCommand.visibility = View.VISIBLE
            searchBar.visibility = View.VISIBLE
            btnToggleAppsView.visibility = View.VISIBLE
            btnSystemSettings.visibility = View.VISIBLE
            btnWifi.visibility = View.VISIBLE
            resetAutoLockTimer()
        } else {
            btnLibraryLock.setImageResource(android.R.drawable.ic_lock_lock)
            tvHeaderTitle.text = "المكتبة"
            btnVoiceCommand.visibility = if (allowVoice) View.VISIBLE else View.GONE
            searchBar.visibility = if (allowSearch) View.VISIBLE else View.GONE
            btnWifi.visibility = if (allowWifi) View.VISIBLE else View.GONE
            btnToggleAppsView.visibility = View.GONE
            btnSystemSettings.visibility = View.GONE
            libraryControlBar.visibility = if (allowSearch || allowWifi) View.VISIBLE else View.GONE
        }
        updateKioskLockState()
        refreshGrid("")
    }

    private fun refreshGrid(query: String) {
        if (showingAppsMode && isLibraryUnlocked) {
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
        val visibleBooks = if (isLibraryUnlocked) allBooks else allBooks.filter { it.name !in hiddenBooks }
        val filteredBooks = if (query.isBlank()) visibleBooks
        else visibleBooks.filter { it.nameWithoutExtension.contains(query, ignoreCase = true) }

        gridBooksOrApps.adapter = BookCoverAdapter(filteredBooks, hiddenBooks)

        gridBooksOrApps.setOnItemClickListener { _, _, position, _ ->
            if (filteredBooks.isNotEmpty()) showReaderView(filteredBooks[position])
        }

        gridBooksOrApps.setOnItemLongClickListener { _, _, position, _ ->
            if (isLibraryUnlocked && filteredBooks.isNotEmpty()) {
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

            val border = GradientDrawable().apply { setColor(Color.WHITE); setStroke(3, Color.BLACK) }
            (ivCover.parent as View).background = border

            val badgeBorder = GradientDrawable().apply { setColor(Color.WHITE); setStroke(2, Color.BLACK) }
            tvFormat.background = badgeBorder
            tvFormat.text = book.extension.lowercase()

            tvProgress.background = badgeBorder
            val pct = stateProps.getProperty("pct_${book.name}", "0")
            tvProgress.text = "$pct%"

            val prefix = if (isLibraryUnlocked && book.name in hiddenBooks) "[مخفي] " else ""
            tvTitle.text = "$prefix${book.nameWithoutExtension}"

            ivCover.setImageBitmap(getBookCoverBitmap(book))
            return view
        }
    }

    // ==========================================
    // أحداث القارئ + تلوين زر العلامة + قراءة PDF الذكية
    // ==========================================
    private fun setupReaderEvents() {
        btnHomeLauncher.setOnClickListener { showLibraryView() }
        touchZoneLeft.setOnClickListener { handleNextPage() }
        touchZoneRight.setOnClickListener { handlePrevPage() }

        btnZoomIn.setOnClickListener {
            if (currentBookType == "PDF") {
                if (pdfZoomScale < 2.2f) { pdfZoomScale += 0.25f; renderPdfPage(currentPdfPageIndex) }
            } else if (fontSize < 60) {
                fontSize += 3; prefs.edit().putInt("FONT_SIZE", fontSize).apply(); refreshWebViewContent()
            }
        }

        btnZoomOut.setOnClickListener {
            if (currentBookType == "PDF") {
                if (pdfZoomScale > 0.75f) { pdfZoomScale -= 0.25f; renderPdfPage(currentPdfPageIndex) }
            } else if (fontSize > 16) {
                fontSize -= 3; prefs.edit().putInt("FONT_SIZE", fontSize).apply(); refreshWebViewContent()
            }
        }

        btnSaveBookmark.setOnClickListener { saveBookmarkForCurrentBook() }
        btnGotoBookmark.setOnClickListener { gotoBookmarkForCurrentBook() }

        btnDarkMode.setOnClickListener {
            isDark = !isDark
            if (currentBookType == "PDF") renderPdfPage(currentPdfPageIndex) else refreshWebViewContent()
        }

        btnLineSpacing.setOnClickListener {
            lineSpacing = if (lineSpacing == 1.4f) 2.0f else 1.4f
            refreshWebViewContent()
        }

        btnSpeak.setOnClickListener {
            if (tts?.isSpeaking == true) {
                tts?.stop()
                btnSpeak.text = "🔊 استماع"
            } else {
                speakCurrentPageContent()
            }
        }

        btnReaderLock.setOnClickListener {
            resetAutoLockTimer()
            if (isReaderUnlocked) {
                isReaderUnlocked = false
                applyReaderToolbarVisibility()
                Toast.makeText(this, "تم قفل إعدادات القارئ", Toast.LENGTH_SHORT).show()
            } else {
                promptForPin(isForReader = true)
            }
        }
    }

    // تحديث لون زر "حفظ علامة" ليصبح أسود بالكامل إذا كانت الصفحة الحالية هي المحفوظة
    private fun updateBookmarkButtonStyle() {
        val file = currentBookFile ?: return
        val isCurrentPageBookmarked = if (currentBookType == "PDF") {
            val bmPage = stateProps.getProperty("bm_page_${file.name}")?.toIntOrNull()
            bmPage != null && bmPage == currentPdfPageIndex
        } else {
            val bmChap = stateProps.getProperty("bm_chap_${file.name}")?.toIntOrNull() ?: 0
            val bmTpage = stateProps.getProperty("bm_tpage_${file.name}")?.toIntOrNull()
            bmTpage != null && bmChap == currentChapterIndex && bmTpage == currentTextPage
        }

        if (isCurrentPageBookmarked) {
            btnSaveBookmark.setBackgroundColor(Color.BLACK)
            btnSaveBookmark.setTextColor(Color.WHITE)
            btnSaveBookmark.text = "✓ محفوظة"
        } else {
            btnSaveBookmark.setBackgroundColor(Color.parseColor("#E0E0E0"))
            btnSaveBookmark.setTextColor(Color.BLACK)
            btnSaveBookmark.text = "حفظ علامة"
        }
    }

    // استخراج النص من PDF (سواء نص مدمج أو صورة عبر OCR) وقراءته صوتياً
    private fun speakCurrentPageContent() {
        if (currentBookType != "PDF") {
            val plainText = Html.fromHtml(currentHtmlContent, Html.FROM_HTML_MODE_LEGACY).toString().take(3800)
            if (plainText.isNotBlank()) {
                btnSpeak.text = "⏹ إيقاف"
                tts?.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "BOOK_TTS")
            }
            return
        }

        val file = currentBookFile ?: return
        btnSpeak.text = "⏳ جاري التفريغ..."

        // 1. فحص وجود نص مفرّغ مسبقاً لهذه الصفحة في الكاش
        val cacheFile = File(Environment.getExternalStorageDirectory(), "MyBooks/.ocr_${file.name}_p${currentPdfPageIndex}.txt")
        if (cacheFile.exists()) {
            val cachedText = cacheFile.readText()
            if (cachedText.isNotBlank()) {
                btnSpeak.text = "⏹ إيقاف"
                tts?.speak(cachedText.take(3800), TextToSpeech.QUEUE_FLUSH, null, "PDF_TTS")
                return
            }
        }

        // 2. محاولة استخراج النص المضمن داخل PDF عبر PDFBox في خيط خلفي
        Thread {
            var extractedText = ""
            try {
                PDDocument.load(file).use { doc ->
                    val stripper = PDFTextStripper().apply {
                        startPage = currentPdfPageIndex + 1
                        endPage = currentPdfPageIndex + 1
                    }
                    extractedText = stripper.getText(doc).trim()
                }
            } catch (_: Exception) {}

            if (extractedText.length > 15) {
                cacheFile.writeText(extractedText)
                runOnUiThread {
                    btnSpeak.text = "⏹ إيقاف"
                    tts?.speak(extractedText.take(3800), TextToSpeech.QUEUE_FLUSH, null, "PDF_TTS")
                }
            } else {
                // 3. إذا كان PDF عبارة عن صور ممسوحة ضوئياً، نمرر صورة الصفحة إلى ML Kit OCR
                val bmp = currentPdfBitmap
                if (bmp != null) {
                    val image = InputImage.fromBitmap(bmp, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val ocrResult = visionText.text.trim()
                            if (ocrResult.isNotBlank()) {
                                cacheFile.writeText(ocrResult)
                                btnSpeak.text = "⏹ إيقاف"
                                tts?.speak(ocrResult.take(3800), TextToSpeech.QUEUE_FLUSH, null, "OCR_TTS")
                            } else {
                                btnSpeak.text = "🔊 استماع"
                                Toast.makeText(this, "لم يتم العثور على نص مقروء في هذه الصورة", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            btnSpeak.text = "🔊 استماع"
                            Toast.makeText(this, "تعذر تفريغ النص من الصفحة", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    runOnUiThread { btnSpeak.text = "🔊 استماع" }
                }
            }
        }.start()
    }

    private fun applyReaderToolbarVisibility() {
        btnHomeLauncher.visibility = View.VISIBLE
        btnZoomIn.visibility = View.VISIBLE
        btnZoomOut.visibility = View.VISIBLE

        if (isReaderUnlocked) {
            btnReaderLock.setImageResource(android.R.drawable.ic_partial_secure)
            btnSaveBookmark.visibility = View.VISIBLE
            btnGotoBookmark.visibility = View.VISIBLE
            btnDarkMode.visibility = View.VISIBLE
            btnLineSpacing.visibility = View.VISIBLE
            btnSpeak.visibility = View.VISIBLE
            resetAutoLockTimer()
        } else {
            btnReaderLock.setImageResource(android.R.drawable.ic_lock_lock)
            val showBm = prefs.getBoolean("SHOW_BOOKMARKS", true)
            btnSaveBookmark.visibility = if (showBm) View.VISIBLE else View.GONE
            btnGotoBookmark.visibility = if (showBm) View.VISIBLE else View.GONE
            btnDarkMode.visibility = if (prefs.getBoolean("SHOW_DARK", false)) View.VISIBLE else View.GONE
            btnLineSpacing.visibility = if (prefs.getBoolean("SHOW_SPACING", false)) View.VISIBLE else View.GONE
            btnSpeak.visibility = if (prefs.getBoolean("SHOW_TTS", true)) View.VISIBLE else View.GONE
        }
        updateBookmarkButtonStyle()
    }

    private fun openBookFile(file: File) {
        closePdfRenderer()
        currentBookFile = file
        loadStateProps()

        when (file.extension.lowercase()) {
            "pdf" -> {
                currentBookType = "PDF"
                epubWebView.visibility = View.GONE
                pdfImageView.visibility = View.VISIBLE
                pdfZoomScale = 1.0f
                pdfFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(pdfFileDescriptor!!)
                val savedPage = stateProps.getProperty("page_${file.name}", "0").toIntOrNull() ?: 0
                renderPdfPage(savedPage.coerceIn(0, (pdfRenderer!!.pageCount - 1).coerceAtLeast(0)))
            }
            "epub" -> {
                currentBookType = "EPUB"
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
                currentBookType = "TEXT"
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

        currentPdfBitmap = bitmap
        pdfImageView.setImageBitmap(bitmap)
        tvPageStatus.text = "صفحة ${index + 1} من ${renderer.pageCount}"
        saveCurrentReadingPosition()
        updateBookmarkButtonStyle()
    }

    private fun refreshWebViewContent() {
        val bg = if (isDark) "#000000" else "#FFFFFF"
        val fg = if (isDark) "#FFFFFF" else "#000000"
        val styledHtml = \"\"\"
            <html dir="rtl" lang="ar">
            <head><meta charset="UTF-8">
            <style>
                body { background-color: $bg; color: $fg; font-size: ${fontSize}px; line-height: $lineSpacing; padding: 20px 28px; font-family: sans-serif; word-wrap: break-word; }
                img { max-width: 100%; height: auto; }
            </style></head>
            <body>$currentHtmlContent</body></html>
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
        val chapInfo = if (currentBookType == "EPUB" && epubChapters.size > 1) " (فصل ${currentChapterIndex + 1}/${epubChapters.size})" else ""
        tvPageStatus.text = "صفحة ${currentTextPage + 1} من $totalTextPages$chapInfo"
        saveCurrentReadingPosition()
        updateBookmarkButtonStyle()
    }

    private fun handleNextPage() {
        if (currentBookType == "PDF") {
            if (pdfRenderer != null && currentPdfPageIndex < pdfRenderer!!.pageCount - 1) renderPdfPage(currentPdfPageIndex + 1)
        } else {
            val pageH = epubWebView.height
            if (currentTextPage < totalTextPages - 1) {
                currentTextPage++; epubWebView.scrollTo(0, currentTextPage * pageH); updateWebPageStatus()
            } else if (currentBookType == "EPUB" && currentChapterIndex < epubChapters.size - 1) {
                currentChapterIndex++; currentTextPage = 0; currentHtmlContent = epubChapters[currentChapterIndex]; refreshWebViewContent()
            }
        }
    }

    private fun handlePrevPage() {
        if (currentBookType == "PDF") {
            if (currentPdfPageIndex > 0) renderPdfPage(currentPdfPageIndex - 1)
        } else {
            val pageH = epubWebView.height
            if (currentTextPage > 0) {
                currentTextPage--; epubWebView.scrollTo(0, currentTextPage * pageH); updateWebPageStatus()
            } else if (currentBookType == "EPUB" && currentChapterIndex > 0) {
                currentChapterIndex--; currentTextPage = 0; currentHtmlContent = epubChapters[currentChapterIndex]; refreshWebViewContent()
            }
        }
    }

    private fun saveCurrentReadingPosition() {
        val file = currentBookFile ?: return
        val pct = if (currentBookType == "PDF" && pdfRenderer != null && pdfRenderer!!.pageCount > 0) {
            stateProps.setProperty("page_${file.name}", currentPdfPageIndex.toString())
            (((currentPdfPageIndex + 1) * 100) / pdfRenderer!!.pageCount).coerceIn(0, 100)
        } else {
            stateProps.setProperty("chap_${file.name}", currentChapterIndex.toString())
            stateProps.setProperty("tpage_${file.name}", currentTextPage.toString())
            if (currentBookType == "EPUB" && epubChapters.isNotEmpty()) (((currentChapterIndex + 1) * 100) / epubChapters.size).coerceIn(0, 100)
            else (((currentTextPage + 1) * 100) / totalTextPages.coerceAtLeast(1)).coerceIn(0, 100)
        }
        stateProps.setProperty("pct_${file.name}", pct.toString())
        persistStateProps()
    }

    private fun saveBookmarkForCurrentBook() {
        val file = currentBookFile ?: return
        if (currentBookType == "PDF") stateProps.setProperty("bm_page_${file.name}", currentPdfPageIndex.toString())
        else {
            stateProps.setProperty("bm_chap_${file.name}", currentChapterIndex.toString())
            stateProps.setProperty("bm_tpage_${file.name}", currentTextPage.toString())
        }
        persistStateProps()
        updateBookmarkButtonStyle()
        Toast.makeText(this, "تم حفظ علامة هذه الصفحة", Toast.LENGTH_SHORT).show()
    }

    private fun gotoBookmarkForCurrentBook() {
        val file = currentBookFile ?: return
        if (currentBookType == "PDF") {
            val bmPage = stateProps.getProperty("bm_page_${file.name}")?.toIntOrNull()
            if (bmPage != null) { renderPdfPage(bmPage); Toast.makeText(this, "تم الانتقال للعلامة", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(this, "لا توجد علامة محفوظة", Toast.LENGTH_SHORT).show()
        } else {
            val bmTpage = stateProps.getProperty("bm_tpage_${file.name}")?.toIntOrNull()
            if (bmTpage != null) {
                currentChapterIndex = stateProps.getProperty("bm_chap_${file.name}")?.toIntOrNull() ?: 0
                currentTextPage = bmTpage
                if (currentBookType == "EPUB" && epubChapters.isNotEmpty()) currentHtmlContent = epubChapters[currentChapterIndex.coerceIn(0, epubChapters.size - 1)]
                refreshWebViewContent()
                Toast.makeText(this, "تم الانتقال للعلامة", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "لا توجد علامة محفوظة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForPin(isForReader: Boolean) {
        val savedPin = prefs.getString("PIN_CODE", "1234")
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "الرمز السري (1234)"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 22f
        }
        AlertDialog.Builder(this)
            .setTitle(if (isForReader) "فتح إعدادات القارئ" else "فتح قفل المكتبة")
            .setView(input)
            .setPositiveButton("دخول") { _, _ ->
                if (input.text.toString() == savedPin) {
                    if (isForReader) {
                        isReaderUnlocked = true
                        applyReaderToolbarVisibility()
                        showReaderControlsDialog()
                    } else {
                        isLibraryUnlocked = true
                        applyLibraryLockState()
                        showLibraryControlsDialog()
                    }
                } else Toast.makeText(this, "رمز خاطئ", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showLibraryControlsDialog() {
        val features = arrayOf("إظهار المساعد الصوتي 🎤 أثناء القفل", "إظهار بحث الكتب أثناء القفل", "إظهار الواي فاي أثناء القفل")
        val checked = booleanArrayOf(prefs.getBoolean("ALLOW_VOICE", true), prefs.getBoolean("ALLOW_SEARCH", false), prefs.getBoolean("ALLOW_WIFI", false))
        AlertDialog.Builder(this)
            .setTitle("تخصيص المكتبة (اضغط مطولاً على كتاب لإخفائه)")
            .setMultiChoiceItems(features, checked) { _, which, isChecked ->
                resetAutoLockTimer()
                when (which) {
                    0 -> prefs.edit().putBoolean("ALLOW_VOICE", isChecked).apply()
                    1 -> prefs.edit().putBoolean("ALLOW_SEARCH", isChecked).apply()
                    2 -> prefs.edit().putBoolean("ALLOW_WIFI", isChecked).apply()
                }
            }
            .setNeutralButton("تغيير الرمز السري") { _, _ -> changePinDialog() }
            .setPositiveButton("حفظ", null)
            .show()
    }

    private fun showReaderControlsDialog() {
        val options = arrayOf("إظهار أزرار الإشارة المرجعية", "إظهار زر الاستماع الصوتي 🔊", "إظهار الوضع الليلي", "إظهار تباعد الأسطر", "منع انطفاء الشاشة أثناء القراءة")
        val states = booleanArrayOf(
            prefs.getBoolean("SHOW_BOOKMARKS", true),
            prefs.getBoolean("SHOW_TTS", true),
            prefs.getBoolean("SHOW_DARK", false),
            prefs.getBoolean("SHOW_SPACING", false),
            prefs.getBoolean("KEEP_SCREEN_ON", true)
        )
        AlertDialog.Builder(this)
            .setTitle("تخصيص القارئ")
            .setMultiChoiceItems(options, states) { _, which, isChecked ->
                resetAutoLockTimer()
                when (which) {
                    0 -> prefs.edit().putBoolean("SHOW_BOOKMARKS", isChecked).apply()
                    1 -> prefs.edit().putBoolean("SHOW_TTS", isChecked).apply()
                    2 -> prefs.edit().putBoolean("SHOW_DARK", isChecked).apply()
                    3 -> prefs.edit().putBoolean("SHOW_SPACING", isChecked).apply()
                    4 -> prefs.edit().putBoolean("KEEP_SCREEN_ON", isChecked).apply()
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
                if (input.text.isNotBlank()) prefs.edit().putString("PIN_CODE", input.text.toString()).apply()
            }.show()
    }

    private fun loadBooks() {
        val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
        if (!booksDir.exists()) booksDir.mkdirs()
        allBooks = booksDir.listFiles { f -> f.extension.lowercase() in listOf("pdf", "epub", "txt") }?.sortedBy { it.name } ?: emptyList()
    }

    private fun loadInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        allApps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }
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

    private fun extractPdfCover(file: File): Bitmap? = try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        if (renderer.pageCount > 0) {
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close(); renderer.close(); pfd.close(); bitmap
        } else { renderer.close(); pfd.close(); null }
    } catch (_: Exception) { null }

    private fun extractEpubCover(file: File): Bitmap? = try {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val coverEntry = entries.firstOrNull {
                !it.isDirectory && it.name.contains("cover", true) && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true))
            } ?: entries.firstOrNull { !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true)) }
            if (coverEntry != null) zip.getInputStream(coverEntry).use { BitmapFactory.decodeStream(it) } else null
        }
    } catch (_: Exception) { null }

    private fun createFallbackCover(title: String): Bitmap {
        val bmp = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp); canvas.drawColor(Color.parseColor("#F4F4F4"))
        val paint = Paint().apply { color = Color.BLACK; textSize = 28f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText(title.take(16), 150f, 200f, paint)
        return bmp
    }

    private fun extractEpubChapters(file: File): List<String> {
        val chapters = mutableListOf<String>()
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                    .filter { !it.isDirectory && (it.name.endsWith(".html", true) || it.name.endsWith(".xhtml", true) || it.name.endsWith(".htm", true)) }
                    .sortedBy { it.name }
                for (entry in entries) chapters.add(zip.getInputStream(entry).bufferedReader().use { it.readText() })
            }
        } catch (e: Exception) { chapters.add("<p>خطأ في فتح الكتاب: ${e.message}</p>") }
        return chapters
    }

    private fun loadStateProps() {
        val stateFile = File(Environment.getExternalStorageDirectory(), "MyBooks/.reader_state.properties")
        if (stateFile.exists()) try { FileInputStream(stateFile).use { stateProps.load(it) } } catch (_: Exception) {}
    }

    private fun persistStateProps() {
        try {
            val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
            if (!booksDir.exists()) booksDir.mkdirs()
            FileOutputStream(File(booksDir, ".reader_state.properties")).use { stateProps.store(it, "Reader State") }
        } catch (_: Exception) {}
    }

    private fun closePdfRenderer() {
        try { pdfRenderer?.close(); pdfFileDescriptor?.close() } catch (_: Exception) {}
        pdfRenderer = null; pdfFileDescriptor = null
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    private fun updateBatteryDisplay() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 0..100) tvBatteryStatus.text = "البطارية: $level%"
    }

    private fun setupKioskPolicyIfOwner() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, AdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(packageName)) dpm.setLockTaskPackages(admin, arrayOf(packageName))
        } catch (_: Exception) {}
    }

    private fun updateKioskLockState() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(packageName)) {
                if (isLibraryUnlocked) stopLockTask() else startLockTask()
            }
        } catch (_: Exception) {}
    }

    private fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        if (isLibraryUnlocked || isReaderUnlocked) autoLockHandler.postDelayed(autoLockRunnable, 120_000L)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (isInReaderMode) updateBatteryDisplay()
        if (isLibraryUnlocked || isReaderUnlocked) resetAutoLockTimer()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (isInReaderMode) handleNextPage() else gridBooksOrApps.smoothScrollBy(450, 0)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (isInReaderMode) handlePrevPage() else gridBooksOrApps.smoothScrollBy(-450, 0)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {
        if (isInReaderMode) showLibraryView()
    }

    override fun onResume() {
        super.onResume()
        if (!isInReaderMode) { loadBooks(); loadStateProps(); refreshGrid(searchBar.text.toString()) }
        updateKioskLockState()
    }

    override fun onPause() { saveCurrentReadingPosition(); super.onPause() }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ar") }
    override fun onDestroy() { speechRecognizer?.destroy(); closePdfRenderer(); tts?.shutdown(); super.onDestroy() }
}
"""
}

for path, content in files.items():
    full_path = os.path.join(base_dir, path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print("تم تحديث التطبيق الموحد v7 بنجاح!")
EOF
python ~/downloads/update_v7.py
cd ~/downloads/Boox_Locked_Apps
git add -A
git commit -m "Add black bookmark state, Arabic voice book launcher, and PDF OCR text-to-speech"
git push