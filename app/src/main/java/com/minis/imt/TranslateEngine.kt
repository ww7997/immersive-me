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
 *  - وضع Google المجاني: endpoint عام بلا مفتاح ولا حساب.
 *  - وضع الذكاء الاصطناعي: أي واجهة متوافقة مع OpenAI بمفتاح المستخدم.
 *
 * الترجمة بالدفعات: نربط الفقرات بفاصل نادر، ونطلب الترجمة كلها بنداء واحد،
 * ثم نفصل. إذا اختلف عدد الأجزاء نرجع لخطة بديلة فقرة-فقرة.
 */
object TranslateEngine {

    private const val SEP = "\n@@\n"
    private val SEP_RE = Regex("""\s*@@\s*""")
    private const val MAX_BATCH_CHARS = 1600

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

        val results = try {
            if (provider == null) googleBatch(pendingTxt, target)
            else aiBatch(pendingTxt, target, provider)
        } catch (e: Exception) {
            // محاولة أخيرة: كل فقرة لحالها
            pendingTxt.map { t ->
                try {
                    if (provider == null) googleOne(t, target)
                    else aiOne(t, target, provider)
                } catch (_: Exception) { "" }
            }
        }

        results.forEachIndexed { i, r ->
            val idx = pendingIdx[i]
            out[idx] = r
            if (r.isNotBlank()) Prefs.cachePut(key(pendingTxt[i], target, tag), r)
        }
        return out.map { it ?: "" }
    }

    // ================= Google المجاني =================

    private fun googleOne(text: String, target: String): String {
        val body = "client=gtx&sl=auto&tl=" + enc(target) + "&dt=t&q=" + enc(text)
        val res = httpPost(
            "https://translate.googleapis.com/translate_a/single",
            body.toByteArray(Charsets.UTF_8),
            mapOf("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8"),
            25_000
        )
        return parseGoogle(res)
    }

    private fun parseGoogle(raw: String): String {
        val arr = JSONArray(raw)
        val segs = arr.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segs.length()) {
            val seg = segs.optJSONArray(i) ?: continue
            val s = seg.optString(0, "")
            sb.append(s)
        }
        return sb.toString().trim()
    }

    private fun googleBatch(texts: List<String>, target: String): List<String> {
        if (texts.size == 1) return listOf(googleOne(texts[0], target))
        val joined = texts.joinToString(SEP)
        if (joined.length > MAX_BATCH_CHARS) {
            val result = ArrayList<String>(texts.size)
            texts.chunked(6).forEach { chunk ->
                result.addAll(googleBatch(chunk, target))
            }
            return result
        }
        val translated = googleOne(joined, target)
        val parts = translated.split(SEP_RE).map { it.trim() }
        if (parts.size == texts.size) return parts
        return texts.map { googleOne(it, target) }   // خطة بديلة
    }

    // ================= الذكاء الاصطناعي =================

    private fun aiOne(text: String, target: String, p: Provider): String {
        val prompt = "Translate the following text into $target.\n" +
            "Output ONLY the translation — no notes, no quotes, no preamble.\n\n" +
            "-----BEGIN-----\n$text\n-----END-----"
        return aiCall(p, prompt).trim()
    }

    private fun aiBatch(texts: List<String>, target: String, p: Provider): List<String> {
        if (texts.size == 1) return listOf(aiOne(texts[0], target, p))
        val n = texts.size
        val prompt = StringBuilder()
        prompt.append("Translate each numbered block below into $target.\n")
        prompt.append("Output ONLY the translations, exactly $n lines, each line starting with the same ")
        prompt.append("number in square brackets followed by a space, e.g. `[1] ...`. ")
        prompt.append("Do not merge, split, reorder or skip any block. Output no other text.\n\n")
        texts.forEachIndexed { i, t ->
            prompt.append("[").append(i + 1).append("] ")
                .append(t.replace("\n", " ").trim()).append("\n")
        }
        val out = aiCall(p, prompt.toString()).trim()

        val res = arrayOfNulls<String>(n)
        val leftovers = ArrayList<String>()
        for (line in out.split("\n")) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val m = Regex("""^\[?(\d+)\]?[.:)]?\s+(.*)$""").find(l)
            if (m != null) {
                val idx = m.groupValues[1].toIntOrNull()
                if (idx != null && idx in 1..n) { res[idx - 1] = m.groupValues[2].trim(); continue }
            }
            leftovers.add(l)
        }
        if (res.any { it == null }) {
            // فشل التحليل → خطة بديلة فقرة-فقرة
            return texts.map { aiOne(it, target, p) }
        }
        return res.map { (it ?: "").replace(Regex("""^\[?\d+\]?[.:)]?\s+"""), "").trim() }
    }

    private fun aiCall(p: Provider, userPrompt: String): String {
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

        val raw = httpPost(url, body.toString().toByteArray(Charsets.UTF_8), headers, 120_000)
        val j = JSONObject(raw)
        if (j.has("error")) throw RuntimeException(j.getJSONObject("error").optString("message", "provider error"))
        var content: String = j.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content", "") ?: ""
        // بعض المزودين يرجّعون المصفوفة بشكل مختلفة (Gemini-compat / Responses API)
        if (content.isBlank()) {
            content = j.optJSONArray("choices")?.optJSONObject(0)?.optString("text", "") ?: ""
        }
        if (content.isBlank()) throw RuntimeException("رد فارغ من المزوّد")
        // إزالة أي سجل تفكير
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
            conn.connectTimeout = 15000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { os: OutputStream -> os.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw RuntimeException("HTTP $code: " + text.take(300))
            return text
        } finally {
            conn.disconnect()
        }
    }
}
