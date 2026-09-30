package com.minis.imt

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var btnGo: TextView
    private lateinit var btnTr: TextView
    private lateinit var btnCfg: TextView
    private lateinit var tvLang: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvMode: TextView

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private var injectedScript: String? = null
    private var currentHost: String? = null

    private val ACCENT = Color.parseColor("#6E96FF")
    private val GREEN = Color.parseColor("#2F9E44")
    private val DIM = Color.parseColor("#8A93A6")

    private val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val LANGS = arrayOf(
        "ar" to "العربية", "en" to "English", "de" to "Deutsch", "fr" to "Français",
        "es" to "Español", "it" to "Italiano", "tr" to "Türkçe", "ru" to "Русский",
        "fa" to "فارسی", "ur" to "اردو", "zh-CN" to "中文", "ja" to "日本語",
        "ko" to "한국어", "hi" to "हिन्दी", "pt" to "Português", "nl" to "Nederlands"
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
        tvLang = findViewById(R.id.tvLang)
        tvStatus = findViewById(R.id.tvStatus)
        tvMode = findViewById(R.id.tvMode)

        setupWebView()
        setupToolbar()
        paintAll()

        val start = intent?.getStringExtra("url") ?: Prefs.homePage
        if (savedInstanceState == null) web.loadUrl(start)
    }

    /* ================= الواجهة ================= */

    private fun setupToolbar() {
        btnGo.setOnClickListener { go() }

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                go(); true
            } else false
        }

        // ضغطة = تشغيل/إيقاف · ضغط مطوّل = تفعيل تلقائي لهالموقع
        btnTr.setOnClickListener {
            Prefs.enabled = !Prefs.enabled
            if (Prefs.enabled) startTranslation() else stopTranslation()
            paintAll()
        }
        btnTr.setOnLongClickListener {
            val on = Prefs.toggleAutoSite(currentHost)
            toast(
                if (on) "✅ الترجمة التلقائية مفعّلة على $currentHost"
                else "أُوقفت الترجمة التلقائية على $currentHost"
            )
            Prefs.enabled = on
            if (on) startTranslation() else stopTranslation()
            paintAll()
            true
        }

        btnCfg.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        tvLang.setOnClickListener { pickLanguage() }
        tvMode.setOnClickListener { cycleMode() }
    }

    private fun paintAll() {
        if (Prefs.enabled) {
            btnTr.setTextColor(GREEN)
            btnTr.text = "译●"
        } else {
            btnTr.setTextColor(DIM)
            btnTr.text = "译"
        }
        tvLang.text = Prefs.target.uppercase()
        tvMode.text = when (Prefs.mode) {
            "blur" -> "ضبابي"
            "replace" -> "استبدال"
            else -> "ثنائي"
        }
        if (!Prefs.enabled) {
            tvStatus.text = if (Prefs.isAutoSite(currentHost))
                "تلقائي مفعّل هون · اضغط 译" else "جاهز · اضغط 译 · ضغط مطوّل = تلقائي دائم"
        }
    }

    private fun pickLanguage() {
        val labels = LANGS.map { it.second + "  (" + it.first + ")" }.toTypedArray()
        val current = LANGS.indexOfFirst { it.first == Prefs.target }
        AlertDialog.Builder(this)
            .setTitle("لغة الترجمة")
            .setSingleChoiceItems(labels, current) { d, which ->
                Prefs.target = LANGS[which].first
                paintAll()
                d.dismiss()
                if (Prefs.enabled) {
                    web.evaluateJavascript("window.__imtStop && window.__imtStop();", null)
                    main.postDelayed({
                        web.evaluateJavascript("window.__imtConfigChanged && window.__imtConfigChanged();", null)
                        main.postDelayed({ startTranslation() }, 150)
                    }, 150)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun cycleMode() {
        val order = arrayOf("below", "blur", "replace")
        val next = order[(order.indexOf(Prefs.mode).coerceAtLeast(0) + 1) % order.size]
        Prefs.mode = next
        paintAll()
        web.evaluateJavascript(
            "window.__imtSetMode && window.__imtSetMode(" + JSONObject.quote(next) + ");", null
        )
    }

    private fun go() {
        var q = urlBar.text.toString().trim()
        if (q.isEmpty()) return
        if (!q.contains(".") || q.contains(" ")) {
            q = "https://www.google.com/search?q=" + Uri.encode(q)
        } else if (!q.startsWith("http")) {
            q = "https://$q"
        }
        web.loadUrl(q)
    }

    /* ================= WebView ================= */

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
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
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
                currentHost = try { Uri.parse(url).host?.lowercase() } catch (e: Exception) { null }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                urlBar.setText(url ?: "")
                currentHost = try { Uri.parse(url).host?.lowercase() } catch (e: Exception) { null }

                // ترجمة تلقائية للمواقع المفعّلة
                if (Prefs.isAutoSite(currentHost)) Prefs.enabled = true

                injectTranslator()
                paintAll()
            }
        }
    }

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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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

    /* ================= جسر JS ↔ Kotlin ================= */

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
                val ms = System.currentTimeMillis() - t0
                Log.d("IMT", "batch ${texts.size} -> ${ms}ms")
                reply(id, JSONArray(out).toString())
            }
        }

        @JavascriptInterface
        fun status(done: Int, total: Int) {
            main.post {
                tvStatus.text = when {
                    total == 0 -> "ما لقيت نص قابل للترجمة بهالصفحة"
                    done >= total -> "✅ ترجمت $done مقطع · كاش: " + Prefs.cacheSize()
                    else -> "عم يترجم… $done/$total  ·  " + Prefs.providerName()
                }
            }
        }

        @JavascriptInterface
        fun saveMode(m: String) {
            Prefs.mode = m
            main.post { paintAll() }
        }

        @JavascriptInterface
        fun setEnabled(on: Boolean) {
            Prefs.enabled = on
            main.post { paintAll() }
        }

        @JavascriptInterface
        fun notify(msg: String) { main.post { toast(msg) } }

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
