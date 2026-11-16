package com.unified.ereader

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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipFile

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: android.content.SharedPreferences

    // حاويات الواجهتين
    private lateinit var libraryContainer: LinearLayout
    private lateinit var readerContainer: LinearLayout

    // عناصر المكتبة واللانشر
    private lateinit var tvHeaderTitle: TextView
    private lateinit var gridBooksOrApps: GridView
    private lateinit var libraryControlBar: LinearLayout
    private lateinit var searchBar: EditText
    private lateinit var btnToggleAppsView: Button
    private lateinit var btnSystemSettings: Button
    private lateinit var btnWifi: Button
    private lateinit var btnLibraryLock: ImageButton

    // عناصر القارئ المدمج
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

    // حالات التطبيق
    private var isInReaderMode = false
    private var isLibraryUnlocked = false
    private var isReaderUnlocked = false
    private var showingAppsMode = false

    private var allBooks: List<File> = emptyList()
    private var allApps: List<ResolveInfo> = emptyList()
    private val coverCache = mutableMapOf<String, Bitmap>()
    private val stateProps = Properties()

    // متغيرات القارئ
    private var fontSize = 26
    private var pdfZoomScale = 1.0f
    private var isDark = false
    private var lineSpacing = 1.4f
    private var currentHtmlContent = ""
    private var currentBookType = "TEXT"
    private var currentBookFile: File? = null

    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPageIndex = 0
    private var pdfFileDescriptor: ParcelFileDescriptor? = null

    private var epubChapters: List<String> = emptyList()
    private var currentChapterIndex = 0
    private var currentTextPage = 0
    private var totalTextPages = 1
    private var tts: TextToSpeech? = null

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

        requestStoragePermission()
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
    // أحداث ومنطق المكتبة واللانشر
    // ==========================================
    private fun setupLibraryEvents() {
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
        gridBooksOrApps.numColumns = 3

        if (isLibraryUnlocked) {
            btnLibraryLock.setImageResource(android.R.drawable.ic_partial_secure)
            libraryControlBar.visibility = View.VISIBLE
            searchBar.visibility = View.VISIBLE
            btnToggleAppsView.visibility = View.VISIBLE
            btnSystemSettings.visibility = View.VISIBLE
            btnWifi.visibility = View.VISIBLE
            resetAutoLockTimer()
        } else {
            btnLibraryLock.setImageResource(android.R.drawable.ic_lock_lock)
            tvHeaderTitle.text = "المكتبة"
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
            if (filteredBooks.isNotEmpty()) {
                showReaderView(filteredBooks[position])
            }
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
    // أحداث ومنطق القارئ المدمج
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
            if (tts?.isSpeaking == true) tts?.stop()
            else {
                val plainText = Html.fromHtml(currentHtmlContent, Html.FROM_HTML_MODE_LEGACY).toString().take(3500)
                tts?.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "BOOK_TTS")
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
            btnSpeak.visibility = if (prefs.getBoolean("SHOW_TTS", false)) View.VISIBLE else View.GONE
        }
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
            <head><meta charset="UTF-8">
            <style>
                body { background-color: $bg; color: $fg; font-size: ${fontSize}px; line-height: $lineSpacing; padding: 20px 28px; font-family: sans-serif; word-wrap: break-word; }
                img { max-width: 100%; height: auto; }
            </style></head>
            <body>$currentHtmlContent</body></html>
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
        val chapInfo = if (currentBookType == "EPUB" && epubChapters.size > 1) " (فصل ${currentChapterIndex + 1}/${epubChapters.size})" else ""
        tvPageStatus.text = "صفحة ${currentTextPage + 1} من $totalTextPages$chapInfo"
        saveCurrentReadingPosition()
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
        Toast.makeText(this, "تم حفظ الإشارة المرجعية", Toast.LENGTH_SHORT).show()
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

    // ==========================================
    // إدارة الرمز السري و Kiosk Mode والملفات
    // ==========================================
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
        val features = arrayOf("إظهار بحث الكتب أثناء القفل", "إظهار الواي فاي أثناء القفل")
        val checked = booleanArrayOf(prefs.getBoolean("ALLOW_SEARCH", false), prefs.getBoolean("ALLOW_WIFI", false))
        AlertDialog.Builder(this)
            .setTitle("تخصيص المكتبة (اضغط مطولاً على كتاب لإخفائه)")
            .setMultiChoiceItems(features, checked) { _, which, isChecked ->
                resetAutoLockTimer()
                if (which == 0) prefs.edit().putBoolean("ALLOW_SEARCH", isChecked).apply()
                if (which == 1) prefs.edit().putBoolean("ALLOW_WIFI", isChecked).apply()
            }
            .setNeutralButton("تغيير الرمز السري") { _, _ -> changePinDialog() }
            .setPositiveButton("حفظ", null)
            .show()
    }

    private fun showReaderControlsDialog() {
        val options = arrayOf("إظهار أزرار الإشارة المرجعية", "إظهار الوضع الليلي", "إظهار تباعد الأسطر", "إظهار القراءة الصوتية", "منع انطفاء الشاشة أثناء القراءة")
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

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
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
    override fun onDestroy() { closePdfRenderer(); tts?.shutdown(); super.onDestroy() }
}
