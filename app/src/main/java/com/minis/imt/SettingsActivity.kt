package com.minis.imt

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var providerBtn: MaterialButton
    private lateinit var hintTv: TextView

    private lateinit var nameEt: TextInputEditText
    private lateinit var baseEt: TextInputEditText
    private lateinit var keyEt: TextInputEditText
    private lateinit var modelEt: TextInputEditText
    private lateinit var promptEt: TextInputEditText

    private lateinit var nameTil: TextInputLayout
    private lateinit var baseTil: TextInputLayout
    private lateinit var keyTil: TextInputLayout
    private lateinit var modelTil: TextInputLayout
    private lateinit var promptTil: TextInputLayout

    private var providers: MutableList<Provider> = mutableListOf()
    private var suppress = false

    private val C_BG = "#0D1015"
    private val C_SURFACE = "#161A21"
    private val C_SURFACE2 = "#1F242D"
    private val C_TEXT = "#E7EAF2"
    private val C_MUTED = "#8B94A7"
    private val C_BRAND = "#7BA0FF"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        providers = Prefs.providers
        if (providers.isEmpty() && Prefs.activeProviderId != Provider.GOOGLE_ID) {
            Prefs.activeProviderId = Provider.GOOGLE_ID
        }
        buildUi()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, top: Int = 16): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor(C_MUTED))
        textSize = 12f
        setPadding(0, dp(top), 0, dp(6))
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor(C_TEXT))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(title: String, body: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(sectionTitle(title))
        box.body()

        val cardView = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            setCardBackgroundColor(Color.parseColor(C_SURFACE))
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(ColorStateList.valueOf(Color.parseColor(C_SURFACE2)))
            setContentPadding(dp(16), dp(16), dp(16), dp(18))
            addView(box)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(14), dp(12), dp(14), 0)
        cardView.layoutParams = lp
        root.addView(cardView)
    }

    private fun til(hint: String, value: String, password: Boolean = false): TextInputLayout =
        TextInputLayout(this).apply {
            this.hint = hint
            boxBackgroundColor = Color.parseColor(C_SURFACE2)
            boxStrokeColor = Color.parseColor(C_BRAND)
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(8), 0, 0)
            layoutParams = lp
        }

    private fun field(value: String, password: Boolean = false): TextInputEditText =
        TextInputEditText(this).apply {
            setText(value)
            setTextColor(Color.parseColor(C_TEXT))
            textSize = 14f
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

    private fun addField(container: LinearLayout, hint: String, value: String, password: Boolean = false,
                         onReady: (TextInputLayout, TextInputEditText) -> Unit) {
        val t = til(hint, value, password)
        val e = field(value, password)
        t.addView(e)
        container.addView(t)
        onReady(t, e)
    }

    private fun btn(text: String, filled: Boolean = false): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            textSize = 13f
            cornerRadius = dp(12)
            if (filled) {
                setBackgroundColor(Color.parseColor(C_BRAND))
                setTextColor(Color.parseColor("#0B1020"))
            } else {
                setBackgroundColor(Color.parseColor(C_SURFACE2))
                setTextColor(Color.parseColor(C_TEXT))
            }
        }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor(C_BG)) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(48))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "الإعدادات"
            setTextColor(Color.parseColor(C_TEXT))
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(20), dp(8), 0, 0)
        })

        /* ---------- الترجمة ---------- */
        card("الترجمة") {
            addView(label("لغة الهدف", 14))
            val langGroup = ChipGroup(this@SettingsActivity).apply { isSingleSelection = true }
            listOf("ar" to "العربية", "en" to "English", "de" to "Deutsch", "fr" to "Français",
                   "es" to "Español", "tr" to "Türkçe", "ru" to "Русский", "fa" to "فارسی",
                   "zh-CN" to "中文", "ja" to "日本語").forEach { (code, lbl) ->
                langGroup.addView(Chip(this@SettingsActivity).apply {
                    text = lbl
                    textSize = 13f
                    isCheckable = true
                    isChecked = Prefs.target == code
                    setOnClickListener { Prefs.target = code }
                })
            }
            addView(langGroup)

            addView(label("وضع العرض"))
            val modeGroup = ChipGroup(this@SettingsActivity).apply { isSingleSelection = true }
            listOf("below" to "ثنائي", "blur" to "ضبابي", "replace" to "استبدال").forEach { (code, lbl) ->
                modeGroup.addView(Chip(this@SettingsActivity).apply {
                    text = lbl
                    textSize = 13f
                    isCheckable = true
                    isChecked = Prefs.mode == code
                    setOnClickListener { Prefs.mode = code }
                })
            }
            addView(modeGroup)

            addView(label("لهجة الترجمة العربية — تعمل مع محرّكات الذكاء الاصطناعي"))
            val dGroup = ChipGroup(this@SettingsActivity).apply { isSingleSelection = true }
            listOf("sy" to "سورية", "lb" to "لبنانية", "eg" to "مصرية",
                   "gulf" to "خليجية", "iq" to "عراقية", "ma" to "مغربية",
                   "fusha" to "فصحى").forEach { (code, lbl) ->
                dGroup.addView(Chip(this@SettingsActivity).apply {
                    text = lbl
                    textSize = 13f
                    isCheckable = true
                    isChecked = Prefs.dialect == code
                    setOnClickListener { Prefs.dialect = code }
                })
            }
            addView(dGroup)

            addView(label("شخصية الترجمة (أسلوب)"))
            val pGroup = ChipGroup(this@SettingsActivity).apply { isSingleSelection = true }
            listOf("" to "عادي", "pro" to "احترافي", "academic" to "أكاديمي",
                   "fun" to "ساخر", "simple" to "بسيط جداً", "child" to "طفولي").forEach { (code, lbl) ->
                pGroup.addView(Chip(this@SettingsActivity).apply {
                    text = lbl
                    textSize = 13f
                    isCheckable = true
                    isChecked = Prefs.persona == code
                    setOnClickListener { Prefs.persona = code }
                })
            }
            addView(pGroup)

            addView(label("لهجة مخصصة — وصف حر (يتقدّم على الشرائح فوق)"))
            val cDial = TextInputEditText(this@SettingsActivity).apply {
                hint = "مثال: لهجة حلب التجارية، بلا كلمات فرنسية، أسلوب بسيط"
                setText(Prefs.customDialect)
                setTextColor(Color.parseColor(C_TEXT))
                textSize = 13f
            }
            val cTil = TextInputLayout(this@SettingsActivity).apply {
                boxBackgroundColor = Color.parseColor(C_SURFACE2)
                setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
                addView(cDial)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, dp(6), 0, 0)
                layoutParams = lp
            }
            cDial.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    Prefs.customDialect = s?.toString() ?: ""
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            addView(cTil)

            addView(MaterialSwitch(this@SettingsActivity).apply {
                text = "ترجمة تدريجية — الأسرع (يترجم اللي قدامك، والباقي مع التمرير)"
                textSize = 13f
                setTextColor(Color.parseColor(C_TEXT))
                isChecked = Prefs.lazyTranslate
                setPadding(0, dp(14), 0, 0)
                setOnCheckedChangeListener { _, v -> Prefs.lazyTranslate = v }
            })

            addView(label("حجم الدفعة — أصغر = ظهور أسرع، أكبر = إنتاجية أعلى"))
            val bGroup = ChipGroup(this@SettingsActivity).apply { isSingleSelection = true }
            listOf(0 to "تلقائي", 3 to "٣ أسرع ظهور", 5 to "٥", 8 to "٨", 12 to "١٢ أعلى إنتاجية").forEach { (v, lbl) ->
                bGroup.addView(Chip(this@SettingsActivity).apply {
                    text = lbl
                    textSize = 12f
                    isCheckable = true
                    isChecked = Prefs.batchOverride == v
                    setOnClickListener { Prefs.batchOverride = v }
                })
            }
            addView(bGroup)

            addView(label("التزامن — كم طلب متوازي"))
            val cGroup = ChipGroup(this@SettingsActivity).apply { isSingleSelection = true }
            listOf(0 to "تلقائي", 1 to "١", 2 to "٢", 3 to "٣", 4 to "٤").forEach { (v, lbl) ->
                cGroup.addView(Chip(this@SettingsActivity).apply {
                    text = lbl
                    textSize = 12f
                    isCheckable = true
                    isChecked = Prefs.concOverride == v
                    setOnClickListener { Prefs.concOverride = v }
                })
            }
            addView(cGroup)

            addView(MaterialSwitch(this@SettingsActivity).apply {
                text = "وضع سطح المكتب (User-Agent)"
                textSize = 14f
                setTextColor(Color.parseColor(C_TEXT))
                isChecked = Prefs.desktopMode
                setPadding(0, dp(14), 0, 0)
                setOnCheckedChangeListener { _, v -> Prefs.desktopMode = v }
            })
        }

        /* ---------- المحرّك ---------- */
        card("محرّك الترجمة") {
            addView(label("المزوّد الفعّال", 14))
            providerBtn = btn("—")
            providerBtn.setOnClickListener { chooseProvider() }
            addView(providerBtn)
            hintTv = TextView(this@SettingsActivity).apply {
                setTextColor(Color.parseColor(C_MUTED))
                textSize = 12f
                setPadding(dp(4), dp(10), 0, 0)
            }
            addView(hintTv)

            addField(this, "الاسم", "") { t, e -> nameTil = t; nameEt = e }
            addField(this, "Base URL (متوافق مع OpenAI)", "") { t, e -> baseTil = t; baseEt = e }
            addField(this, "API Key — يُخزَّن على جهازك فقط", "", true) { t, e -> keyTil = t; keyEt = e }
            addField(this, "Model", "") { t, e -> modelTil = t; modelEt = e }

            // زر التحقّق من المفتاح وجلب الموديلات الحقيقية
            addView(MaterialButton(this@SettingsActivity).apply {
                text = "🔑 تحقّق من المفتاح واجلب الموديلات"
                textSize = 13f
                cornerRadius = dp(12)
                setBackgroundColor(Color.parseColor(C_BRAND))
                setTextColor(Color.parseColor("#0B1020"))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, dp(12), 0, 0)
                layoutParams = lp
                setOnClickListener { fetchModels() }
            })
            addField(this, "System Prompt (اختياري)", "") { t, e -> promptTil = t; promptEt = e }

            nameEt.addTextChangedListener(simpleWatcher())
            baseEt.addTextChangedListener(simpleWatcher())
            keyEt.addTextChangedListener(simpleWatcher())
            modelEt.addTextChangedListener(simpleWatcher())
            promptEt.addTextChangedListener(simpleWatcher())

            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
            }
            val add = btn("+ مزوّد")
            add.setOnClickListener {
                val np = Provider.newDefault(providers.size)
                providers.add(np)
                Prefs.providers = providers
                Prefs.activeProviderId = np.id
                fillFields()
                snack("أُضيف «${np.name}» — عبّي المفتاح والموديل")
            }
            val del = btn("حذف")
            del.setOnClickListener {
                val p = Prefs.activeProvider()
                if (p == null) { snack("لا يمكن حذف Google المجاني"); return@setOnClickListener }
                providers.remove(p)
                Prefs.providers = providers
                Prefs.activeProviderId = Provider.GOOGLE_ID
                fillFields()
                snack("حُذف «${p.name}»")
            }
            val test = btn("اختبار", true)
            test.setOnClickListener { runTest() }

            row.addView(add, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(del, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(test, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(row)

            addView(label("نماذج جاهزة — اضغط للتعبئة"))
            val presets = listOf(
                "OpenRouter ⚡ فلاش|https://openrouter.ai/api/v1|google/gemini-2.5-flash-lite",
                "OpenRouter فلاش قوي|https://openrouter.ai/api/v1|google/gemini-2.5-flash",
                "OpenRouter Haiku|https://openrouter.ai/api/v1|anthropic/claude-haiku-4.5",
                "OpenRouter DeepSeek فلاش|https://openrouter.ai/api/v1|deepseek/deepseek-v4-flash",
                "Groq (سريع جداً)|https://api.groq.com/openai/v1|llama-3.3-70b-versatile",
                "DeepSeek مباشر|https://api.deepseek.com/v1|deepseek-chat",
                "OpenAI|https://api.openai.com/v1|gpt-4o-mini",
                "Mistral|https://api.mistral.ai/v1|mistral-large-latest",
                "Together|https://api.together.xyz/v1|meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "llama.cpp محلي|http://127.0.0.1:8080/v1|qwen3-4b-local",
                "Ollama محلي|http://127.0.0.1:11434/v1|qwen2.5:7b"
            )
            presets.forEach { preset ->
                val p = preset.split("|")
                addView(MaterialButton(this@SettingsActivity).apply {
                    text = p[0]
                    textSize = 13f
                    cornerRadius = dp(12)
                    setBackgroundColor(Color.parseColor(C_SURFACE2))
                    setTextColor(Color.parseColor(C_BRAND))
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, dp(6), 0, 0)
                    layoutParams = lp
                    setOnClickListener {
                        if (Prefs.activeProvider() == null) {
                            val np = Provider.newDefault(providers.size)
                            providers.add(np)
                            Prefs.providers = providers
                            Prefs.activeProviderId = np.id
                        }
                        nameEt.setText(p[0]); baseEt.setText(p[1]); modelEt.setText(p[2])
                        syncActive()
                        fillFields()
                        providerBtn.text = p[0]
                        nameTil.error = null
                        snack("✓ تعبّى «${p[0]}» — الحقول فوق. ضع المفتاح ثم اضغط اختبار")
                    }
                })
            }
        }

        /* ---------- الكاش ---------- */
        card("الكاش والتشخيص") {
            statusTv = TextView(this@SettingsActivity).apply {
                setTextColor(Color.parseColor(C_MUTED))
                textSize = 13f
                text = "عدد الترجمات المخزّنة: " + Prefs.cacheSize()
                setPadding(0, dp(10), 0, 0)
            }
            addView(statusTv)
            addView(btn("تصفير الكاش").apply {
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, dp(12), 0, 0)
                layoutParams = lp
                setOnClickListener {
                    Prefs.clearCache()
                    statusTv.text = "تم تصفير الكاش ✓"
                }
            })
        }

        root.addView(TextView(this).apply {
            text = "Immersive-Me v3.0 · ملكك · بلا اشتراك ولا حساب"
            setTextColor(Color.parseColor("#4A5263"))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(26), 0, 0)
        })

        fillFields()
    }

    private fun simpleWatcher() = object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { syncActive() }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }

    private fun chooseProvider() {
        val names = mutableListOf(Provider.google.name)
        providers.forEach { names.add(it.name + "  ·  " + it.model) }
        val current = if (Prefs.activeProviderId == Provider.GOOGLE_ID) 0
                      else providers.indexOfFirst { it.id == Prefs.activeProviderId } + 1
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر محرّك الترجمة")
            .setSingleChoiceItems(ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, names), current.coerceAtLeast(0)) { d, which ->
                Prefs.activeProviderId = if (which == 0) Provider.GOOGLE_ID else providers[which - 1].id
                d.dismiss()
                fillFields()
                snack("المحرّك الفعّال: " + Prefs.providerName())
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun fillFields() {
        suppress = true
        providerBtn.text = Prefs.providerName()
        val p = providers.firstOrNull { it.id == Prefs.activeProviderId }
        val keyInfo = when {
            p == null -> ""
            p.apiKey.isBlank() -> " • المفتاح: ❌ فاضي"
            else -> " • المفتاح: ✓ ${p.apiKey.length} حرف (…${p.apiKey.takeLast(4)})"
        }
        if (p == null) {
            nameEt.setText(Provider.google.name)
            baseEt.setText("—"); keyEt.setText("—"); modelEt.setText("—"); promptEt.setText("")
            listOf(nameTil, baseTil, keyTil, modelTil, promptTil).forEach { it.isEnabled = false }
        } else {
            listOf(nameTil, baseTil, keyTil, modelTil, promptTil).forEach { it.isEnabled = true }
            nameEt.setText(p.name)
            baseEt.setText(p.baseUrl)
            keyEt.setText(p.apiKey)
            modelEt.setText(p.model)
            promptEt.setText(p.systemPrompt)
        }
        hintTv.text = if (p == null)
            "✓ جاهز — محرّك مجاني بلا مفتاح"
        else if (Prefs.effectiveProvider() != null)
            "✓ جاهز — الترجمة رح تستعمل «${p.name}»$keyInfo"
        else
            "⚠️ ناقص: ${if (p.apiKey.isBlank()) "API Key" else ""}${if (p.baseUrl.isBlank()) " Base URL" else ""}${if (p.model.isBlank()) " Model" else ""}$keyInfo — الترجمة رح تستعمل Google المجاني"
        suppress = false
    }

    private fun syncActive() {
        if (suppress) return
        val idx = providers.indexOfFirst { it.id == Prefs.activeProviderId }
        if (idx < 0) return
        if (!::nameEt.isInitialized) return
        val p = providers[idx]                       // ← المزوّد الحقيقي بالقائمة المحلية
        p.name = nameEt.text?.toString()?.trim().orEmpty()
        p.baseUrl = baseEt.text?.toString()?.trim().orEmpty()
        p.apiKey = (keyEt.text?.toString() ?: "").trim()
            .removePrefix("Bearer ").removePrefix("bearer ").trim()   // نتقبّل لو لصقت "Bearer" معو
        p.model = modelEt.text?.toString()?.trim().orEmpty()
        p.systemPrompt = promptEt.text?.toString().orEmpty()
        Prefs.providers = providers
    }

    private fun snack(msg: String) =
        com.google.android.material.snackbar.Snackbar
            .make(findViewById(android.R.id.content), msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .show()

    /** يجلب الموديلات من المزوّد — ودليل نجاحها إنو المفتاح شغّال */
    private fun fetchModels() {
        syncActive()
        val p = providers.firstOrNull { it.id == Prefs.activeProviderId }
        if (p == null) { snack("اختار مزوّد أول — Google المجاني ما بدو موديل"); return }
        if (p.baseUrl.isBlank()) { snack("عبّي Base URL أول"); return }
        hintTv.text = "⏳ جاري التحقّق من المفتاح وجلب الموديلات…"
        Thread {
            try {
                val models = TranslateEngine.listModels(p)
                runOnUiThread {
                    hintTv.text = "✓ المفتاح شغّال — ${models.size} موديل متاح من «${p.name}»"
                    snack("✓ المفتاح صحيح — اختر موديل")
                    showModelPicker(p.name, models)
                }
            } catch (e: Exception) {
                val msg = TranslateEngine.describe(e)
                runOnUiThread {
                    hintTv.text = "✗ فشل: $msg"
                    snack("✗ $msg")
                }
            }
        }.start()
    }

    private fun showModelPicker(providerName: String, models: List<String>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(4))
        }
        val filter = TextInputEditText(this).apply {
            hint = "ابحث…"
            textSize = 14f
            setTextColor(Color.parseColor(C_TEXT))
        }
        box.addView(TextInputLayout(this).apply {
            hint = "فلترة الموديلات"
            boxBackgroundColor = Color.parseColor(C_SURFACE2)
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            addView(filter)
        })

        // ⚡ = سريع جداً · 🐢 = بطيء · 🐌 = تفكير (الأبطأ)
        val FAST = Regex("(?i)(flash|mini|turbo|haiku|instant|small|lite|nano|8b|9b|scout)")
        val SLOW = Regex("(?i)(reason|thinking|think|-r1|/r1|o1-|o3-|opus|405b|235b|110b)")
        fun mark(m: String) = when {
            FAST.containsMatchIn(m) -> "⚡ "
            SLOW.containsMatchIn(m) -> "🐢 "
            else -> "   "
        }
        val list = android.widget.ListView(this)
        val data = ArrayList(models.map { mark(it) + it })
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
        list.adapter = adapter
        list.dividerHeight = dp(1)

        var dlg: androidx.appcompat.app.AlertDialog? = null
        list.setOnItemClickListener { _, _, pos, _ ->
            val shown = adapter.getItem(pos) ?: return@setOnItemClickListener
            val picked = shown.substring(2)      // نشيل علامة السرعة
            modelEt.setText(picked)
            syncActive()
            fillFields()
            dlg?.dismiss()
            snack(
                when {
                    shown.startsWith("⚡") -> "⚡ «$picked» موديل سريع — اختيار ممتاز"
                    shown.startsWith("🐢") ->
                        "🐢 «$picked» بطيء بالترجمة — جرّب موديل فيه flash أو mini أو turbo"
                    else -> "الموديل: $picked — اضغط اختبار للتأكيد"
                }
            )
        }

        filter.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { adapter.filter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        box.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(360)
        ).apply { setMargins(0, dp(10), 0, 0) })

        dlg = MaterialAlertDialogBuilder(this)
            .setTitle("موديلات $providerName (${models.size})")
            .setMessage("⚡ أسرع · 🐢 أبطأ — للترجمة بدك ⚡")
            .setView(box)
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun runTest() {
        syncActive()
        statusTv.text = "جاري الاختبار…"
        Thread {
            val sb = StringBuilder()
            sb.append("المحرّك: ").append(Prefs.effectiveName()).append("\n")
            val ap = Prefs.activeProvider()
            sb.append("المفتاح: ").append(
                when {
                    ap == null -> "غير مطلوب (محرّك مجاني)"
                    ap.apiKey.isBlank() -> "❌ فاضي — ما انحفظ!"
                    else -> "✓ ${ap.apiKey.length} حرف (…${ap.apiKey.takeLast(4)})"
                }
            ).append("\n")
            sb.append("العنوان: ").append(ap?.baseUrl ?: "-").append("\n")
            sb.append("الموديل: ").append(ap?.model ?: "-").append("\n\n")

            // ---- اختبار ١: مقطع واحد ----
            TranslateEngine.clearError()
            val one = try {
                TranslateEngine.translate(listOf("Good morning, how are you today?"), Prefs.target)
                    .firstOrNull() ?: ""
            } catch (e: Exception) { "" }
            if (one.isNotBlank()) sb.append("✓ مقطع واحد نجح:\n  ").append(one).append("\n\n")
            else sb.append("✗ مقطع واحد فشل: ").append(TranslateEngine.lastError ?: "فشل بلا رسالة").append("\n\n")

            // ---- اختبار ٢: دفعة من ٣ (هون بيتكشّف مشكل التركيب) ----
            TranslateEngine.clearError()
            val three = try {
                TranslateEngine.translate(
                    listOf("The cat sits on the mat.", "Water boils at one hundred degrees.",
                           "She reads a book every evening."), Prefs.target)
            } catch (e: Exception) { emptyList() }
            val ok = three.count { it.isNotBlank() }
            if (ok == 3) {
                sb.append("✓ الدفعة (٣ مقاطع) نجحت:\n")
                three.forEachIndexed { i, t -> sb.append("  [${i + 1}] ").append(t).append("\n") }
            } else {
                sb.append("✗ الدفعة فشلت — نجح ").append(ok).append(" من ٣\n")
                sb.append("  السبب: ").append(TranslateEngine.lastError ?: "؟").append("\n")
            }

            val text = sb.toString()
            runOnUiThread {
                statusTv.text = text
                snack(if (text.contains("✗")) "في فشل — شوف التفاصيل تحت" else "كل الاختبارات نجحت ✓")
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        syncActive()
        Prefs.flushCache()
    }
}
