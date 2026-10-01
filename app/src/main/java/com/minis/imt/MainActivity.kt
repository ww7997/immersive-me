package com.minis.imt

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var btnGo: MaterialButton
    private lateinit var fabTr: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var btnMenu: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var btnReload: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvChip: TextView

    private lateinit var browserScreen: LinearLayout
    private lateinit var homeScreen: ScrollView
    private lateinit var soonScreen: LinearLayout
    private lateinit var homeRoot: LinearLayout
    private lateinit var discoverRoot: LinearLayout
    private lateinit var textRoot: LinearLayout
    private lateinit var discoverScreen: ScrollView
    private lateinit var textScreen: ScrollView
    private lateinit var soonIcon: TextView
    private lateinit var soonTitle: TextView
    private lateinit var soonBody: TextView
    private lateinit var soonAction: MaterialButton
    private lateinit var bottomNav: BottomNavigationView

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private var injectedScript: String? = null
    private var currentHost: String? = null
    private var lastTab = R.id.tab_home
    private var suppressNav = false
    private var lastBatchMs = 0L
    private var lastBatchCount = 0
    private var ytInfo = ""
    private var trInfo = ""

    private fun refreshStatus() {
        tvStatus.text = if (ytInfo.isBlank()) trInfo else trInfo + "  |  " + ytInfo
    }

    private val ACCENT = Color.parseColor("#E9457B")
    private val ACCENT_SOFT = Color.parseColor("#FCE4EE")
    private val TEXT = Color.parseColor("#16161A")
    private val MUTED = Color.parseColor("#8A8A94")
    private val LINE = Color.parseColor("#E8E8EE")

    // أسماء قديمة (اللوحة السريعة) — صارت بألوان فاتحة
    private val C_BRAND = ACCENT
    private val C_OK = Color.parseColor("#22B573")
    private val C_MUTED = MUTED
    private val C_TEXT = TEXT
    private val C_SURFACE = Color.WHITE
    private val C_SURFACE2 = Color.parseColor("#EFEFF4")

    private val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** نسخة سطح المكتب — ضرورية ليوتيوب: موقع الموبايل ما عندو زر ترجمات */
    private val UA_DESKTOP = UA.replace(" Mobile", "")

    private var ytFixAt = 0L
    private var destroyed = false
    private var netRetries = 0
    private var pageReady = false

    /** منتقي ملفات EPUB */
    private val bookPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loadBook(uri)
    }

    /** منتقي ملفات PDF */
    private val pdfPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loadPdf(uri)
    }

    /** يفتح ملف PDF في عارض pdf.js ويترجمو بنفس المحرّك */
    private fun loadPdf(uri: android.net.Uri) {
        tvStatus.text = "عم يقرأ الملف…"
        pool.execute {
            val bytes: ByteArray? = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) { null }
            if (destroyed) return@execute
            if (bytes == null || bytes.isEmpty()) {
                main.post { snack("تعذّر قراءة الملف") }
                return@execute
            }
            val name = uri.lastPathSegment?.substringAfterLast('/')?.take(60) ?: "document.pdf"
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            main.post {
                if (destroyed) return@post
                goBrowser(null)
                pageReady = false
                try {
                    web.settings.allowFileAccess = true
                    web.loadUrl("file:///android_asset/pdfview.html")
                    tvStatus.text = "📄 " + name + " · عم يفتح…"
                    main.postDelayed({ sendPdf(b64, name) }, 2200)
                } catch (e: Exception) { snack("تعذّر فتح العارض") }
            }
        }
    }

    /** نرسل محتوى الملف للعارض على أجزاء — منشان ما نجمّد الواجهة */
    private fun sendPdf(b64: String, name: String) {
        if (destroyed) return
        web.evaluateJavascript("window.__imtPdfBegin && window.__imtPdfBegin();", null)
        val CHUNK = 500_000
        var pos = 0
        val h = Handler(Looper.getMainLooper())
        val step = object : Runnable {
            override fun run() {
                if (destroyed) return
                if (pos >= b64.length) {
                    web.evaluateJavascript(
                        "window.__imtPdfEnd && window.__imtPdfEnd(" + JSONObject.quote(name) + ");", null)
                    return
                }
                val end = minOf(pos + CHUNK, b64.length)
                val part = b64.substring(pos, end)
                pos = end
                web.evaluateJavascript(
                    "window.__imtPdfChunk && window.__imtPdfChunk(" + JSONObject.quote(part) + ");", null)
                h.postDelayed(this, 30)
            }
        }
        h.post(step)
    }

    /** يفتح كتاب EPUB ويعرض فصولو بصفحة واحدة ليعمل عليها محرّك الترجمة */
    private fun loadBook(uri: android.net.Uri) {
        tvStatus.text = "عم يفتح الكتاب…"
        pool.execute {
            val book = EpubReader.open(this, uri)
            main.post {
                if (destroyed) return@post
                if (book == null) { snack("تعذّر فتح ملف EPUB"); return@post }
                goBrowser(null)
                pageReady = false
                try {
                    web.loadDataWithBaseURL(
                        "https://book.local/", book.html, "text/html", "utf-8", null
                    )
                    tvStatus.text = "📖 ${book.title} · ${book.chapters} فصل — اضغط ترجمة"
                } catch (e: Exception) { snack("تعذّر العرض") }
            }
        }
    }

    /** نغيّر وكيل المستخدم فقط عند تفعيل وضع سطح المكتب — وإلا نرجع الافتراضي */
    private fun applyUaFor(@Suppress("UNUSED_PARAMETER") url: String?) {
        val want: String? = if (Prefs.desktopMode) UA_DESKTOP else null
        try {
            if (web.settings.userAgentString != want) web.settings.userAgentString = want
            web.settings.allowFileAccess = url != null && url.startsWith("file:///android_asset/")
        } catch (e: Exception) {}
    }

    private val LANGS = listOf(
        "ar" to "العربية", "en" to "English", "de" to "Deutsch", "fr" to "Français",
        "es" to "Español", "tr" to "Türkçe", "ru" to "Русский", "fa" to "فارسی",
        "zh-CN" to "中文", "ja" to "日本語", "ko" to "한국어", "hi" to "हिन्दी"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        btnGo = findViewById(R.id.btnGo)
        fabTr = findViewById(R.id.fabTr)
        btnMenu = findViewById(R.id.btnMenu)
        tvStatus = findViewById(R.id.tvStatus)
        tvChip = findViewById(R.id.tvChip)

        browserScreen = findViewById(R.id.browserScreen)
        homeScreen = findViewById(R.id.homeScreen)
        soonScreen = findViewById(R.id.soonScreen)
        homeRoot = findViewById(R.id.homeRoot)
        discoverRoot = findViewById(R.id.discoverRoot)
        textRoot = findViewById(R.id.textRoot)
        discoverScreen = findViewById(R.id.discoverScreen)
        textScreen = findViewById(R.id.textScreen)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        soonIcon = findViewById(R.id.soonIcon)
        soonTitle = findViewById(R.id.soonTitle)
        soonBody = findViewById(R.id.soonBody)
        soonAction = findViewById(R.id.soonAction)
        bottomNav = findViewById(R.id.bottomNav)

        setupWebView()
        setupToolbar()
        setupNav()
        if (lastTab == R.id.tab_home) buildHome()
        paintAll()

        if (savedInstanceState == null) {
            suppressNav = true
            bottomNav.selectedItemId = R.id.tab_home
            suppressNav = false
            selectTab(R.id.tab_home)
            web.loadUrl(intent?.getStringExtra("url") ?: Prefs.homePage)
        } else {
            selectTab(lastTab)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /* ===================== التنقّل ===================== */

    private fun setupNav() {
        bottomNav.setOnItemSelectedListener(object : NavigationBarView.OnItemSelectedListener {
            override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
                if (suppressNav) return true          // تغيير برمجي، مو ضغطة مستخدم
                selectTab(item.itemId); return true
            }
        })
    }

    private fun selectTab(id: Int) {
        lastTab = id
        browserScreen.visibility = View.GONE
        homeScreen.visibility = View.GONE
        discoverScreen.visibility = View.GONE
        textScreen.visibility = View.GONE
        soonScreen.visibility = View.GONE

        when (id) {
            R.id.tab_home -> { homeScreen.visibility = View.VISIBLE; buildHome() }
            R.id.tab_discover -> { discoverScreen.visibility = View.VISIBLE; buildDiscover() }
            R.id.tab_text -> { textScreen.visibility = View.VISIBLE; buildText() }
        }
    }

    /** يفتح المتصفح على رابط */
    private fun goBrowser(url: String?) {
        browserScreen.visibility = View.VISIBLE
        homeScreen.visibility = View.GONE
        discoverScreen.visibility = View.GONE
        textScreen.visibility = View.GONE
        soonScreen.visibility = View.GONE
        paintAll()
        if (!url.isNullOrBlank()) {
            applyUaFor(url)
            web.loadUrl(url)
        }
    }

    private fun showSoon(emoji: String, title: String, body: String, action: String, url: String?) {
        soonScreen.visibility = View.VISIBLE
        homeScreen.visibility = View.GONE
        discoverScreen.visibility = View.GONE
        textScreen.visibility = View.GONE
        browserScreen.visibility = View.GONE
        soonIcon.text = emoji
        soonTitle.text = title
        soonBody.text = body
        if (url != null) {
            soonAction.visibility = View.VISIBLE
            soonAction.text = action
            soonAction.setOnClickListener { goBrowser(url) }
        } else {
            soonAction.visibility = View.GONE
        }
    }

    /* ===================== عناصر مشتركة ===================== */

    private fun box(v: View, radiusDp: Int, bg: Int, w: Int, h: Int): com.google.android.material.card.MaterialCardView {
        val c = com.google.android.material.card.MaterialCardView(this).apply {
            this.radius = dp(radiusDp).toFloat()
            setCardBackgroundColor(bg)
            cardElevation = 0f
            strokeWidth = 0
        }
        c.addView(v, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        c.layoutParams = LinearLayout.LayoutParams(dp(w), dp(h))
        return c
    }

    /** شريط علوي: شعار + اسم + إعدادات */
    private fun topBar(title: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(16), dp(10))
        }
        val logo = TextView(this).apply {
            text = "译"
            setTextColor(ACCENT)
            textSize = 19f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(box(logo, 12, ACCENT_SOFT, 38, 38))
        row.addView(TextView(this).apply {
            text = title
            setTextColor(TEXT)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(11), 0, 0, 0)
        })
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        row.addView(box(gear, 14, Color.WHITE, 42, 42))
        return row
    }

    /** عنوان كبير — السطر التاني بلون مميز */
    private fun bigHeading(a: String, b: String): TextView {
        val sp = android.text.SpannableString(a + "\n" + b)
        sp.setSpan(android.text.style.ForegroundColorSpan(ACCENT),
            a.length + 1, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return TextView(this).apply {
            text = sp
            textSize = 27f
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(5).toFloat(), 1f)
            setPadding(dp(22), dp(46), dp(22), dp(20))
        }
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(MUTED)
        textSize = 13f
        setPadding(dp(22), dp(22), dp(22), dp(12))
    }

    /* ===================== ١ · المفضلة ===================== */

    private val SITES = listOf(
        Triple("YouTube", "\u25B6", "https://m.youtube.com"),
        Triple("Google", "G", "https://www.google.com"),
        Triple("Wikipedia", "W", "https://en.wikipedia.org"),
        Triple("Reddit", "R", "https://www.reddit.com"),
        Triple("X", "\uD835\uDD4F", "https://twitter.com"),
        Triple("Facebook", "f", "https://m.facebook.com"),
        Triple("Amazon", "a", "https://www.amazon.de"),
        Triple("News", "N", "https://www.tagesschau.de")
    )

    private fun buildHome() {
        homeRoot.removeAllViews()
        homeRoot.addView(topBar("Immersive-Me"))
        homeRoot.addView(bigHeading("اكتب الرابط", "وابدأ الترجمة الفورية"))

        // حقل البحث
        val q = EditText(this).apply {
            hint = "ابحث أو اكتب رابطاً"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 15f
            background = null
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(22), 0, dp(6), 0)
        }
        q.setOnEditorActionListener { _, a, _ ->
            if (a == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                goBrowser(normalize(q.text.toString()))
                true
            } else false
        }
        val go = TextView(this).apply {
            text = "\u203A"
            textSize = 26f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setOnClickListener { goBrowser(normalize(q.text.toString())) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inner.addView(q, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        inner.addView(go, LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.MATCH_PARENT))
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(30).toFloat()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(android.content.res.ColorStateList.valueOf(LINE))
            addView(inner)
        }
        val clp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(58)
        )
        clp.setMargins(dp(20), 0, dp(20), 0)
        card.layoutParams = clp
        homeRoot.addView(card)

        // شبكة المواقع
        homeRoot.addView(sectionTitle("وصول سريع"))
        SITES.chunked(4).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(14), 0, dp(14), 0)
            }
            chunk.forEach { (name, glyph, url) ->
                row.addView(siteTile(name, glyph) { goBrowser(url) })
            }
            repeat(4 - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            homeRoot.addView(row)
        }

        // أدوات
        homeRoot.addView(sectionTitle("أدوات"))
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        tools.addView(siteTile("PDF", "\uD83D\uDCC4") { pdfPicker.launch(arrayOf("application/pdf")) })
        tools.addView(siteTile("Books", "\uD83D\uDCD6") {
            bookPicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        })
        tools.addView(siteTile("Settings", "\u2699") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        })
        tools.addView(siteTile("Search", "\uD83D\uDD0D") { goBrowser("https://www.google.com") })
        homeRoot.addView(tools)

        // آخر الصفحات
        val hist = Prefs.history
        if (hist.isNotEmpty()) {
            homeRoot.addView(sectionTitle("آخر الصفحات"))
            hist.take(4).forEach { (u, t) -> homeRoot.addView(recentRow(u, t)) }
        }
    }

    private fun normalize(input: String): String {
        val q = input.trim()
        if (q.isEmpty()) return Prefs.homePage
        return when {
            q.startsWith("http") -> q
            !q.contains(".") || q.contains(" ") -> "https://www.google.com/search?q=" + Uri.encode(q)
            else -> "https://$q"
        }
    }

    /** بلاطة موقع — مربع أبيض مدوّر + حرف + اسم تحته */
    private fun siteTile(name: String, glyph: String, onClick: () -> Unit): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            setPadding(0, dp(8), 0, dp(12))
            setOnClickListener { onClick() }
        }
        val g = TextView(this).apply {
            text = glyph
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        col.addView(box(g, 18, Color.WHITE, 62, 62))
        col.addView(TextView(this).apply {
            text = name
            setTextColor(MUTED)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        return col
    }

    private fun recentRow(url: String, title: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(16), 0, dp(16), dp(8))
        row.layoutParams = lp
        row.isClickable = true
        row.setOnClickListener { goBrowser(url) }
        row.addView(TextView(this).apply {
            val h = try { Uri.parse(url).host ?: "?" } catch (e: Exception) { "?" }
            text = h.removePrefix("www.").take(1).uppercase()
            setTextColor(ACCENT)
            textSize = 13f
            gravity = Gravity.CENTER
            setBackgroundColor(ACCENT_SOFT)
            setPadding(dp(9), dp(7), dp(9), dp(7))
        })
        row.addView(TextView(this).apply {
            text = title
            setTextColor(TEXT)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val l = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            l.setMargins(dp(11), 0, dp(11), 0)
            layoutParams = l
        })
        return row
    }

    /* ===================== ٢ · اكتشف ===================== */

    private val CATS = listOf(
        "تقنية" to listOf(
            "يوتيوب" to "https://m.youtube.com",
            "هاكر نيوز" to "https://news.ycombinator.com",
            "ذا فيرج" to "https://www.theverge.com",
            "جيت هاب" to "https://github.com"
        ),
        "أخبار" to listOf(
            "تاجشاو" to "https://www.tagesschau.de",
            "بي بي سي" to "https://www.bbc.com/news",
            "رويترز" to "https://www.reuters.com",
            "الجزيرة" to "https://www.aljazeera.net"
        ),
        "علوم" to listOf(
            "أركايف" to "https://arxiv.org",
            "نيتشر" to "https://www.nature.com",
            "ساينس دايلي" to "https://www.sciencedaily.com",
            "ناسا" to "https://www.nasa.gov"
        ),
        "ترفيه" to listOf(
            "ريديت" to "https://www.reddit.com",
            "آي إم دي بي" to "https://www.imdb.com",
            "سبوتيفاي" to "https://open.spotify.com",
            "تويتر" to "https://twitter.com"
        )
    )

    private fun buildDiscover() {
        discoverRoot.removeAllViews()
        discoverRoot.addView(topBar("اكتشف"))

        // تبويبات الفئات
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(4), dp(18), dp(14))
        }
        CATS.forEachIndexed { i, (name, _) ->
            val tv = TextView(this).apply {
                text = name
                textSize = 15f
                setTextColor(if (i == 0) ACCENT else MUTED)
                setTypeface(typeface, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(6), dp(20), dp(6))
                setOnClickListener { discoverTab = i; buildDiscover() }
            }
            tabs.addView(tv)
        }
        discoverRoot.addView(tabs)

        // خط وردي تحت التبويب النشط
        val line = View(this).apply { setBackgroundColor(ACCENT) }
        val lineWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), 0, 0, dp(14))
        }
        lineWrap.addView(line, LinearLayout.LayoutParams(dp(56), dp(3)))
        discoverRoot.addView(lineWrap)

        // كروت المواقع
        val cat = CATS[discoverTab.coerceIn(0, CATS.size - 1)]
        cat.second.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(14), 0, dp(14), 0)
            }
            pair.forEach { (name, url) -> row.addView(discoverCard(name, url)) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            discoverRoot.addView(row)
        }

        discoverRoot.addView(sectionTitle("اقتراحات المترجم"))
        listOf(
            "المقال الأسبوعي — ذكاء اصطناعي" to "https://www.technologyreview.com",
            "ورقة بحثية مترجمة" to "https://arxiv.org/list/cs.CL/recent"
        ).forEach { (t, u) -> discoverRoot.addView(recentRow(u, t)) }
    }

    private var discoverTab = 0

    private fun discoverCard(name: String, url: String): com.google.android.material.card.MaterialCardView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(TextView(this).apply {
            val h = try { Uri.parse(url).host ?: "?" } catch (e: Exception) { "?" }
            text = h.removePrefix("www.").take(1).uppercase()
            setTextColor(ACCENT)
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = name
            setTextColor(TEXT)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(3))
        })
        col.addView(TextView(this).apply {
            text = url.replace("https://", "").take(28)
            setTextColor(MUTED)
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 0f
            strokeWidth = 0
            addView(col)
            isClickable = true
            setOnClickListener { goBrowser(url) }
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(6), dp(6), dp(6), dp(6))
        card.layoutParams = lp
        return card
    }

    /* ===================== ٣ · الترجمة (نص) ===================== */

    private fun buildText() {
        textRoot.removeAllViews()
        textRoot.addView(topBar("الترجمة"))

        // محدد اللغة
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(14))
        }
        langRow.addView(TextView(this).apply {
            text = "كشف تلقائي"
            setTextColor(TEXT)
            textSize = 15f
        })
        langRow.addView(TextView(this).apply {
            text = "  \u21C4  "
            setTextColor(MUTED)
            textSize = 15f
            setPadding(dp(8), 0, dp(8), 0)
        })
        langRow.addView(TextView(this).apply {
            text = langName(Prefs.target)
            setTextColor(TEXT)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })
        textRoot.addView(langRow)

        // الكارت الكبير
        val input = EditText(this).apply {
            hint = "اكتب النص للترجمة…"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 16f
            background = null
            gravity = Gravity.TOP
            minLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(20), dp(18), dp(20), dp(8))
        }
        val outTv = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 16f
            setPadding(dp(20), dp(6), dp(20), dp(18))
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(input)
        col.addView(outTv)

        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(android.content.res.ColorStateList.valueOf(LINE))
            addView(col)
        }
        val clp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        clp.setMargins(dp(18), 0, dp(18), 0)
        card.layoutParams = clp
        textRoot.addView(card)

        // زر الترجمة
        val btn = com.google.android.material.button.MaterialButton(this).apply {
            text = "ترجم"
            textSize = 15f
            cornerRadius = dp(18)
            setBackgroundColor(ACCENT)
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener {
                val txt = input.text.toString().trim()
                if (txt.isEmpty()) { snack("اكتب نصاً أول"); return@setOnClickListener }
                outTv.text = "…"
                pool.execute {
                    val r = try {
                        TranslateEngine.translate(listOf(txt), Prefs.target).firstOrNull() ?: ""
                    } catch (e: Exception) { "✗ " + TranslateEngine.describe(e) }
                    main.post { outTv.text = r }
                }
            }
        }
        val blp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        blp.setMargins(dp(18), dp(14), dp(18), 0)
        btn.layoutParams = blp
        textRoot.addView(btn)

        // أزرار الملفات
        textRoot.addView(sectionTitle("ترجمة الملفات"))
        val frow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        frow.addView(siteTile("PDF", "\uD83D\uDCC4") { pdfPicker.launch(arrayOf("application/pdf")) })
        frow.addView(siteTile("Books", "\uD83D\uDCD6") {
            bookPicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        })
        frow.addView(siteTile("Files", "\uD83D\uDCC1") { showSoon("\uD83D\uDCC1", "متصفح الملفات", "قريباً", "افتح", null) })
        frow.addView(siteTile("Settings", "\u2699") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        })
        textRoot.addView(frow)
    }

    private fun langName(code: String): String {
        val names = mapOf(
            "ar" to "العربية", "en" to "English", "de" to "Deutsch", "fr" to "Français",
            "es" to "Español", "tr" to "Türkçe", "ru" to "Русский", "fa" to "فارسی",
            "zh-CN" to "中文", "ja" to "日本語", "ko" to "한국어", "hi" to "हिन्दी"
        )
        return names[code] ?: code
    }

    /* ===================== شريط المتصفح ===================== */

    private fun setupToolbar() {
        btnGo.setOnClickListener { go() }

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                go(); true
            } else false
        }

        fabTr.setOnClickListener {
            Prefs.enabled = !Prefs.enabled
            if (Prefs.enabled) startTranslation() else stopTranslation()
            paintAll()
        }

        fabTr.setOnLongClickListener {
            val on = Prefs.toggleAutoSite(currentHost)
            Prefs.enabled = on
            if (on) startTranslation() else stopTranslation()
            paintAll()
            snack(if (on) "الترجمة التلقائية مفعّلة على $currentHost"
                  else "أُوقفت الترجمة التلقائية على $currentHost")
            true
        }

        btnMenu.setOnClickListener { showQuickPanel() }
        btnBack.setOnClickListener { selectTab(R.id.tab_home); bottomNav.selectedItemId = R.id.tab_home }
        btnReload.setOnClickListener {
            try {
                if (progress.visibility == View.VISIBLE) web.stopLoading() else web.reload()
            } catch (e: Exception) {}
        }
        tvChip.setOnClickListener { showQuickPanel() }
    }

    private fun paintAll() {
        if (Prefs.enabled) {
            fabTr.text = "مترجم ✓"
            fabTr.backgroundTintList = ColorStateList.valueOf(C_OK)
            fabTr.setIconTint(ColorStateList.valueOf(Color.parseColor("#062516")))
            fabTr.setTextColor(Color.parseColor("#062516"))
        } else {
            fabTr.text = "ترجمة"
            fabTr.backgroundTintList = ColorStateList.valueOf(C_SURFACE2)
            fabTr.setIconTint(ColorStateList.valueOf(C_MUTED))
            fabTr.setTextColor(C_MUTED)
        }
        tvChip.text = Prefs.target.uppercase()
        if (!Prefs.enabled) {
            tvStatus.text = if (Prefs.isAutoSite(currentHost))
                "تلقائي مفعّل على هالموقع · اضغط 译" else "جاهز · اضغط 译 · ضغط مطوّل = تلقائي دائم"
        }
    }

    private fun showQuickPanel() {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
            setBackgroundColor(C_SURFACE)
        }

        fun title(t: String, top: Int = 18) {
            root.addView(TextView(this).apply {
                text = t; setTextColor(C_MUTED); textSize = 12f
                setPadding(0, dp(top), 0, dp(8))
            })
        }

        root.addView(TextView(this).apply {
            text = "إعدادات سريعة"; setTextColor(C_TEXT); textSize = 18f
            setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(10), 0, 0)
        })

        title("محرّك الترجمة")
        val engGroup = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf(
            "fast" to "⚡ سريع (مجاني)",
            "hybrid" to "🧠 هجين (فوري + جودة)",
            "auto" to "⚙️ تلقائي",
            "quality" to "🎯 دقيق (مفتاحك)"
        ).forEach { (code, lbl) ->
            engGroup.addView(Chip(this).apply {
                text = lbl
                textSize = 12f
                isCheckable = true
                isChecked = Prefs.engineMode == code
                setOnClickListener {
                    Prefs.engineMode = code
                    snack(when (code) {
                        "fast" -> "⚡ سريع — جوجل، بلا مفتاح، فوري"
                        "hybrid" -> "🧠 هجين — ظهور فوري بجوجل ثم تحسين بمفتاحك"
                        "quality" -> "🎯 دقيق — " + Prefs.effectiveName()
                        else -> "⚙️ تلقائي — " + Prefs.effectiveName()
                    })
                    if (Prefs.enabled) applyLangChange()
                    sheet.dismiss()
                }
            })
        }
        root.addView(engGroup)

        root.addView(TextView(this).apply {
            text = "الحالي: " + Prefs.effectiveName()
            setTextColor(C_BRAND); textSize = 13f
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(MaterialButton(this).apply {
            text = "إدارة المفاتيح والمحرّكات"; textSize = 13f
            setOnClickListener {
                sheet.dismiss()
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        })

        title("لغة الترجمة")
        val langGroup = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        LANGS.forEach { (code, label) ->
            langGroup.addView(Chip(this).apply {
                text = label; textSize = 13f; isCheckable = true; isChecked = Prefs.target == code
                setOnClickListener {
                    if (Prefs.target != code) { Prefs.target = code; applyLangChange() }
                    paintAll()
                }
            })
        }
        root.addView(langGroup)

        title("وضع العرض")
        val modeGroup = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf("below" to "ثنائي", "blur" to "ضبابي", "replace" to "استبدال").forEach { (code, label) ->
            modeGroup.addView(Chip(this).apply {
                text = label; textSize = 13f; isCheckable = true; isChecked = Prefs.mode == code
                setOnClickListener {
                    Prefs.mode = code; paintAll()
                    web.evaluateJavascript(
                        "window.__imtSetMode && window.__imtSetMode(${JSONObject.quote(code)});", null)
                }
            })
        }
        root.addView(modeGroup)

        title("مساعدة القراءة")
        root.addView(MaterialSwitch(this).apply {
            text = "ترجمة النص المظلَّل"; textSize = 14f; setTextColor(C_TEXT)
            isChecked = Prefs.selectionTranslate
            setOnCheckedChangeListener { _, v -> Prefs.selectionTranslate = v }
        })
        root.addView(MaterialSwitch(this).apply {
            text = "ترجمة صناديق الإدخال"; textSize = 14f; setTextColor(C_TEXT)
            isChecked = Prefs.inputTranslate
            setOnCheckedChangeListener { _, v -> Prefs.inputTranslate = v }
        })

        title("المتصفح")
        root.addView(MaterialButton(this).apply {
            text = "📄 افتح ملف PDF"
            textSize = 13f
            setOnClickListener {
                sheet.dismiss()
                try { pdfPicker.launch(arrayOf("application/pdf")) }
                catch (e: Exception) { snack("تعذّر فتح منتقي الملفات") }
            }
        })
        root.addView(MaterialButton(this).apply {
            text = "📖 افتح كتاب EPUB"
            textSize = 13f
            setOnClickListener {
                sheet.dismiss()
                try {
                    bookPicker.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*"))
                } catch (e: Exception) { snack("تعذّر فتح منتقي الملفات") }
            }
        })
        root.addView(MaterialButton(this).apply {
            text = "🧹 تصفير بيانات المتصفح (كاش + كوكيز)"
            textSize = 13f
            setOnClickListener {
                try {
                    web.clearCache(true)
                    web.clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    web.clearFormData()
                    snack("تم التصفير — أُعيد تحميل الصفحة")
                    web.reload()
                } catch (e: Exception) { snack("تعذّر التصفير") }
            }
        })
        root.addView(MaterialSwitch(this).apply {
            text = "وضع سطح المكتب (يفتح المواقع كنسخة الكمبيوتر)"
            textSize = 14f
            setTextColor(C_TEXT)
            isChecked = Prefs.desktopMode
            setOnCheckedChangeListener { _, v ->
                Prefs.desktopMode = v
                applyUaFor(web.url)
                web.reload()
                snack(if (v) "وضع سطح المكتب — أُعيد التحميل" else "وضع الموبايل — أُعيد التحميل")
            }
        })

        title("ترجمات الفيديو")
        root.addView(MaterialSwitch(this).apply {
            text = "ترجمة عميقة للـ API (محتوى ديناميكي — ريديت/تويتر)"
            textSize = 14f
            setTextColor(C_TEXT)
            isChecked = Prefs.apiTranslate
            setOnCheckedChangeListener { _, v ->
                Prefs.apiTranslate = v
                snack(if (v) "الترجمة العميقة مفعّلة" else "أُوقفت الترجمة العميقة")
            }
        })
        root.addView(MaterialSwitch(this).apply {
            text = "اعتراض ترجمات الفيديو من الشبكة"
            textSize = 14f
            setTextColor(C_TEXT)
            isChecked = Prefs.captureSubs
            setOnCheckedChangeListener { _, v ->
                Prefs.captureSubs = v
                snack(if (v) "اعتراض الترجمات مفعّل" else "أُوقف اعتراض الترجمات")
            }
        })
        root.addView(MaterialSwitch(this).apply {
            text = "أصلي + ترجمة (بدل الترجمة فقط)"
            textSize = 14f
            setTextColor(C_TEXT)
            isChecked = Prefs.subsBilingual
            setOnCheckedChangeListener { _, v -> Prefs.subsBilingual = v }
        })

        title("ترجمات الصفحات — وضع العرض")
        val subsGroup = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf(
            false to "ترجمة فقط",
            true to "أصلي + ترجمة"
        ).forEach { (both, lbl) ->
            subsGroup.addView(Chip(this).apply {
                text = lbl
                textSize = 12f
                isCheckable = true
                isChecked = Prefs.subsBilingual == both
                setOnClickListener {
                    Prefs.subsBilingual = both
                    web.evaluateJavascript(
                        "window.__imtYtReset && window.__imtYtReset();", null)
                    snack(if (both) "الترجمات: أصلي + ترجمة" else "الترجمات: ترجمة فقط")
                }
            })
        }
        root.addView(subsGroup)

        title("الموقع الحالي")
        root.addView(MaterialSwitch(this).apply {
            text = "ترجمة تلقائية على " + (currentHost ?: "هالموقع")
            textSize = 14f; setTextColor(C_TEXT)
            isChecked = Prefs.isAutoSite(currentHost)
            setOnCheckedChangeListener { _, _ ->
                Prefs.toggleAutoSite(currentHost); paintAll()
            }
        })

        // نغلّفها بـ ScrollView — منشان كل الأقسام تكون قابلة للوصول
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(C_SURFACE)
            isFillViewport = true
        }
        scroll.addView(root)
        sheet.setContentView(scroll)
        sheet.show()
    }

    private fun applyLangChange() {
        paintAll()
        if (Prefs.enabled) {
            web.evaluateJavascript("window.__imtStop && window.__imtStop();", null)
            main.postDelayed({
                web.evaluateJavascript("window.__imtConfigChanged && window.__imtConfigChanged();", null)
                main.postDelayed({
                    web.evaluateJavascript("window.__imtStart && window.__imtStart();", null)
                    tvStatus.text = "عم يترجم إلى " + Prefs.target + " …"
                }, 200)
            }, 200)
        }
    }

    private fun go() {
        var q = urlBar.text.toString().trim()
        if (q.isEmpty()) return
        if (!q.contains(".") || q.contains(" ")) q = "https://www.google.com/search?q=" + Uri.encode(q)
        else if (!q.startsWith("http")) q = "https://$q"
        applyUaFor(q)
        web.loadUrl(q)
    }

    private fun snack(msg: String) =
        Snackbar.make(findViewById(R.id.screenHost), msg, Snackbar.LENGTH_SHORT).show()

    private var lastShownError: String? = null

    /** يعرض خطأ المحرّك مرة وحدة لكل خطأ جديد */
    private fun showEngineError(err: String) {
        if (err == lastShownError) return
        lastShownError = err
        tvStatus.text = "⚠️ $err"
        tvStatus.setTextColor(Color.parseColor("#FF8A8A"))
        Snackbar.make(findViewById(R.id.screenHost), "⚠️ $err", Snackbar.LENGTH_LONG).show()
    }

    /* ===================== WebView ===================== */

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(true)          // يوتيوب وبعض المواقع بتحتاجها
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // وكيل المستخدم: نتركه افتراضياً من أندرويد — أضمن توافقاً مع كل المواقع
        // (الوكيل المزيّف كان يسبب ERR_NETWORK_CHANGED على يوتيوب)
        s.userAgentString = if (Prefs.desktopMode) UA_DESKTOP else null

        WebView.setWebContentsDebuggingEnabled(true)

        // كوكيز صريحة — بعض المواقع (يوتيوب) ما بتشتغل بدونها
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(web, true)
        } catch (e: Exception) {}

        web.addJavascriptInterface(Bridge(), "ImtNative")

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
            }
            /** نافذة جديدة (window.open) → نفتحها بنفس المتصفح بدل ما تفشل */
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean {
                try {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    val tmp = WebView(this@MainActivity)
                    tmp.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                            req?.url?.let { web.loadUrl(it.toString()) }
                            return true
                        }
                    }
                    transport?.webView = tmp
                    (resultMsg?.target as? Handler)?.sendMessage(resultMsg)
                    return true
                } catch (e: Exception) { return false }
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                val scheme = u.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, u)); true } catch (e: Exception) { true }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageReady = false
                if (!urlBar.hasFocus()) urlBar.setText(url ?: "")
                currentHost = hostOf(url)
                subsSeen.clear()                    // صفحة جديدة → نسمح باعتراض جديد
            }

            /**
             * اعتراض الشبكة — أهم جزء بالمعمارية الجديدة.
             * نلتقط طلب ملف الترجمات الأصلي (يوتيوب/نتفليكس/أي مشغّل)،
             * نحمّل نسخة منه، ونترك الطلب الأصلي يمشي طبيعياً بلا تدخّل.
             */
            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                try {
                    val u = request?.url?.toString() ?: return null
                    if (!Prefs.captureSubs) return null
                    if (!Subs.looksLikeSubtitleUrl(u)) return null
                    if (!subsSeen.add(u)) return null          // مرة وحدة لكل رابط
                    val method = request.method ?: "GET"
                    pool.execute { grabSubs(u, method) }
                } catch (e: Exception) {}
                return null                                    // الطلب يمشي طبيعياً
            }

            /** إعادة محاولة تلقائية عند أخطاء الشبكة العابرة — مع تصفير الكاش عند اللزوم */
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                val desc = error?.description?.toString() ?: ""
                val transient = desc.contains("ERR_NETWORK_CHANGED") ||
                                desc.contains("ERR_CONNECTION") ||
                                desc.contains("ERR_TIMED_OUT") ||
                                desc.contains("ERR_INTERNET_DISCONNECTED") ||
                                desc.contains("ERR_NETWORK_IO") ||
                                desc.contains("ERR_CACHE")
                if (!transient) return
                if (netRetries >= 3) {
                    netRetries = 0
                    tvStatus.text = "تعذّر التحميل: $desc — جرّب تحديث الصفحة"
                    return
                }
                netRetries++
                val n = netRetries
                tvStatus.text = "انقطعت الشبكة — محاولة $n/3…"
                main.postDelayed({
                    if (destroyed) return@postDelayed
                    try {
                        if (n >= 2) web.clearCache(true)      // كاش تالف؟ نصفّرو
                        CookieManager.getInstance().flush()
                        view?.reload()
                    } catch (e: Exception) {}
                }, 1500L * n)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                netRetries = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                if (!urlBar.hasFocus()) urlBar.setText(url ?: "")
                currentHost = hostOf(url)
                if (Prefs.isAutoSite(currentHost)) Prefs.enabled = true
                injectTranslator()
                paintAll()
                main.postDelayed({
                    Prefs.addHistory(view?.url, view?.title)
                    if (homeScreen.visibility == View.VISIBLE) buildHome()
                }, 600)
            }
        }
    }

    private val subsSeen = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * يحمل نسخة من ملف الترجمات، يفكّها، يبعتها للواجهة فوراً بالتوقيت الأصلي،
     * ثم يترجمها على دفعات ويحدّث كل دفعة لحظة وصولها.
     */
    private fun grabSubs(url: String, method: String) {
        if (destroyed) return
        val cookies = try { CookieManager.getInstance().getCookie(url) } catch (e: Exception) { null }
        val body = Subs.fetch(url, cookies, method) ?: return
        val cues = Subs.parse(body)
        if (cues.size < 2) return
        Log.d("IMT", "subs captured: ${cues.size} cues from $url")

        // ١) نبعتها فوراً بالتوقيت الأصلي (بلا ترجمة) — مزامنة مثالية من اللحظة الأولى
        val firstJson = Subs.toJson(cues, emptyList())
        main.post {
            if (destroyed) return@post
            try {
                web.evaluateJavascript(
                    "window.__imtSubs && window.__imtSubs(" + JSONObject.quote(firstJson) + ")", null)
                tvStatus.text = "ترجمات الفيديو: ${cues.size} سطر · عم يترجم…"
            } catch (e: Exception) {}
        }

        // ٢) نترجم على دفعات صغيرة ونحدّث تدريجياً
        val CH = 8
        var i = 0
        while (i < cues.size && !destroyed) {
            val slice = cues.subList(i, minOf(i + CH, cues.size))
            val tr = try {
                TranslateEngine.translate(slice.map { it.text }, Prefs.target)
            } catch (e: Exception) { emptyList() }
            if (tr.isNotEmpty()) {
                val arr = JSONArray()
                tr.forEach { arr.put(it) }
                val idx = i
                val done = minOf(i + CH, cues.size)
                val total = cues.size
                main.post {
                    if (destroyed) return@post
                    try {
                        web.evaluateJavascript(
                            "window.__imtSubsTr && window.__imtSubsTr($idx," +
                            JSONObject.quote(arr.toString()) + ")", null)
                        tvStatus.text = "ترجمات الفيديو: $done/$total"
                    } catch (e: Exception) {}
                }
            }
            i += CH
        }
        main.post {
            if (!destroyed) tvStatus.text = "✓ ترجمات الفيديو جاهزة (${cues.size} سطر)"
        }
    }

    private fun hostOf(url: String?): String? =
        try { url?.let { Uri.parse(it).host?.lowercase() } } catch (e: Exception) { null }

    private fun loadScript(): String {
        injectedScript?.let { return it }
        val sb = StringBuilder()
        assets.open("translate.js").use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
                var line = r.readLine()
                while (line != null) { sb.append(line).append('\n'); line = r.readLine() }
            }
        }
        injectedScript = sb.toString()
        return injectedScript!!
    }

    private fun injectTranslator() {
        try {
            if (!pageReady) return
            // نداء واحد: نحن السكريبت ثم نستدعي الإقلاع — يمنع سباق التنفيذ
            val js = loadScript() + "\ntry{window.__imtBoot&&window.__imtBoot();}catch(e){}"
            web.evaluateJavascript(js, null)
            main.postDelayed({ paintAll() }, 400)
        } catch (e: Exception) {
            Log.e("IMT", "inject failed", e)
            tvStatus.text = "خطأ بالحقن: " + (e.message ?: "?")
        }
    }

    private fun startTranslation() {
        lastShownError = null
        TranslateEngine.clearError()
        tvStatus.setTextColor(C_MUTED)
        web.evaluateJavascript("window.__imtStart && window.__imtStart();", null)
        tvStatus.text = "عم يترجم…  ·  " + Prefs.effectiveName()
    }

    private fun stopTranslation() {
        web.evaluateJavascript("window.__imtStop && window.__imtStop();", null)
        tvStatus.text = "أُوقفت الترجمة"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (browserScreen.visibility != View.VISIBLE) {
                selectTab(R.id.tab_home); bottomNav.selectedItemId = R.id.tab_home; return true
            }
            if (web.canGoBack()) { web.goBack(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() { super.onPause(); Prefs.flushCache() }

    override fun onResume() {
        super.onResume()
        paintAll()
        injectedScript = null
        if (lastTab == R.id.tab_home) buildHome()
        if (pageReady) {          // لا ننادي JS على صفحة مو جاهزة
            web.evaluateJavascript("window.__imtConfigChanged && window.__imtConfigChanged();", null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("url")?.let { goBrowser(it) }
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)      // نلغي أي مهام معلّقة قبل التدمير
        Prefs.flushCache()
        Prefs.shutdown()                           // ننهي خيط كتابة الكاش
        pool.shutdown()                            // إنهاء لطيف بدل shutdownNow
        try { web.stopLoading() } catch (e: Exception) {}
        web.removeJavascriptInterface("ImtNative")
        web.destroy()
        super.onDestroy()
    }

    /* ===================== الجسر ===================== */

    inner class Bridge {

        @JavascriptInterface
        fun getConfig(): String {
            val o = JSONObject()
            o.put("target", Prefs.target)
            o.put("mode", Prefs.mode)
            o.put("enabled", Prefs.enabled)
            o.put("provider", Prefs.effectiveName())
            o.put("selection", Prefs.selectionTranslate)
            o.put("input", Prefs.inputTranslate)
            o.put("lazy", Prefs.lazyTranslate)
            o.put("subs", if (Prefs.subsBilingual) "both" else "tr")
            o.put("api", Prefs.apiTranslate)
            val ai = Prefs.effectiveProvider() != null
            val hybrid = Prefs.engineMode == "hybrid" && ai
            // دفعات أصغر + تزامن أعلى = نتائج تظهر أسرع بكثير مع موديلات الـ AI
            val b = Prefs.batchOverride.takeIf { it > 0 } ?: if (hybrid) 8 else 6
            val c = Prefs.concOverride.takeIf { it > 0 } ?: if (ai) (if (hybrid) 5 else 4) else 2
            o.put("batch", b)
            o.put("conc", c)
            return o.toString()
        }

        @JavascriptInterface
        fun translate(id: String, textsJson: String) {
            // حماية: حدّ أقصى للطلبات — صفحة خبيثة ما تقدر تحرق رصيد المفتاح
            if (!rateOk()) { reply(id, "[]"); return }
            val texts: List<String> = try {
                val a = JSONArray(textsJson)
                List(a.length()) { a.getString(it) }
            } catch (e: Exception) { emptyList() }
            if (texts.isEmpty()) { reply(id, "[]"); return }

            // الترجمة العادية تتطلب التفعيل؛ ترجمة التحديد والإدخال تعمل دائماً
            if (texts.size > 1 && !Prefs.enabled) { reply(id, "[]"); return }

            pool.execute {
                val t0 = System.currentTimeMillis()
                val out = try {
                    TranslateEngine.translate(texts, Prefs.target) { idx, txt ->
                        if (idx in texts.indices) replyPartial(id, idx, txt)
                    }
                } catch (e: Exception) {
                    Log.e("IMT", "translate failed", e)
                    List(texts.size) { "" }
                }
                val ms = System.currentTimeMillis() - t0
                lastBatchMs = ms
                lastBatchCount = texts.size
                Log.d("IMT", "batch ${texts.size} in ${ms}ms")
                TranslateEngine.lastError?.let { err -> main.post { showEngineError(err) } }
                reply(id, JSONArray(out).toString())
            }
        }

        @JavascriptInterface
        fun explain(id: String, text: String) {
            pool.execute {
                val out = try {
                    TranslateEngine.explain(text)
                } catch (e: Exception) { "✗ " + TranslateEngine.describe(e) }
                main.post {
                    if (destroyed) return@post
                    val js = "window.__imtExplain(" + JSONObject.quote(id) + "," +
                             JSONObject.quote(out) + ")"
                    try { web.evaluateJavascript(js, null) } catch (e: Exception) {}
                }
            }
        }

        @JavascriptInterface
        fun ytStatus(msg: String) {
            ytInfo = msg
            main.post { refreshStatus() }
        }

        @JavascriptInterface
        fun fetchCaptions(id: String, url: String) {
            pool.execute {
                val payload: String = try {
                    TranslateEngine.fetchCaptions(url)
                } catch (e: Exception) {
                    "{\"error\":" + JSONObject.quote(TranslateEngine.describe(e)) + "}"
                }
                main.post {
                    val js = "window.__imtCaptions(" + JSONObject.quote(id) + "," +
                             JSONObject.quote(payload) + ")"
                    web.evaluateJavascript(js, null)
                }
            }
        }

        @JavascriptInterface
        fun status(done: Int, total: Int) {
            main.post {
                val speed = if (lastBatchMs > 0)
                    "  ·  ${lastBatchCount} في ${"%.1f".format(lastBatchMs / 1000.0)}ث" else ""
                trInfo = when {
                    total == 0 -> "ما لقيت نص قابل للترجمة"
                    done >= total -> "✓ $done مقطع · كاش ${Prefs.cacheSize()}$speed"
                    else -> "عم يترجم…  $done/$total$speed"
                }
                refreshStatus()
            }
        }

        @JavascriptInterface
        fun saveMode(m: String) { Prefs.mode = m; main.post { paintAll() } }

        @JavascriptInterface
        fun setEnabled(on: Boolean) { Prefs.enabled = on; main.post { paintAll() } }

        @JavascriptInterface
        fun notify(msg: String) { main.post { snack(msg) } }

        @JavascriptInterface
        fun log(msg: String) = Log.d("IMT-JS", msg)
    }

    private val callTimes = ArrayDeque<Long>()

    /** ١٥٠ طلب بالدقيقة كحد أقصى — يمنع أي صفحة من استنزاف رصيد المفتاح */
    private fun rateOk(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(callTimes) {
            while (callTimes.isNotEmpty() && now - callTimes.first() > 60_000) callTimes.removeFirst()
            if (callTimes.size >= 150) return false
            callTimes.addLast(now)
            return true
        }
    }

    private fun reply(id: String, json: String) {
        if (destroyed) return                       // ما نلمس WebView بعد التدمير
        main.post {
            if (destroyed) return@post
            val js = "window.__imtCallback(" + JSONObject.quote(id) + "," + JSONObject.quote(json) + ")"
            try { web.evaluateJavascript(js, null) } catch (e: Exception) {}
        }
    }

    /** نتيجة مقطع واحد وصلت من البثّ — نعرضها فوراً */
    private fun replyPartial(id: String, index: Int, text: String) {
        if (destroyed) return
        main.post {
            if (destroyed) return@post
            val js = "window.__imtPartial(" + JSONObject.quote(id) + "," + index + "," +
                     JSONObject.quote(text) + ")"
            try { web.evaluateJavascript(js, null) } catch (e: Exception) {}
        }
    }
}
