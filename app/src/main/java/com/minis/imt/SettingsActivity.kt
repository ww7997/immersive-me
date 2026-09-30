package com.minis.imt

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

/**
 * شاشة الإعدادات: لغات، أوضاع العرض، وإدارة مفاتيح الذكاء الاصطناعي.
 * مبنية برمجياً بلا XML — لتقليل الملفات وزيادة الموثوقية.
 */
class SettingsActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var providerSpinner: Spinner
    private lateinit var nameEt: EditText
    private lateinit var baseEt: EditText
    private lateinit var keyEt: EditText
    private lateinit var modelEt: EditText
    private lateinit var promptEt: EditText
    private lateinit var targetEt: EditText
    private lateinit var modeGroup: RadioGroup
    private lateinit var statusTv: TextView

    private var providers: MutableList<Provider> = mutableListOf()
    private var suppress = false

    private val BG = 0xFF12141A.toInt()
    private val CARD = 0xFF1B1D22.toInt()
    private val FG = 0xFFE8EAED.toInt()
    private val MUTED = 0xFF8A93A6.toInt()
    private val ACCENT = 0xFF6E96FF.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        providers = Prefs.providers
        if (providers.isEmpty() && Prefs.activeProviderId != Provider.GOOGLE_ID) {
            Prefs.activeProviderId = Provider.GOOGLE_ID
        }
        buildUi()
        refreshSpinner()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(MUTED)
        textSize = 11f
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun field(hint: String, value: String, password: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            setTextColor(FG)
            setHintTextColor(MUTED)
            textSize = 14f
            setBackgroundColor(0xFF25282E.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { syncActive() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

    private fun button(text: String, accent: Boolean = false): Button =
        Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (accent) 0xFFFFFFFF.toInt() else MUTED)
            setBackgroundColor(if (accent) ACCENT else 0xFF2C3037.toInt())
        }

    private fun card(title: String, body: LinearLayout.() -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CARD)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val t = TextView(this).apply {
            text = title
            setTextColor(FG)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        box.addView(t)
        box.body()
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(12), dp(10), dp(12), 0)
        box.layoutParams = lp
        root.addView(box)
        return box
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(40))
        }
        scroll.addView(root)
        setContentView(scroll)

        // ---------- الترجمة ----------
        card("الترجمة") {
            addView(label("لغة الهدف (رمز ISO، مثال: ar, en, fr)"))
            targetEt = field("ar", Prefs.target)
            addView(targetEt)

            addView(label("وضع العرض"))
            modeGroup = RadioGroup(this@SettingsActivity).apply {
                orientation = RadioGroup.HORIZONTAL
            }
            listOf("below" to "ثنائي", "blur" to "ضبابي", "replace" to "استبدال").forEach { (v, t) ->
                val rb = RadioButton(this@SettingsActivity).apply {
                    text = t
                    id = View.generateViewId()
                    tag = v
                    setTextColor(FG)
                    textSize = 13f
                    isChecked = Prefs.mode == v
                }
                modeGroup.addView(rb)
            }
            modeGroup.setOnCheckedChangeListener { g, _ ->
                val rb = g.findViewById<RadioButton>(g.checkedRadioButtonId)
                Prefs.mode = (rb?.tag as? String) ?: "below"
            }
            addView(modeGroup)

            val auto = CheckBox(this@SettingsActivity).apply {
                text = "ترجمة تلقائية عند فتح أي صفحة"
                setTextColor(FG); textSize = 13f; isChecked = Prefs.autoTranslate
                setPadding(0, dp(10), 0, 0)
                setOnCheckedChangeListener { _, v -> Prefs.autoTranslate = v }
            }
            addView(auto)

            val desk = CheckBox(this@SettingsActivity).apply {
                text = "وضع سطح المكتب (User-Agent)"
                setTextColor(FG); textSize = 13f; isChecked = Prefs.desktopMode
                setOnCheckedChangeListener { _, v -> Prefs.desktopMode = v }
            }
            addView(desk)
        }

        // ---------- المزوّدون ----------
        card("محرّك الترجمة (مفاتيحك)") {
            addView(label("المزوّد الفعّال"))
            providerSpinner = Spinner(this@SettingsActivity)
            addView(providerSpinner)

            addView(label("الاسم"))
            nameEt = field("OpenAI / OpenRouter / Groq ...", "")
            addView(nameEt)

            addView(label("Base URL — نقطة نهاية متوافقة مع OpenAI"))
            baseEt = field("https://api.openai.com/v1", "")
            addView(baseEt)

            addView(label("API KEY (يُخزَّن محلياً على جهازك فقط)"))
            keyEt = field("sk-...", "", password = true)
            addView(keyEt)

            addView(label("MODEL"))
            modelEt = field("gpt-4o-mini", "")
            addView(modelEt)

            addView(label("SYSTEM PROMPT (اختياري)"))
            promptEt = field(Provider.DEFAULT_PROMPT, "")
            promptEt.setMinLines(2)
            addView(promptEt)

            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
            }
            val add = button("+ مزوّد جديد")
            add.setOnClickListener {
                val np = Provider.newDefault(providers.size)
                providers.add(np)
                Prefs.providers = providers
                Prefs.activeProviderId = np.id
                refreshSpinner()
                status("أُضيف «${np.name}» — عبّي المفتاح والموديل")
            }
            val del = button("حذف الحالي")
            del.setOnClickListener {
                val sel = providerSpinner.selectedItemPosition
                if (sel <= 0) { status("لا يمكن حذف Google المجاني"); return@setOnClickListener }
                val p = providers[sel - 1]
                providers.remove(p)
                Prefs.providers = providers
                Prefs.activeProviderId = Provider.GOOGLE_ID
                refreshSpinner()
                status("حُذف «${p.name}»")
            }
            val test = button("اختبار", accent = true)
            test.setOnClickListener { runTest() }

            row.addView(add, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(del, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(test, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(row)

            addView(label("نماذج جاهزة — Base URL"))
            val presets = listOf(
                "OpenAI|https://api.openai.com/v1|gpt-4o-mini",
                "OpenRouter|https://openrouter.ai/api/v1|google/gemini-flash-1.5",
                "Groq|https://api.groq.com/openai/v1|llama-3.3-70b-versatile",
                "DeepSeek|https://api.deepseek.com/v1|deepseek-chat",
                "Mistral|https://api.mistral.ai/v1|mistral-large-latest",
                "Together|https://api.together.xyz/v1|meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "سيرفر محلي (llama.cpp)|http://127.0.0.1:8080/v1|qwen3-4b-local",
                "Ollama محلي|http://127.0.0.1:11434/v1|qwen2.5:7b"
            )
            val chips = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, 0)
            }
            presets.forEach { preset ->
                val parts = preset.split("|")
                val b = TextView(this@SettingsActivity).apply {
                    text = "• " + parts[0]
                    setTextColor(ACCENT)
                    textSize = 13f
                    setPadding(0, dp(7), 0, dp(7))
                    setOnClickListener {
                        baseEt.setText(parts[1]); modelEt.setText(parts[2])
                        if (nameEt.text.isNullOrBlank()) nameEt.setText(parts[0])
                        status("تم تعبئة " + parts[0] + " — ضع المفتاح ثم اضغط اختبار")
                    }
                }
                chips.addView(b)
            }
            addView(chips)
        }

        // ---------- الكاش ----------
        card("الكاش") {
            statusTv = TextView(this@SettingsActivity).apply {
                setTextColor(MUTED); textSize = 12f
                text = "عدد الترجمات المخزّنة: " + Prefs.cacheSize()
            }
            addView(statusTv)
            val clr = button("تصفير كاش الترجمة")
            clr.setOnClickListener {
                Prefs.clearCache()
                status("تم تصفير الكاش")
            }
            addView(clr, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) })
        }

        val foot = TextView(this).apply {
            text = "Immersive-Me · نسخة خاصة · بلا اشتراك ولا حساب"
            setTextColor(MUTED); textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(foot)
    }

    private fun refreshSpinner() {
        suppress = true
        val names = mutableListOf(Provider.google.name)
        providers.forEach { names.add(it.name + "  ·  " + it.model) }
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )
        providerSpinner.adapter = adapter

        val activeId = Prefs.activeProviderId
        val idx = if (activeId == Provider.GOOGLE_ID) 0
                  else (providers.indexOfFirst { it.id == activeId } + 1).coerceAtLeast(0)
        providerSpinner.setSelection(idx)
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (suppress) return
                Prefs.activeProviderId = if (pos == 0) Provider.GOOGLE_ID else providers[pos - 1].id
                fillFields(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        fillFields(idx)
        suppress = false
    }

    private fun fillFields(pos: Int) {
        suppress = true
        if (pos == 0) {
            nameEt.setText(Provider.google.name); nameEt.isEnabled = false
            baseEt.setText(""); baseEt.isEnabled = false
            keyEt.setText(""); keyEt.isEnabled = false
            modelEt.setText(""); modelEt.isEnabled = false
            promptEt.setText(""); promptEt.isEnabled = false
        } else {
            val p = providers.getOrNull(pos - 1)
            if (p != null) {
                nameEt.isEnabled = true; baseEt.isEnabled = true
                keyEt.isEnabled = true; modelEt.isEnabled = true; promptEt.isEnabled = true
                nameEt.setText(p.name); baseEt.setText(p.baseUrl)
                keyEt.setText(p.apiKey); modelEt.setText(p.model)
                promptEt.setText(p.systemPrompt)
            }
        }
        suppress = false
    }

    /** يحفظ حقول الشاشة في المزوّد المختار */
    private fun syncActive() {
        if (suppress) return
        if (!::providerSpinner.isInitialized) return
        val pos = providerSpinner.selectedItemPosition
        if (pos <= 0) return
        val p = providers.getOrNull(pos - 1) ?: return
        p.name = nameEt.text.toString()
        p.baseUrl = baseEt.text.toString().trim()
        p.apiKey = keyEt.text.toString().trim()
        p.model = modelEt.text.toString().trim()
        p.systemPrompt = promptEt.text.toString()
        Prefs.providers = providers
        Prefs.target = targetEt.text.toString().trim().ifBlank { "ar" }
    }

    private fun status(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun runTest() {
        syncActive()
        Prefs.target = targetEt.text.toString().trim().ifBlank { "ar" }
        Toast.makeText(this, "جاري الاختبار…", Toast.LENGTH_SHORT).show()
        Thread {
            val sample = "The quick brown fox jumps over the lazy dog."
            val out = try {
                TranslateEngine.translate(listOf(sample), Prefs.target).firstOrNull() ?: "(فارغ)"
            } catch (e: Exception) {
                "❌ " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                statusTv.text = "نتيجة الاختبار:\n$out"
                Toast.makeText(
                    this,
                    if (out.startsWith("❌")) "فشل — شوف التفاصيل بالأسفل" else "نجح ✓",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        syncActive()
        Prefs.flushCache()
    }
}
