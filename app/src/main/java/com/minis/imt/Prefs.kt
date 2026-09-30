package com.minis.imt

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * مزوّد ترجمة (مفتاح ذكاء اصطناعي يفعّله المستخدم بنفسه).
 * أي واجهة متوافقة مع OpenAI: OpenAI, OpenRouter, Groq, DeepSeek, Gemini(OpenAI-compat),
 * Anthropic عبر بوابة متوافقة، أو سيرفر محلي (llama.cpp / Ollama / LM Studio).
 */
data class Provider(
    var id: String,
    var name: String,
    var baseUrl: String,
    var apiKey: String,
    var model: String,
    var systemPrompt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("baseUrl", baseUrl)
        put("apiKey", apiKey); put("model", model); put("systemPrompt", systemPrompt)
    }

    companion object {
        const val GOOGLE_ID = "__google__"
        const val DEFAULT_PROMPT =
            "You are a precise translation engine. Translate the user's text faithfully " +
            "into the requested language. Never explain, never add commentary, never refuse. " +
            "Preserve inline punctuation, code, URLs and numbers exactly as they are."

        fun fromJson(o: JSONObject): Provider = Provider(
            o.optString("id"), o.optString("name"), o.optString("baseUrl"),
            o.optString("apiKey"), o.optString("model"),
            o.optString("systemPrompt", DEFAULT_PROMPT)
        )

        val google = Provider(
            GOOGLE_ID, "Google (مجاني — بلا مفتاح)",
            "https://translate.googleapis.com", "", "", ""
        )

        fun newDefault(index: Int) = Provider(
            "p" + System.currentTimeMillis().toString(36) + index,
            "مزوّدي " + (index + 1),
            "https://api.openai.com/v1",
            "",
            "gpt-4o-mini"
        )
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    private var cacheFile: File? = null

    /** ذاكرة ترجمة مؤقتة داخل الجلسة */
    private val memCache = LinkedHashMap<String, String>(1024, 0.75f, true)
    private var cacheDirty = false

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("imt", Context.MODE_PRIVATE)
        cacheFile = File(ctx.filesDir, "translation_cache.json")
        loadCache()
    }

    // ---------- إعدادات عامة ----------
    var target: String
        get() = sp.getString("target", "ar")!!
        set(v) = sp.edit().putString("target", v).apply()

    /** below | blur | replace */
    var mode: String
        get() = sp.getString("mode", "below")!!
        set(v) = sp.edit().putString("mode", v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var autoTranslate: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop", false)
        set(v) = sp.edit().putBoolean("desktop", v).apply()

    var homePage: String
        get() = sp.getString("home", "https://www.google.com")!!
        set(v) = sp.edit().putString("home", v).apply()

    /** مواقع مفعّل فيها الترجمة التلقائية (بالـ host فقط) */
    val autoSites: MutableSet<String>
        get() = (sp.getString("autoSites", "") ?: "")
            .split('|').filter { it.isNotBlank() }.toMutableSet()

    fun saveAutoSites(set: Collection<String>) {
        sp.edit().putString("autoSites", set.joinToString("|")).apply()
    }

    fun isAutoSite(host: String?): Boolean =
        !host.isNullOrBlank() && autoSites.contains(host.lowercase())

    fun toggleAutoSite(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        val cur = autoSites
        val now = if (cur.contains(h)) { cur.remove(h); false } else { cur.add(h); true }
        saveAutoSites(cur)
        return now
    }

    /** ترجمة النص المظلَّل */
    var selectionTranslate: Boolean
        get() = sp.getBoolean("selTr", true)
        set(v) = sp.edit().putBoolean("selTr", v).apply()

    /** ترجمة صناديق الإدخال */
    var inputTranslate: Boolean
        get() = sp.getBoolean("inpTr", true)
        set(v) = sp.edit().putBoolean("inpTr", v).apply()

    // ---------- سجل الصفحات ----------
    var history: MutableList<Pair<String, String>>
        get() {
            val raw = sp.getString("history", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(raw)
                MutableList(arr.length()) {
                    val o = arr.getJSONObject(it)
                    o.optString("u") to o.optString("t")
                }
            } catch (e: Exception) { mutableListOf() }
        }
        set(list) {
            val arr = JSONArray()
            list.take(20).forEach { arr.put(JSONObject().put("u", it.first).put("t", it.second)) }
            sp.edit().putString("history", arr.toString()).apply()
        }

    fun addHistory(url: String?, title: String?) {
        if (url.isNullOrBlank() || title.isNullOrBlank()) return
        if (!url.startsWith("http")) return
        if (title.startsWith("http") || title.length < 4) return
        val cur = history.filter { it.first != url }.toMutableList()
        cur.add(0, url to title)
        history = cur
    }

    // ---------- المزوّدون ----------
    var providers: MutableList<Provider>
        get() {
            val raw = sp.getString("providers", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(raw)
                MutableList(arr.length()) { Provider.fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) { mutableListOf() }
        }
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            sp.edit().putString("providers", arr.toString()).apply()
        }

    var activeProviderId: String
        get() = sp.getString("activeProvider", Provider.GOOGLE_ID)!!
        set(v) = sp.edit().putString("activeProvider", v).apply()

    /** يعيد المزوّد الفعّال، أو null إذا كان Google المجاني */
    fun activeProvider(): Provider? {
        if (activeProviderId == Provider.GOOGLE_ID) return null
        return providers.firstOrNull { it.id == activeProviderId }
    }

    fun providerName(): String =
        activeProvider()?.name ?: "Google (مجاني)"

    // ---------- الكاش ----------
    private fun loadCache() {
        try {
            val f = cacheFile ?: return
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                memCache[o.getString("k")] = o.getString("v")
            }
        } catch (_: Exception) {}
    }

    fun flushCache() {
        if (!cacheDirty) return
        cacheDirty = false
        try {
            val arr = JSONArray()
            val entries = memCache.entries.toList()          // Set ← List أولاً
            val from = if (entries.size > MAX_PERSIST) entries.size - MAX_PERSIST else 0
            for (i in from until entries.size) {
                val e = entries[i]
                arr.put(JSONObject().put("k", e.key).put("v", e.value))
            }
            cacheFile?.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun clearCache() {
        memCache.clear()
        cacheDirty = true
        flushCache()
    }

    fun cacheSize(): Int = memCache.size

    fun cacheGet(key: String): String? = memCache[key]

    fun cachePut(key: String, value: String) {
        memCache[key] = value
        cacheDirty = true
        if (memCache.size > MAX_MEM) {
            val iter = memCache.keys.iterator()          // ← مو "it" (كان متظلّل بـ repeat)
            var drop = memCache.size - MAX_PERSIST
            while (drop > 0 && iter.hasNext()) {
                iter.next()
                iter.remove()
                drop--
            }
        }
    }

    private const val MAX_MEM = 5000
    private const val MAX_PERSIST = 4000
}
