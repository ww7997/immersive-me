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
            strokeColor = ColorStateList.valueOf(Color.parseColor(C_SURFACE2))
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

            addField(this, "الاسم", "") { t, e -> nameTil = t; nameEt = e }
            addField(this, "Base URL (متوافق مع OpenAI)", "") { t, e -> baseTil = t; baseEt = e }
            addField(this, "API Key — يُخزَّن على جهازك فقط", "", true) { t, e -> keyTil = t; keyEt = e }
            addField(this, "Model", "") { t, e -> modelTil = t; modelEt = e }
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
                "OpenAI|https://api.openai.com/v1|gpt-4o-mini",
                "OpenRouter|https://openrouter.ai/api/v1|google/gemini-flash-1.5",
                "Groq|https://api.groq.com/openai/v1|llama-3.3-70b-versatile",
                "DeepSeek|https://api.deepseek.com/v1|deepseek-chat",
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
                        snack("تم تعبئة ${p[0]} — ضع المفتاح ثم اضغط اختبار")
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
            text = "Immersive-Me v2.0 · ملكك · بلا اشتراك ولا حساب"
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
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun fillFields() {
        suppress = true
        providerBtn.text = Prefs.providerName()
        val p = Prefs.activeProvider()
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
        suppress = false
    }

    private fun syncActive() {
        if (suppress) return
        val p = Prefs.activeProvider() ?: return
        p.name = nameEt.text?.toString() ?: ""
        p.baseUrl = baseEt.text?.toString()?.trim() ?: ""
        p.apiKey = keyEt.text?.toString()?.trim() ?: ""
        p.model = modelEt.text?.toString()?.trim() ?: ""
        p.systemPrompt = promptEt.text?.toString() ?: ""
        Prefs.providers = providers
    }

    private fun snack(msg: String) =
        com.google.android.material.snackbar.Snackbar
            .make(findViewById(android.R.id.content), msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .show()

    private fun runTest() {
        syncActive()
        val p = Prefs.activeProvider()
        if (p != null && p.baseUrl.isBlank()) { snack("عبّي Base URL أول"); return }
        if (p != null && p.model.isBlank()) { snack("عبّي اسم الموديل أول"); return }
        statusTv.text = "جاري الاختبار…"
        Thread {
            val out = try {
                val r = TranslateEngine.translate(listOf("The quick brown fox jumps over the lazy dog."), Prefs.target)
                "✓ نجح — الرد:\n" + (r.firstOrNull() ?: "(فارغ)")
            } catch (e: Exception) {
                "✗ فشل — " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                statusTv.text = out
                snack(if (out.startsWith("✓")) "نجح الاختبار" else "فشل الاختبار — التفاصيل بالأسفل")
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        syncActive()
        Prefs.flushCache()
    }
}
