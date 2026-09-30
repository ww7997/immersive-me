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
    private lateinit var tvStatus: TextView
    private lateinit var tvChip: TextView

    private lateinit var browserScreen: LinearLayout
    private lateinit var homeScreen: ScrollView
    private lateinit var soonScreen: LinearLayout
    private lateinit var homeRoot: LinearLayout
    private lateinit var soonIcon: TextView
    private lateinit var soonTitle: TextView
    private lateinit var soonBody: TextView
    private lateinit var soonAction: MaterialButton
    private lateinit var bottomNav: BottomNavigationView

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private var injectedScript: String? = null
    private var currentHost: String? = null
    private var lastTab = R.id.tab_browser
    private var homeVisible = false
    private var suppressNav = false

    private val C_BRAND = Color.parseColor("#7BA0FF")
    private val C_OK = Color.parseColor("#46D68C")
    private val C_MUTED = Color.parseColor("#8B94A7")
    private val C_TEXT = Color.parseColor("#E7EAF2")
    private val C_SURFACE = Color.parseColor("#161A21")
    private val C_SURFACE2 = Color.parseColor("#1F242D")

    private val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

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
        soonIcon = findViewById(R.id.soonIcon)
        soonTitle = findViewById(R.id.soonTitle)
        soonBody = findViewById(R.id.soonBody)
        soonAction = findViewById(R.id.soonAction)
        bottomNav = findViewById(R.id.bottomNav)

        setupWebView()
        setupToolbar()
        setupNav()
        buildHome()
        paintAll()

        if (savedInstanceState == null) {
            suppressNav = true
            bottomNav.selectedItemId = R.id.tab_browser
            suppressNav = false
            showHome()                       // نبدأ من الشاشة الرئيسية
            web.loadUrl(intent?.getStringExtra("url") ?: Prefs.homePage)
        } else {
            if (lastTab == R.id.tab_browser) showHome() else selectTab(lastTab)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /* ===================== التنقّل ===================== */

    private fun setupNav() {
        bottomNav.setOnItemSelectedListener(object : NavigationBarView.OnItemSelectedListener {
            override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
                if (suppressNav) return true          // تغيير برمجي، مو ضغطة مستخدم
                when (item.itemId) {
                    R.id.tab_settings -> {
                        suppressNav = true
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        bottomNav.selectedItemId = lastTab
                        suppressNav = false
                        return false
                    }
                    R.id.tab_browser -> {
                        // ضغطة أولى → المتصفح · ضغطة تانية → الرئيسية (تبديل)
                        if (lastTab == R.id.tab_browser && !homeVisible) showHome()
                        else selectTab(R.id.tab_browser)
                        return true
                    }
                    else -> { selectTab(item.itemId); return true }
                }
            }
        })
    }

    private fun selectTab(id: Int) {
        lastTab = id
        homeVisible = false
        browserScreen.visibility = View.GONE
        homeScreen.visibility = View.GONE
        soonScreen.visibility = View.GONE

        when (id) {
            R.id.tab_browser -> {
                browserScreen.visibility = View.VISIBLE
                paintAll()
            }
            R.id.tab_video -> {
                showSoon(
                    "\uD83C\uDFAC", "ترجمات الفيديو الثنائية",
                    "ترجمة ترجمات يوتيوب مباشرة على الفيديو — سطر أصلي وسطر مترجم.\n\nقيد البناء: بده تجريب على مشغّل يوتيوب الحقيقي.",
                    "افتح يوتيوب الآن", "https://m.youtube.com"
                )
            }
            R.id.tab_files -> {
                showSoon(
                    "\uD83D\uDCC4", "ترجمة PDF ثنائية",
                    "افتح أي PDF واقرأه بالعربية والإنجليزية معاً.\n\nقيد البناء: نستعمل pdf.js حتى تعمل نفس محرّك الترجمة.",
                    "افتح ملف PDF", null
                )
            }
            R.id.tab_books -> {
                showSoon(
                    "\uD83D\uDCD6", "قارئ الكتب (EPUB)",
                    "كتاب كامل بالإنجليزي والعربي جنب بعض، فصل ورا فصل.\n\nقيد البناء.",
                    "افتح كتاب EPUB", null
                )
            }
        }
    }

    private fun showHome() {
        homeVisible = true
        browserScreen.visibility = View.GONE
        soonScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE
        buildHome()
    }

    /** فتح المتصفح على رابط — بلا ما تتشغّل قفزة التنقّل */
    private fun goBrowser(url: String?) {
        suppressNav = true
        bottomNav.selectedItemId = R.id.tab_browser
        suppressNav = false
        selectTab(R.id.tab_browser)
        if (!url.isNullOrBlank()) web.loadUrl(url)
    }

    private fun showSoon(emoji: String, title: String, body: String, action: String, url: String?) {
        soonScreen.visibility = View.VISIBLE
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

    /* ===================== الشاشة الرئيسية ===================== */

    private fun buildHome() {
        homeRoot.removeAllViews()

        homeRoot.addView(TextView(this).apply {
            text = "مرحباً \uD83D\uDC4B"
            setTextColor(C_TEXT)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        homeRoot.addView(TextView(this).apply {
            text = "Immersive-Me · " + Prefs.effectiveName()
            setTextColor(C_MUTED)
            textSize = 12f
            setPadding(0, dp(2), 0, dp(18))
        })

        homeRoot.addView(sectionLabel("وصول سريع"))
        homeRoot.addView(tileRow(listOf(
            Triple("\uD83C\uDFAC", "يوتيوب", "https://m.youtube.com"),
            Triple("\uD83D\uDD0D", "جوجل", "https://www.google.com"),
            Triple("\uD83D\uDCDA", "ويكيبيديا", "https://en.wikipedia.org")
        )))
        homeRoot.addView(tileRow(listOf(
            Triple("\uD83D\uDCF0", "BBC", "https://www.bbc.com/news"),
            Triple("\uD83D\uDD34", "Reddit", "https://www.reddit.com"),
            Triple("\uD835\uDD4F", "Twitter", "https://twitter.com")
        )))
        homeRoot.addView(tileRow(listOf(
            Triple("\uD83D\uDC19", "GitHub", "https://github.com"),
            Triple("\uD83D\uDFE0", "Hacker News", "https://news.ycombinator.com"),
            Triple("\uD83D\uDCA1", "Stack Overflow", "https://stackoverflow.com")
        )))

        homeRoot.addView(sectionLabel("آخر الصفحات · تُترجم تلقائياً"))
        val hist = Prefs.history
        if (hist.isEmpty()) {
            homeRoot.addView(TextView(this).apply {
                text = "ما زرت أي صفحة بعد. افتح موقعاً من المتصفح وبيظهر هون."
                setTextColor(Color.parseColor("#5C6478"))
                textSize = 12.5f
                setPadding(dp(4), dp(6), 0, 0)
            })
        } else {
            hist.forEach { (u, t) -> homeRoot.addView(recentRow(u, t)) }
        }

        homeRoot.addView(sectionLabel("المحرّك الحالي"))
        homeRoot.addView(TextView(this).apply {
            text = Prefs.effectiveName()
            setTextColor(C_BRAND)
            textSize = 14f
            setPadding(dp(4), 0, 0, 0)
        })
        homeRoot.addView(MaterialButton(this).apply {
            text = "إدارة المفاتيح والمحرّكات"
            textSize = 13f
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
    }

    private fun sectionLabel(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(C_MUTED)
        textSize = 12f
        setPadding(dp(4), dp(10), 0, dp(10))
    }

    private fun tileRow(items: List<Triple<String, String, String>>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        items.forEach { (emoji, label, url) ->
            val card = MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                setCardBackgroundColor(C_SURFACE)
                cardElevation = 0f
                strokeWidth = dp(1)
                setStrokeColor(ColorStateList.valueOf(C_SURFACE2))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                isClickable = true
                setOnClickListener { goBrowser(url) }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(14), dp(8), dp(14))
            }
            inner.addView(TextView(this).apply { text = emoji; textSize = 20f; gravity = Gravity.CENTER })
            inner.addView(TextView(this).apply {
                text = label
                setTextColor(C_MUTED)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            card.addView(inner)
            row.addView(card)
        }
        return row
    }

    private fun recentRow(url: String, title: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(C_SURFACE)
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, dp(8))
        row.layoutParams = lp
        row.isClickable = true
        row.setOnClickListener { goBrowser(url) }
        row.addView(TextView(this).apply {
            val h = try { Uri.parse(url).host ?: "?" } catch (e: Exception) { "?" }
            text = h.removePrefix("www.").take(1).uppercase()
            setTextColor(C_BRAND)
            textSize = 13f
            gravity = Gravity.CENTER
            setBackgroundColor(C_SURFACE2)
            setPadding(dp(7), dp(5), dp(7), dp(5))
        })
        row.addView(TextView(this).apply {
            text = title
            setTextColor(C_TEXT)
            textSize = 12.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val l = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            l.setMargins(dp(10), 0, dp(10), 0)
            layoutParams = l
        })
        row.addView(TextView(this).apply {
            text = "AR ✓"
            setTextColor(C_OK)
            textSize = 9.5f
            setBackgroundColor(Color.parseColor("#224632D6"))
            setPadding(dp(7), dp(3), dp(7), dp(3))
        })
        return row
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

        title("الموقع الحالي")
        root.addView(MaterialSwitch(this).apply {
            text = "ترجمة تلقائية على " + (currentHost ?: "هالموقع")
            textSize = 14f; setTextColor(C_TEXT)
            isChecked = Prefs.isAutoSite(currentHost)
            setOnCheckedChangeListener { _, _ ->
                Prefs.toggleAutoSite(currentHost); paintAll()
            }
        })

        title("محرّك الترجمة")
        root.addView(TextView(this).apply {
            text = Prefs.effectiveName(); setTextColor(C_BRAND); textSize = 14f
        })
        root.addView(MaterialButton(this).apply {
            text = "إدارة المفاتيح والمحرّكات"; textSize = 13f
            setOnClickListener {
                sheet.dismiss()
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        })

        sheet.setContentView(root)
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
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = if (Prefs.desktopMode) UA.replace(" Mobile", "") else UA

        WebView.setWebContentsDebuggingEnabled(true)
        web.addJavascriptInterface(Bridge(), "ImtNative")

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
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
                urlBar.setText(url ?: "")
                currentHost = hostOf(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                urlBar.setText(url ?: "")
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
            web.evaluateJavascript(loadScript(), null)
            web.evaluateJavascript("window.__imtBoot && window.__imtBoot();", null)
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
            if (lastTab != R.id.tab_browser) {
                goBrowser(null); return true
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
        buildHome()
        web.evaluateJavascript("window.__imtConfigChanged && window.__imtConfigChanged();", null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("url")?.let { goBrowser(it) }
    }

    override fun onDestroy() {
        Prefs.flushCache()
        pool.shutdownNow()
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
            val ai = Prefs.effectiveProvider() != null
            o.put("batch", if (ai) 10 else 6)      // الذكاء الاصطناعي يتحمّل دفعات أكبر
            o.put("conc", if (ai) 3 else 2)
            return o.toString()
        }

        @JavascriptInterface
        fun translate(id: String, textsJson: String) {
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
                    TranslateEngine.translate(texts, Prefs.target)
                } catch (e: Exception) {
                    Log.e("IMT", "translate failed", e)
                    List(texts.size) { "" }
                }
                Log.d("IMT", "batch ${texts.size} in ${System.currentTimeMillis() - t0}ms")
                TranslateEngine.lastError?.let { err -> main.post { showEngineError(err) } }
                reply(id, JSONArray(out).toString())
            }
        }

        @JavascriptInterface
        fun status(done: Int, total: Int) {
            main.post {
                tvStatus.text = when {
                    total == 0 -> "ما لقيت نص قابل للترجمة بهالصفحة"
                    done >= total -> "✓ ترجمت $done مقطع · الكاش: " + Prefs.cacheSize()
                    else -> "عم يترجم…  $done/$total  ·  " + Prefs.effectiveName()
                }
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

    private fun reply(id: String, json: String) {
        main.post {
            val js = "window.__imtCallback(" + JSONObject.quote(id) + "," + JSONObject.quote(json) + ")"
            web.evaluateJavascript(js, null)
        }
    }
}
