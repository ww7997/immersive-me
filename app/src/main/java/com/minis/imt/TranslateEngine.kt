package com.minis.imt

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * محرّك الترجمة.
 *  - Google المجاني: منفذ يقبل عدة فقرات بنداء واحد + عنوان متصفّح + إعادة محاولة.
 *  - أي واجهة متوافقة مع OpenAI بمفتاح المستخدم.
 *
 * كل خطأ يُمرَّر للشاشة بالعربي — لا فشل صامت.
 */
object TranslateEngine {

    private const val T_BATCH = 45_000
    private const val T_SINGLE = 25_000
    private const val T_GOOGLE = 20_000

    /** عنوان متصفّح حقيقي — بدونه جوجل بتحجب الطلبات بـ 429 */
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var failStreak = 0

    fun clearError() { lastError = null; failStreak = 0 }

    private fun fail(msg: String): String {
        lastError = msg
        failStreak++
        return ""
    }

    private fun ckey(text: String, target: String, tag: String): String {
        var h = 5381L
        for (c in text) h = ((h shl 5) + h + c.code) and 0xFFFFFFFFL
        return "$target|$tag|${h.toString(36)}|${text.length}"
    }

    fun translate(texts: List<String>, target: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val provider = Prefs.chosenProvider()   // حسب الوضع المختار
        val tag = provider?.id ?: "google"

        val out = arrayOfNulls<String>(texts.size)
        val idxs = ArrayList<Int>()
        val raws = ArrayList<String>()

        texts.forEachIndexed { i, t ->
            val c = Prefs.cacheGet(ckey(t, target, tag))
            if (c != null) out[i] = c else { idxs.add(i); raws.add(t) }
        }
        if (raws.isEmpty()) return out.map { it ?: "" }

        if (failStreak >= 3 && provider != null) {
            lastError = lastError ?: "فشل متكرر من المزوّد — أُوقف مؤقتاً"
            return out.map { it ?: "" }
        }

        val results: List<String> = try {
            if (provider == null) Google.freeBatch(raws, target, ::fail)
            else aiBatch(raws, target, provider)
        } catch (e: Exception) {
            lastError = describe(e); failStreak++
            List(raws.size) { "" }
        }

        if (results.size == raws.size) {
            var ok = false
            results.forEachIndexed { i, r ->
                out[idxs[i]] = r
                if (r.isNotBlank()) { ok = true; Prefs.cachePut(ckey(raws[i], target, tag), r) }
            }
            if (ok) { failStreak = 0; lastError = null }
        }
        return out.map { it ?: "" }
    }

    internal fun describe(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("401") -> "المفتاح مرفوض (401) — تأكد من الـ API Key"
            m.contains("402") -> "رصيد المزوّد خلص (402)"
            m.contains("403") -> "ممنوع (403) — المفتاح بلا صلاحية أو الموديل غير متاح"
            m.contains("404") -> "الموديل أو العنوان غير موجود (404)"
            m.contains("429") -> "المزوّد بيرفض الطلبات (429) — جرّب بعد شوي"
            m.contains("HTTP 5") -> "خطأ من سيرفر المزوّد"
            m.contains("timeout", true) -> "انتهت المدة — المزوّد بطيء"
            else -> m.take(150)
        }
    }

    /* ==================== Google المجاني ==================== */
    object Google {

        /** عدة فقرات بنداء واحد — بلا فواصل، الجواب مصفوفة مباشرة */
        fun freeBatch(texts: List<String>, target: String, onFail: (String) -> String): List<String> {
            if (texts.size == 1) return listOf(one(texts[0], target, onFail))

            // نداء واحد بعدة q
            try {
                return multi(texts, target)
            } catch (e: Exception) {
                // منفذ متعدد فشل → نرجع للمنفذ القديم بنفس الطريقة
                try {
                    return multiOld(texts, target)
                } catch (e2: Exception) {
                    // خطة أخيرة: مقطع-مقطع
                    return texts.map { t ->
                        try { one(t, target, onFail) } catch (e3: Exception) { onFail(describe(e3)) }
                    }
                }
            }
        }

        private fun multi(texts: List<String>, target: String): List<String> {
            val sb = StringBuilder(URL_ALT).append("&sl=auto&tl=").append(enc(target))
            texts.forEach { sb.append("&q=").append(enc(it)) }
            val raw = httpGet(sb.toString(), T_GOOGLE)
            val arr = JSONArray(raw)

            val res = ArrayList<String>(texts.size)
            if (arr.length() > 0 && arr.opt(0) is JSONArray) {
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONArray(i) ?: continue
                    res.add(row.optString(0, ""))
                }
            } else if (arr.length() > 0 && arr.opt(0) is String) {
                res.add(arr.optString(0, ""))
            }
            if (res.size != texts.size) throw RuntimeException("رد غير متوقّع من Google (${res.size}/${texts.size})")
            return res
        }

        private fun one(text: String, target: String, onFail: (String) -> String): String {
            try {
                val sb = StringBuilder(URL_ALT).append("&sl=auto&tl=").append(enc(target))
                    .append("&q=").append(enc(text))
                val raw = httpGet(sb.toString(), T_GOOGLE)
                val arr = JSONArray(raw)
                if (arr.length() > 0 && arr.opt(0) is JSONArray) return arr.getJSONArray(0).optString(0, "").trim()
                if (arr.length() > 0) return arr.optString(0, "").trim()
                throw RuntimeException("رد فارغ من Google")
            } catch (e: Exception) {
                return onFail(describe(e))
            }
        }

        private const val URL_ALT = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex"

        /** منفذ احتياطي: translate_a/single مع فصل بـ @@ */
        private fun multiOld(texts: List<String>, target: String): List<String> {
            val joined = texts.joinToString("\n@@\n")
            val body = "client=gtx&sl=auto&tl=" + enc(target) + "&dt=t&q=" + enc(joined)
            val raw = httpPost(
                "https://translate.googleapis.com/translate_a/single",
                body.toByteArray(Charsets.UTF_8),
                mapOf("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8"),
                T_GOOGLE
            )
            val arr = JSONArray(raw); val segs = arr.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segs.length()) sb.append(segs.optJSONArray(i)?.optString(0, "") ?: "")
            val parts = sb.toString().trim().split(Regex("""\s*@@\s*""")).map { it.trim() }
            if (parts.size != texts.size) throw RuntimeException("رد غير متوقّع (${parts.size}/${texts.size})")
            return parts
        }
    }

    /* ==================== الذكاء الاصطناعي ==================== */

    private fun aiOne(text: String, target: String, p: Provider): String {
        val prompt = "Translate the following text into $target.\n" +
            "Output ONLY the translation — no notes, no quotes, no preamble.\n\n" +
            "-----BEGIN-----\n$text\n-----END-----"
        return aiCall(p, prompt, T_SINGLE).trim()
    }

    /* ---- ترجمة متوازية (تُستعمل كخطة بديلة بدل التسلسل البطيء) ---- */
    private val parPool = java.util.concurrent.Executors.newFixedThreadPool(4)

    private fun aiParallel(texts: List<String>, target: String, p: Provider): List<String> {
        val jobs = texts.map { t ->
            parPool.submit(java.util.concurrent.Callable {
                try { aiOne(t, target, p) } catch (e: Exception) { fail(describe(e)) }
            })
        }
        return jobs.map { f ->
            try { f.get() } catch (e: Exception) { "" }
        }
    }

    private fun aiBatch(texts: List<String>, target: String, p: Provider): List<String> {
        val n = texts.size
        if (n == 1) return listOf(aiOne(texts[0], target, p))

        val prompt = StringBuilder()
        prompt.append("Translate each numbered block below into $target.\n")
        prompt.append("Output ONLY the translations, exactly $n lines, each line starting with the same ")
        prompt.append("number in square brackets followed by a space, e.g. `[1] ...`. ")
        prompt.append("Do not merge, split, reorder or skip any block. Output no other text.\n\n")
        texts.forEachIndexed { i, t ->
            prompt.append("[").append(i + 1).append("] ").append(t.replace("\n", " ").trim()).append("\n")
        }

        val out: String = try {
            aiCall(p, prompt.toString(), T_BATCH).trim()
        } catch (e: Exception) {
            lastError = describe(e)
            return aiParallel(texts, target, p)      // ← متوازي مو تسلسلي
        }

        // نظّف أسوار الماركداون
        var body = out
        if (body.startsWith("```")) {
            body = body.replace(Regex("""(?s)^```[a-zA-Z]*\s*"""), "")
                       .replace(Regex("""(?s)\s*```\s*$"""), "").trim()
        }

        val res = arrayOfNulls<String>(n)
        val plain = ArrayList<String>()
        for (line in body.split("\n")) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val m = Regex("""^\[?\(?(\d+)\)?\]?[.:)\-—]?\s+(.*)$""").find(l)
            if (m != null) {
                val ix = m.groupValues[1].toIntOrNull()
                if (ix != null && ix in 1..n && res[ix - 1] == null) {
                    res[ix - 1] = m.groupValues[2].trim(); continue
                }
            }
            plain.add(l)
        }

        // كل الأسطر مرقّمة ومطابقة
        if (res.all { it != null }) {
            return res.map { (it ?: "").replace(Regex("""^\[?\d+\]?[.:)]?\s+"""), "").trim() }
        }
        // ما في ترقيم بس العدد مطابق → خدهم بالترتيب
        if (plain.size == n && res.none { it != null }) return plain
        // عدد الأسطر = عدد المقاطع (مع بعض الترقيم) → أكمل الناقص بالترتيب
        if (plain.size + res.count { it != null } == n) {
            var pi = 0
            for (i in 0 until n) if (res[i] == null) res[i] = plain[pi++]
            return res.map { (it ?: "").trim() }
        }
        // فشل التحليل → متوازي
        return aiParallel(texts, target, p)
    }

    private fun aiCall(p: Provider, userPrompt: String, timeoutMs: Int): String {
        val base = p.baseUrl.trimEnd('/')
        val url = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val sys = (p.systemPrompt.ifBlank { Provider.DEFAULT_PROMPT }) + dialectHint()
        val body = JSONObject().apply {
            put("model", p.model)
            put("temperature", 0.2)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
        }
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/json"
        if (p.apiKey.isNotBlank()) headers["Authorization"] = "Bearer " + p.apiKey

        val raw = httpPost(url, body.toString().toByteArray(Charsets.UTF_8), headers, timeoutMs)
        val j = JSONObject(raw)

        if (j.has("error") && !j.isNull("error")) {
            val err = j.optJSONObject("error")
            val msg = err?.optString("message") ?: j.opt("error").toString()
            throw RuntimeException("HTTP ${err?.optInt("code") ?: "?"}: $msg")
        }

        val msg = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        var content: String = msg?.optString("content", "") ?: ""
        if (content.isBlank()) content = j.optJSONArray("choices")?.optJSONObject(0)?.optString("text", "") ?: ""
        if (content.isBlank()) content = msg?.optString("reasoning", "") ?: ""
        if (content.isBlank()) throw RuntimeException("رد فارغ من المزوّد — جرّب موديل تاني")

        content = content.replace(Regex("""(?s)Thinking\.\.\..*?\.\.\.done thinking\."""), "")
        content = content.replace(Regex("""(?s)<(think|thinking)>.*?</\1>"""), "")
        return content.trim()
    }

    /* ==================== اللهجة ==================== */

    /** تعليمة اللهجة — تُضاف دائماً لبرومبت النظام عند الترجمة للعربية */
    fun dialectHint(): String {
        if (!Prefs.target.startsWith("ar")) return ""
        return when (Prefs.dialect) {
            "sy" -> " IMPORTANT: Write the translation in natural, everyday **Syrian (Levantine) Arabic dialect** — " +
                    "exactly the way people actually speak in Damascus. Use colloquial words and idioms, " +
                    "not formal Modern Standard Arabic. Avoid stiff or classical phrasing. " +
                    "It must sound like a Syrian person talking, not like a news bulletin."
            "lb" -> " IMPORTANT: Write the translation in natural everyday Lebanese Arabic dialect — colloquial, not Modern Standard Arabic."
            "eg" -> " IMPORTANT: Write the translation in natural everyday Egyptian Arabic dialect — colloquial, not Modern Standard Arabic."
            "gulf" -> " IMPORTANT: Write the translation in natural everyday Gulf (Khaleeji) Arabic dialect — colloquial, not Modern Standard Arabic."
            "iq" -> " IMPORTANT: Write the translation in natural everyday Iraqi Arabic dialect — colloquial, not Modern Standard Arabic."
            "ma" -> " IMPORTANT: Write the translation in natural everyday Moroccan Darija — colloquial, not Modern Standard Arabic."
            else -> " Use clear Modern Standard Arabic (فصحى)."
        }
    }

    /* ==================== قائمة الموديلات ==================== */

    /**
     * يجلب الموديلات المتاحة من المزوّد.
     * هاي الدالة **بتتحقق من المفتاح بنفس الوقت** — إذا رجعت قائمة، يعني المفتاح شغّال.
     */
    fun listModels(p: Provider): List<String> {
        val base = p.baseUrl.trimEnd('/')
        if (base.isBlank()) throw RuntimeException("عبّي Base URL أول")
        val url = when {
            base.endsWith("/models") -> base
            base.endsWith("/v1") -> "$base/models"
            else -> "$base/v1/models"
        }
        val headers = HashMap<String, String>()
        headers["Accept"] = "application/json"
        if (p.apiKey.isNotBlank()) headers["Authorization"] = "Bearer " + p.apiKey

        val raw = withRetry {
            val conn = open(url, 30_000)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            try {
                val code = conn.responseCode
                val text = read(conn, code)
                if (code !in 200..299) throw RuntimeException("HTTP $code: " + text.take(200))
                text
            } finally { conn.disconnect() }
        }

        val j = JSONObject(raw)
        val arr = j.optJSONArray("data") ?: j.optJSONArray("models") ?: JSONArray()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var id = o.optString("id", "")
            if (id.isBlank()) id = o.optString("name", "")
            if (id.isNotBlank()) out.add(id)
        }
        if (out.isEmpty()) throw RuntimeException("المزوّد ما رجّع أي موديل — تأكد من العنوان")
        return out.distinct().sorted()
    }

    /* ==================== HTTP ==================== */

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** إعادة محاولة على 429 وأخطاء السيرفر */
    private inline fun withRetry(block: () -> String): String {
        val delays = longArrayOf(700L, 1800L, 3500L)
        var last: Exception? = null
        for (attempt in 0..delays.size) {
            try {
                return block()
            } catch (e: Exception) {
                last = e
                val m = e.message ?: ""
                val retriable = m.contains("429") || m.contains("HTTP 5")
                if (!retriable || attempt == delays.size) throw e
                try { Thread.sleep(delays[attempt]) } catch (ie: InterruptedException) {}
            }
        }
        throw last ?: RuntimeException("فشل")
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String = withRetry {
        open(urlStr, timeoutMs).use2 { conn ->
            val code = conn.responseCode
            val text = read(conn, code)
            if (code !in 200..299) throw RuntimeException("HTTP $code: " + text.take(220))
            text
        }
    }

    private fun httpPost(
        urlStr: String,
        body: ByteArray,
        headers: Map<String, String>,
        timeoutMs: Int
    ): String = withRetry {
        open(urlStr, timeoutMs).use2 { conn ->
            conn.requestMethod = "POST"
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { os: OutputStream -> os.write(body) }
            val code = conn.responseCode
            val text = read(conn, code)
            if (code !in 200..299) throw RuntimeException("HTTP $code: " + text.take(220))
            text
        }
    }

    private fun open(urlStr: String, timeoutMs: Int): HttpURLConnection {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", BROWSER_UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        return c
    }

    private fun read(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private inline fun <T> HttpURLConnection.use2(block: (HttpURLConnection) -> T): T {
        try { return block(this) } finally { disconnect() }
    }
}
