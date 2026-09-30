package com.minis.imt

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
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
    private lateinit var btnTr: MaterialButton
    private lateinit var btnCfg: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvChip: TextView

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private var injectedScript: String? = null
    private var currentHost: String? = null

    private val C_BRAND = Color.parseColor("#7BA0FF")
    private val C_OK = Color.parseColor("#46D68C")
    private val C_MUTED = Color.parseColor("#8B94A7")

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
        btnTr = findViewById(R.id.btnTr)
        btnCfg = findViewById(R.id.btnCfg)
        tvStatus = findViewById(R.id.tvStatus)
        tvChip = findViewById(R.id.tvChip)

        setupWebView()
        setupToolbar()
        paintAll()

        if (savedInstanceState == null) web.loadUrl(intent?.getStringExtra("url") ?: Prefs.homePage)
    }

    /* ===================== الواجهة ===================== */

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun setupToolbar() {
        btnGo.setOnClickListener { go() }

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                go(); true
            } else false
        }

        btnTr.setOnClickListener {
            Prefs.enabled = !Prefs.enabled
            if (Prefs.enabled) startTranslation() else stopTranslation()
            paintAll()
        }

        btnTr.setOnLongClickListener {
            val on = Prefs.toggleAutoSite(currentHost)
            Prefs.enabled = on
            if (on) startTranslation() else stopTranslation()
            paintAll()
            snack(
                if (on) "الترجمة التلقائية مفعّلة على $currentHost"
                else "أُوقفت الترجمة التلقائية على $currentHost"
            )
            true
        }

        btnCfg.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        tvChip.setOnClickListener { showQuickPanel() }
        tvStatus.setOnClickListener {
            val wv = web
            if (Prefs.enabled) {
                wv.evaluateJavascript("window.__imtStartAction && window.__imtStartAction();", null)
            } else snack("اضغط أيقونة الترجمة 译 لتشغيل الترجمة")
        }
    }

    private fun paintAll() {
        val tint = if (Prefs.enabled) C_OK else C_MUTED
        btnTr.iconTint = ColorStateList.valueOf(tint)
        tvChip.text = Prefs.target.uppercase()
        if (!Prefs.enabled) {
            tvStatus.text = if (Prefs.isAutoSite(currentHost))
                "تلقائي مفعّل على هالموقع · اضغط 译" else "جاهز · اضغط 译 · ضغط مطوّل = تلقائي دائم"
        }
    }

    /** لوحة الإعدادات السريعة — Bottom Sheet */
    private fun showQuickPanel() {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
            setBackgroundColor(Color.parseColor("#161A21"))
        }

        fun title(t: String, top: Int = 18) {
            root.addView(TextView(this).apply {
                text = t
                setTextColor(Color.parseColor("#8B94A7"))
                textSize = 12f
                setPadding(0, dp(top), 0, dp(8))
            })
        }

        root.addView(TextView(this).apply {
            text = "إعدادات سريعة"
            setTextColor(Color.parseColor("#E7EAF2"))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        })

        // ---- اللغة ----
        title("لغة الترجمة")
        val langGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        LANGS.forEach { (code, label) ->
            val chip = Chip(this).apply {
                text = label
                textSize = 13f
                isCheckable = true
                isChecked = Prefs.target == code
                setOnClickListener {
                    if (Prefs.target != code) { Prefs.target = code; applyLangChange() }
                    paintAll()
                }
            }
            langGroup.addView(chip)
        }
        root.addView(langGroup)

        // ---- وضع العرض ----
        title("وضع العرض")
        val modeGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        listOf(
            "below" to "ثنائي — تحت الأصل",
            "blur" to "ضبابي — يظهر بالتمرير",
            "replace" to "استبدال"
        ).forEach { (code, label) ->
            val chip = Chip(this).apply {
                text = label
                textSize = 13f
                isCheckable = true
                isChecked = Prefs.mode == code
                setOnClickListener {
                    Prefs.mode = code
                    paintAll()
                    web.evaluateJavascript(
                        "window.__imtSetMode && window.__imtSetMode(" + JSONObject.quote(code) + ");", null
                    )
                }
            }
            modeGroup.addView(chip)
        }
        root.addView(modeGroup)

        // ---- ترجمة تلقائية ----
        title("الموقع الحالي")
        val sw = MaterialSwitch(this).apply {
            text = "ترجمة تلقائية على " + (currentHost ?: "هالموقع")
            textSize = 14f
            setTextColor(Color.parseColor("#E7EAF2"))
            isChecked = Prefs.isAutoSite(currentHost)
            setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, checked ->
                if (Prefs.isAutoSite(currentHost) != checked) {
                    Prefs.toggleAutoSite(currentHost)
                    paintAll()
                }
            }
        }
        root.addView(sw)

        // ---- المحرّك ----
        title("محرّك الترجمة")
        root.addView(TextView(this).apply {
            text = Prefs.providerName()
            setTextColor(Color.parseColor("#7BA0FF"))
            textSize = 14f
        })
        root.addView(MaterialButton(this).apply {
            text = "إدارة المفاتيح والمحرّكات"
            textSize = 13f
            setPadding(0, dp(6), 0, 0)
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
        Snackbar.make(findViewById(R.id.web), msg, Snackbar.LENGTH_SHORT).show()

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
        web.evaluateJavascript("window.__imtStart && window.__imtStart();", null)
        tvStatus.text = "عم يترجم…  ·  " + Prefs.providerName()
    }

    private fun stopTranslation() {
        web.evaluateJavascript("window.__imtStop && window.__imtStop();", null)
        tvStatus.text = "أُوقفت الترجمة"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() { super.onPause(); Prefs.flushCache() }

    override fun onResume() {
        super.onResume()
        paintAll()
        injectedScript = null
        web.evaluateJavascript("window.__imtConfigChanged && window.__imtConfigChanged();", null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("url")?.let { web.loadUrl(it) }
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
            o.put("provider", Prefs.providerName())
            return o.toString()
        }

        @JavascriptInterface
        fun translate(id: String, textsJson: String) {
            if (!Prefs.enabled) { reply(id, "[]"); return }
            val texts: List<String> = try {
                val a = JSONArray(textsJson)
                List(a.length()) { a.getString(it) }
            } catch (e: Exception) { emptyList() }
            if (texts.isEmpty()) { reply(id, "[]"); return }

            pool.execute {
                val t0 = System.currentTimeMillis()
                val out = try {
                    TranslateEngine.translate(texts, Prefs.target)
                } catch (e: Exception) {
                    Log.e("IMT", "translate failed", e)
                    List(texts.size) { "" }
                }
                Log.d("IMT", "batch ${texts.size} in ${System.currentTimeMillis() - t0}ms")
                reply(id, JSONArray(out).toString())
            }
        }

        @JavascriptInterface
        fun status(done: Int, total: Int) {
            main.post {
                tvStatus.text = when {
                    total == 0 -> "ما لقيت نص قابل للترجمة بهالصفحة"
                    done >= total -> "✓ ترجمت $done مقطع · الكاش: " + Prefs.cacheSize()
                    else -> "عم يترجم…  $done/$total  ·  " + Prefs.providerName()
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
