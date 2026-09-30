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
 *  - Google المجاني: endpoint عام بلا مفتاح.
 *  - أي واجهة متوافقة مع OpenAI بمفتاح المستخدم.
 *
 * الدفعات: نربط الفقرات بفاصل نادر، ونطلب الترجمة كلها بنداء واحد.
 * المهل قصيرة عمداً حتى لا تعلق الواجهة، وكل خطأ يُمرَّر للشاشة.
 */
object TranslateEngine {

    private const val SEP = "\n@@\n"
    private val SEP_RE = Regex("""\s*@@\s*""")
    private const val MAX_BATCH_CHARS = 1600

    private const val T_BATCH = 45_000
    private const val T_SINGLE = 25_000
    private const val T_GOOGLE = 20_000

    /** آخر خطأ صار — يظهر بالواجهة */
    @Volatile
    var lastError: String? = null
        private set

    /** عدد الأخطاء المتتالية — إذا زاد عن ٣ نتوقف بدل ما نعلّق الجهاز */
    @Volatile
    private var failStreak = 0

    fun clearError() { lastError = null; failStreak = 0 }

    private fun fail(msg: String): String {
        lastError = msg
        failStreak++
        return ""
    }

    private fun key(text: String, target: String, providerTag: String): String {
        var h = 5381L
        for (c in text) h = ((h shl 5) + h + c.code) and 0xFFFFFFFFL
        return "$target|$providerTag|${h.toString(36)}|${text.length}"
    }

    fun translate(texts: List<String>, target: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val provider = Prefs.effectiveProvider()   // ناقص مفتاح؟ → Google المجاني
        val tag = provider?.id ?: "google"

        val out = arrayOfNulls<String>(texts.size)
        val pendingIdx = ArrayList<Int>()
        val pendingTxt = ArrayList<String>()

        texts.forEachIndexed { i, t ->
            val cached = Prefs.cacheGet(key(t, target, tag))
            if (cached != null) out[i] = cached
            else { pendingIdx.add(i); pendingTxt.add(t) }
        }

        if (pendingTxt.isEmpty()) return out.map { it ?: "" }

        // توقّف مبكر: إذا فشل المزوّد ٣ مرات متتالية، ما نضل نعلّق الواجهة
        if (failStreak >= 3 && provider != null) {
            lastError = lastError ?: "فشل متكرر من المزوّد"
            return out.map { it ?: "" }
        }

        val results: List<String> = try {
            if (provider == null) googleBatch(pendingTxt, target)
            else aiBatch(pendingTxt, target, provider)
        } catch (e: Exception) {
            val msg = describe(e)
            lastError = msg
            failStreak++
            List(pendingTxt.size) { "" }
        }

        if (results.size == pendingTxt.size) {
            var anyOk = false
            results.forEachIndexed { i, r ->
                val idx = pendingIdx[i]
                out[idx] = r
                if (r.isNotBlank()) {
                    anyOk = true
                    Prefs.cachePut(key(pendingTxt[i], target, tag), r)
                }
            }
            if (anyOk) { failStreak = 0; lastError = null }
        }

        return out.map { it ?: "" }
    }

    private fun describe(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("HTTP 401") || m.contains("401") -> "المفتاح مرفوض (401) — تأكد من الـ API Key"
            m.contains("HTTP 402") -> "رصيد المزوّد خلص (402)"
            m.contains("HTTP 403") -> "ممنوع (403) — المفتاح بلا صلاحية أو الموديل غير متاح"
            m.contains("HTTP 404") -> "الموديل أو العنوان غير موجود (404)"
            m.contains("HTTP 429") -> "تجاوزت حدّ الطلبات (429) — استنّى شوي"
            m.contains("HTTP 5") -> "خطأ من سيرفر المزوّد"
            m.contains("timeout") || m.contains("Timeout") -> "انتهت المدة — المزوّد بطيء"
            else -> m.take(140)
        }
    }

    // ================= Google المجاني =================

    private fun googleOne(text: String, target: String): String {
        val body = "client=gtx&sl=auto&tl=" + enc(target) + "&dt=t&q=" + enc(text)
        val res = httpPost(
            "https://translate.googleapis.com/translate_a/single",
            body.toByteArray(Charsets.UTF_8),
            mapOf("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8"),
            T_GOOGLE
        )
        return parseGoogle(res)
    }

    private fun parseGoogle(raw: String): String {
        val arr = JSONArray(raw)
        val segs = arr.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segs.length()) {
            val seg = segs.optJSONArray(i) ?: continue
            sb.append(seg.optString(0, ""))
        }
        return sb.toString().trim()
    }

    private fun googleBatch(texts: List<String>, target: String): List<String> {
        if (texts.size == 1) return listOf(googleOne(texts[0], target))
        val joined = texts.joinToString(SEP)
        if (joined.length > MAX_BATCH_CHARS) {
            val result = ArrayList<String>(texts.size)
            texts.chunked(6).forEach { chunk -> result.addAll(googleBatch(chunk, target)) }
            return result
        }
        val translated = googleOne(joined, target)
        val parts = translated.split(SEP_RE).map { it.trim() }
        if (parts.size == texts.size) return parts
        return texts.map { t -> try { googleOne(t, target) } catch (e: Exception) { fail(describe(e)) } }
    }

    // ================= الذكاء الاصطناعي =================

    private fun aiOne(text: String, target: String, p: Provider): String {
        val prompt = "Translate the following text into $target.\n" +
            "Output ONLY the translation — no notes, no quotes, no preamble.\n\n" +
            "-----BEGIN-----\n$text\n-----END-----"
        return aiCall(p, prompt, T_SINGLE).trim()
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
            prompt.append("[").append(i + 1).append("] ")
                .append(t.replace("\n", " ").trim()).append("\n")
        }

        val out: String = try {
            aiCall(p, prompt.toString(), T_BATCH).trim()
        } catch (e: Exception) {
            // الدفعة فشلت → نرجع لمقطع-مقطع (بمهلة قصيرة)
            lastError = describe(e)
            return texts.map { t ->
                try { aiOne(t, target, p) } catch (e2: Exception) { fail(describe(e2)) }
            }
        }

        val res = arrayOfNulls<String>(n)
        for (line in out.split("\n")) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val m = Regex("""^\[?(\d+)\]?[.:)]?\s+(.*)$""").find(l)
            if (m != null) {
                val idx = m.groupValues[1].toIntOrNull()
                if (idx != null && idx in 1..n) { res[idx - 1] = m.groupValues[2].trim(); continue }
            }
        }
        if (res.any { it == null }) {
            // تعذّر التحليل → مقطع-مقطع
            return texts.map { t ->
                try { aiOne(t, target, p) } catch (e: Exception) { fail(describe(e)) }
            }
        }
        return res.map { (it ?: "").replace(Regex("""^\[?\d+\]?[.:)]?\s+"""), "").trim() }
    }

    private fun aiCall(p: Provider, userPrompt: String, timeoutMs: Int): String {
        val base = p.baseUrl.trimEnd('/')
        val url = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val sys = p.systemPrompt.ifBlank { Provider.DEFAULT_PROMPT }
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

        if (j.has("error")) {
            val err = j.optJSONObject("error")
            val msg = err?.optString("message") ?: j.opt("error").toString()
            throw RuntimeException("HTTP ${err?.optInt("code") ?: "?"}: $msg")
        }

        val msg = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        var content: String = msg?.optString("content", "") ?: ""
        if (content.isBlank()) content = j.optJSONArray("choices")?.optJSONObject(0)?.optString("text", "") ?: ""
        if (content.isBlank()) {
            val fr = msg?.optString("reasoning", "") ?: ""
            if (fr.isNotBlank()) content = fr
        }
        if (content.isBlank()) throw RuntimeException("رد فارغ من المزوّد — جرّب موديل تاني")

        content = content.replace(Regex("""(?s)Thinking\.\.\..*?\.\.\.done thinking\."""), "")
        content = content.replace(Regex("""(?s)<(think|thinking)>.*?</\1>"""), "")
        return content.trim()
    }

    // ================= HTTP =================

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpPost(
        urlStr: String,
        body: ByteArray,
        headers: Map<String, String>,
        timeoutMs: Int
    ): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { os: OutputStream -> os.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw RuntimeException("HTTP $code: " + text.take(220))
            return text
        } finally {
            conn.disconnect()
        }
    }
}
