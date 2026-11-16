package com.locked.reader

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
                val escaped = file.readText().replace("\n", "<br/>")
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
        val styledHtml = """
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
        """.trimIndent()

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
