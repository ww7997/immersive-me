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

    fun translate(
        texts: List<String>,
        target: String,
        onItem: ((Int, String) -> Unit)? = null
    ): List<String> {
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
        if (raws.isEmpty()) {
            // كل شي بالكاش — نبعتو فوراً للواجهة
            texts.forEachIndexed { i, _ -> out[i]?.let { onItem?.invoke(i, it) } }
            return out.map { it ?: "" }
        }

        if (failStreak >= 3 && provider != null) {
            lastError = lastError ?: "فشل متكرر من المزوّد — أُوقف مؤقتاً"
            return out.map { it ?: "" }
        }

        // نحوّل فهرس الدفعة لفهرس أصلي
        val cb: ((Int, String) -> Unit)? = onItem?.let { f -> { i, t -> f(idxs[i], t) } }

        val results: List<String> = try {
            if (provider == null) Google.freeBatch(raws, target, ::fail)
            else aiBatch(raws, target, provider, cb)
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

    private fun aiBatch(
        texts: List<String>,
        target: String,
        p: Provider,
        onItem: ((Int, String) -> Unit)? = null
    ): List<String> {
        val n = texts.size
        if (n == 1) {
            val r = aiOne(texts[0], target, p)
            if (r.isNotBlank()) onItem?.invoke(0, r)
            return listOf(r)
        }

        val prompt = StringBuilder()
        prompt.append("Translate each numbered block below into $target.\n")
        prompt.append("Output ONLY the translations, exactly $n lines, each line starting with the same ")
        prompt.append("number in square brackets followed by a space, e.g. `[1] ...`. ")
        prompt.append("Do not merge, split, reorder or skip any block. Output no other text.\n\n")
        texts.forEachIndexed { i, t ->
            prompt.append("[").append(i + 1).append("] ").append(t.replace("\n", " ").trim()).append("\n")
        }

        val res = arrayOfNulls<String>(n)

        /* ===== ١) بثّ مباشر — النتائج تطلع أول بأول ===== */
        try {
            aiCallStream(p, prompt.toString(), T_BATCH, n) { ix, txt ->
                if (ix in 1..n && res[ix - 1] == null && txt.isNotBlank()) {
                    res[ix - 1] = txt
                    onItem?.invoke(ix - 1, txt)
                }
            }
        } catch (e: Exception) {
            lastError = describe(e)
        }
        if (res.all { it != null }) return res.map { it ?: "" }

        /* ===== ٢) اللي ناقص → نداءات متوازية ===== */
        val missing = (0 until n).filter { res[it] == null }
        if (missing.isNotEmpty()) {
            val filled = aiParallel(missing.map { texts[it] }, target, p)
            missing.forEachIndexed { k, i ->
                res[i] = filled.getOrElse(k) { "" }
                if (!res[i].isNullOrBlank()) onItem?.invoke(i, res[i]!!)
            }
        }
        return res.map { it ?: "" }
    }

    /** تحليل الأسطر المكتملة من رد البثّ */
    private fun parseStreamed(s: String, n: Int, done: BooleanArray, onItem: (Int, String) -> Unit) {
        for (ln in s.split("\n")) {
            val l = ln.trim()
            if (l.isEmpty()) continue
            val m = Regex("""^\s*\[?\(?(\d+)\)?\]?[.:)\-—]?\s+(.*)$""").find(l) ?: continue
            val ix = m.groupValues[1].toIntOrNull() ?: continue
            if (ix in 1..n && !done[ix - 1]) {
                val txt = m.groupValues[2].trim()
                if (txt.isNotBlank()) { done[ix - 1] = true; onItem(ix, txt) }
            }
        }
    }

    /** نداء بثّ مباشر (SSE) — كل مقطع بيوصل لحالو */
    private fun aiCallStream(
        p: Provider,
        userPrompt: String,
        timeoutMs: Int,
        n: Int,
        onItem: (Int, String) -> Unit
    ) {
        val url = chatUrl(p)
        val sys = (p.systemPrompt.ifBlank { Provider.DEFAULT_PROMPT }) + dialectHint()
        val body = JSONObject().apply {
            put("model", p.model)
            put("temperature", 0.2)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
        }
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/json"
        headers["Accept"] = "text/event-stream"
        if (p.apiKey.isNotBlank()) headers["Authorization"] = "Bearer " + p.apiKey

        val conn = open(url, timeoutMs)
        conn.requestMethod = "POST"
        conn.doOutput = true
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = try {
                (conn.errorStream ?: conn.inputStream)?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
                } ?: ""
            } catch (e: Exception) { "" }
            conn.disconnect()
            throw RuntimeException("HTTP $code: " + err.take(200))
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        val acc = StringBuilder()
        val done = BooleanArray(n)
        var parsedUpTo = 0
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload == "[DONE]") break
                val delta = try {
                    JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content", "") ?: ""
                } catch (e: Exception) { "" }
                if (delta.isEmpty()) continue
                acc.append(delta)

                // نحلّل بس الأسطر المكتملة (اللي بعدها \n)
                val cut = acc.lastIndexOf('\n')
                if (cut > parsedUpTo) {
                    parseStreamed(acc.substring(parsedUpTo, cut), n, done, onItem)
                    parsedUpTo = cut
                }
            }
            // الباقي بلا سطر جديد
            if (parsedUpTo < acc.length) parseStreamed(acc.substring(parsedUpTo), n, done, onItem)
        } finally {
            try { reader.close() } catch (e: Exception) {}
            conn.disconnect()
        }
    }

    private fun chatUrl(p: Provider): String {
        val base = p.baseUrl.trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
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
            "sy" -> " Translate into natural everyday Syrian (Levantine) Arabic dialect — the way people speak in Damascus. Colloquial, not formal MSA."
            "lb" -> " Translate into natural everyday Lebanese Arabic dialect (colloquial)."
            "eg" -> " Translate into natural everyday Egyptian Arabic dialect (colloquial)."
            "gulf" -> " Translate into natural everyday Gulf (Khaleeji) Arabic dialect."
            "iq" -> " Translate into natural everyday Iraqi Arabic dialect."
            "ma" -> " Translate into natural everyday Moroccan Darija."
            else -> " Use clear Modern Standard Arabic (فصحى)."
        }
    }

    /* ==================== ترجمات يوتيوب ==================== */

    /**
     * يجلب ملف ترجمات يوتيوب ويحوّلو لمصفوفة مقاطع مزامَنة.
     * الاستدعاء من Kotlin → بلا مشاكل CORS أو حجب.
     */
    fun fetchCaptions(baseUrl: String): String {
        if (!baseUrl.startsWith("http")) throw RuntimeException("رابط الترجمات غير صالح")
        val url = if (baseUrl.contains("fmt=")) baseUrl else "$baseUrl&fmt=json3"
        val raw = httpGet(url, 25_000)
        if (raw.isBlank()) throw RuntimeException("يوتيوب ما رجّع ترجمات (يمكن الفيديو ما عندو)")

        // صيغة json3
        if (raw.trimStart().startsWith("{")) {
            val j = JSONObject(raw)
            val ev = j.optJSONArray("events") ?: JSONArray()
            val out = JSONArray()
            for (i in 0 until ev.length()) {
                val e = ev.optJSONObject(i) ?: continue
                val segs = e.optJSONArray("segs") ?: continue
                val sb = StringBuilder()
                for (k in 0 until segs.length()) sb.append(segs.optJSONObject(k)?.optString("utf8", "") ?: "")
                val text = sb.toString().replace("\n", " ").trim()
                if (text.isEmpty() || text == " ") continue
                val start = e.optLong("tStartMs", 0L)
                var dur = e.optLong("dDurationMs", 1800L)
                if (dur <= 0) dur = 1800
                out.put(JSONObject().put("s", start).put("e", start + dur).put("t", text))
            }
            if (out.length() == 0) throw RuntimeException("الترجمات فاضية — يمكن الفيديو ما عندو ترجمات")
            return out.toString()
        }

        // صيغة XML (خطة بديلة)
        val out = JSONArray()
        for (m in Regex("""<text start="([\d.]+)" dur="([\d.]+)"[^>]*>([\s\S]*?)</text>""").findAll(raw)) {
            val s = (m.groupValues[1].toDoubleOrNull() ?: 0.0) * 1000
            val d = (m.groupValues[2].toDoubleOrNull() ?: 1.8) * 1000
            val t = m.groupValues[3]
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace(Regex("<[^>]+>"), "").trim()
            if (t.isEmpty()) continue
            out.put(JSONObject().put("s", s.toLong()).put("e", (s + d).toLong()).put("t", t))
        }
        if (out.length() == 0) throw RuntimeException("ما قدرنا نقرأ ملف الترجمات")
        return out.toString()
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
