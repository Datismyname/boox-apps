package com.locked.reader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.Html
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.*
import java.util.zip.ZipFile

class ReaderMainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var epubWebView: WebView
    private lateinit var pdfScrollView: ScrollView
    private lateinit var pdfImageView: ImageView

    private lateinit var btnBackToLibrary: Button
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button
    private lateinit var btnPrevPage: Button
    private lateinit var btnNextPage: Button
    private lateinit var btnDarkMode: Button
    private lateinit var btnLineSpacing: Button
    private lateinit var btnSpeak: Button
    private lateinit var btnReaderLock: ImageButton

    private var isUnlocked = false
    private var fontSize = 26
    private var pdfZoomScale = 1.0f
    private var isDark = false
    private var lineSpacing = 1.4f
    private var currentHtmlContent = ""
    private var currentMode = "TEXT"

    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPageIndex = 0
    private var pdfFileDescriptor: ParcelFileDescriptor? = null

    private var epubChapters: List<String> = emptyList()
    private var currentChapterIndex = 0

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_reader_main)

        requestStoragePermission()

        prefs = getSharedPreferences("BookReaderPrefs", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        epubWebView = findViewById(R.id.epubWebView)
        pdfScrollView = findViewById(R.id.pdfScrollView)
        pdfImageView = findViewById(R.id.pdfImageView)

        btnBackToLibrary = findViewById(R.id.btnBackToLibrary)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        btnDarkMode = findViewById(R.id.btnDarkMode)
        btnLineSpacing = findViewById(R.id.btnLineSpacing)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnReaderLock = findViewById(R.id.btnReaderLock)

        epubWebView.settings.javaScriptEnabled = false
        fontSize = prefs.getInt("FONT_SIZE", 26)

        handleIncomingIntent(intent)
        updateToolbarVisibility()

        btnBackToLibrary.setOnClickListener {
            finish()
        }

        btnZoomIn.setOnClickListener {
            if (currentMode == "PDF") {
                if (pdfZoomScale < 2.5f) {
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

        btnPrevPage.setOnClickListener {
            if (currentMode == "PDF" && currentPdfPageIndex > 0) {
                renderPdfPage(currentPdfPageIndex - 1)
            } else if (currentMode == "EPUB" && currentChapterIndex > 0) {
                currentChapterIndex--
                currentHtmlContent = epubChapters[currentChapterIndex]
                refreshWebViewContent()
            }
        }

        btnNextPage.setOnClickListener {
            if (currentMode == "PDF" && pdfRenderer != null && currentPdfPageIndex < pdfRenderer!!.pageCount - 1) {
                renderPdfPage(currentPdfPageIndex + 1)
            } else if (currentMode == "EPUB" && currentChapterIndex < epubChapters.size - 1) {
                currentChapterIndex++
                currentHtmlContent = epubChapters[currentChapterIndex]
                refreshWebViewContent()
            }
        }

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
            if (tts?.isSpeaking == true) {
                tts?.stop()
            } else {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val path = intent?.getStringExtra("BOOK_PATH")
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                openBookFile(file)
                return
            }
        }
        loadLastOrFirstBook()
    }

    private fun openBookFile(file: File) {
        closePdfRenderer()
        when (file.extension.lowercase()) {
            "pdf" -> {
                currentMode = "PDF"
                epubWebView.visibility = View.GONE
                pdfScrollView.visibility = View.VISIBLE
                btnPrevPage.visibility = View.VISIBLE
                btnNextPage.visibility = View.VISIBLE
                pdfZoomScale = 1.0f
                pdfFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(pdfFileDescriptor!!)
                renderPdfPage(0)
            }
            "epub" -> {
                currentMode = "EPUB"
                pdfScrollView.visibility = View.GONE
                epubWebView.visibility = View.VISIBLE
                epubChapters = extractEpubChapters(file)
                currentChapterIndex = 0
                val hasMultiChapters = epubChapters.size > 1
                btnPrevPage.visibility = if (hasMultiChapters) View.VISIBLE else View.GONE
                btnNextPage.visibility = if (hasMultiChapters) View.VISIBLE else View.GONE
                currentHtmlContent = epubChapters.firstOrNull() ?: "<p>تعذر قراءة ملف EPUB</p>"
                refreshWebViewContent()
            }
            else -> {
                currentMode = "TEXT"
                pdfScrollView.visibility = View.GONE
                epubWebView.visibility = View.VISIBLE
                btnPrevPage.visibility = View.GONE
                btnNextPage.visibility = View.GONE
                val escaped = file.readText().replace("\n", "<br/>")
                currentHtmlContent = "<p>$escaped</p>"
                refreshWebViewContent()
            }
        }
    }

    private fun extractEpubChapters(file: File): List<String> {
        val chapters = mutableListOf<String>()
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                    .filter { !it.isDirectory && (it.name.endsWith(".html", true) || it.name.endsWith(".xhtml", true) || it.name.endsWith(".htm", true)) }
                    .sortedBy { it.name }
                for (entry in entries) {
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    chapters.add(text)
                }
            }
        } catch (e: Exception) {
            chapters.add("<p>خطأ في فتح الكتاب: ${e.message}</p>")
        }
        return chapters
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
        pdfScrollView.scrollTo(0, 0)
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
                    padding: 20px;
                    font-family: sans-serif;
                    word-wrap: break-word;
                }
                img { max-width: 100%; height: auto; }
            </style>
            </head>
            <body>$currentHtmlContent</body>
            </html>
        """.trimIndent()
        epubWebView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
    }

    private fun closePdfRenderer() {
        try {
            pdfRenderer?.close()
            pdfFileDescriptor?.close()
        } catch (_: Exception) {}
        pdfRenderer = null
        pdfFileDescriptor = null
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun updateToolbarVisibility() {
        btnBackToLibrary.visibility = View.VISIBLE
        btnZoomIn.visibility = View.VISIBLE
        btnZoomOut.visibility = View.VISIBLE

        if (isUnlocked) {
            btnReaderLock.setImageResource(android.R.drawable.ic_partial_secure)
            btnDarkMode.visibility = View.VISIBLE
            btnLineSpacing.visibility = View.VISIBLE
            btnSpeak.visibility = View.VISIBLE
        } else {
            btnReaderLock.setImageResource(android.R.drawable.ic_lock_lock)
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
        val options = arrayOf("إظهار الوضع الليلي", "إظهار تباعد الأسطر", "إظهار القراءة الصوتية")
        val states = booleanArrayOf(
            prefs.getBoolean("SHOW_DARK", false),
            prefs.getBoolean("SHOW_SPACING", false),
            prefs.getBoolean("SHOW_TTS", false)
        )
        AlertDialog.Builder(this)
            .setTitle("اختر الأزرار المسموح بظهورها خارج القفل")
            .setMultiChoiceItems(options, states) { _, which, isChecked ->
                val key = when (which) {
                    0 -> "SHOW_DARK"
                    1 -> "SHOW_SPACING"
                    else -> "SHOW_TTS"
                }
                prefs.edit().putBoolean(key, isChecked).apply()
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

    private fun loadLastOrFirstBook() {
        val booksDir = File(Environment.getExternalStorageDirectory(), "MyBooks")
        if (!booksDir.exists()) booksDir.mkdirs()
        val files = booksDir.listFiles { f -> f.extension.lowercase() in listOf("txt", "epub", "pdf") }
        if (!files.isNullOrEmpty()) openBookFile(files[0])
        else {
            currentHtmlContent = "<p>اختر كتاباً من المكتبة للبدء.</p>"
            refreshWebViewContent()
        }
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ar") }
    override fun onDestroy() { closePdfRenderer(); tts?.shutdown(); super.onDestroy() }
}
