package com.minis.imt

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * طبقة الترجمات الموحّدة — "اعتراض الشبكة وامتلاك البيانات".
 *
 * الفكرة الجذرية: لا نقرأ من الشاشة (DOM) أبداً.
 * بدل ذلك نعترض طلب ملف الترجمات الأصلي (VTT / TTML / SRT / json3)
 * قبل ما يوصل للمشغّل، نحمل نسخة منه، نفكّها، نترجمها مقدماً،
 * ثم نعرضها على طبقة واحدة تفهم التوقيت الأصلي.
 *
 * نفس الكود يعمل على يوتيوب ونتفليكس وأي مشغّل آخر.
 */
object Subs {

    data class Cue(val start: Long, val end: Long, var text: String, var tr: String = "")

    /** أنماط روابط ملفات الترجمات — تُوسَّع بسطر واحد لكل موقع */
    private val URL_PATTERNS = listOf(
        Regex("timedtext", RegexOption.IGNORE_CASE),          // يوتيوب
        Regex("\\.vtt(\\?|$)", RegexOption.IGNORE_CASE),
        Regex("\\.srt(\\?|$)", RegexOption.IGNORE_CASE),
        Regex("\\.ttml(\\?|$)", RegexOption.IGNORE_CASE),     // نتفليكس
        Regex("\\.dfxp(\\?|$)", RegexOption.IGNORE_CASE),
        Regex("/subtitles?", RegexOption.IGNORE_CASE),
        Regex("[/=]sub[/=]", RegexOption.IGNORE_CASE),
        Regex("\\.ass(\\?|$)", RegexOption.IGNORE_CASE)
    )

    /** ما نحمّل نسخة إلا إذا الرابط يبدو ملف ترجمات */
    fun looksLikeSubtitleUrl(url: String): Boolean {
        if (url.startsWith("data:") || url.startsWith("blob:")) return false
        return URL_PATTERNS.any { it.containsMatchIn(url) }
    }

    /** هل المحتوى فعلاً ملف ترجمات؟ */
    private fun looksLikeSubtitleBody(body: String): Boolean {
        val h = body.trimStart().take(600)
        if (h.startsWith("WEBVTT")) return true
        if (h.startsWith("<?xml") && h.contains("<tt")) return true
        if (h.startsWith("<tt")) return true
        if (h.startsWith("{")) return h.contains("\"events\"") || h.contains("\"wireMagic\"")
        // SRT: رقم ثم سطر توقيت
        if (Regex("""^\d+\s*\r?\n\s*\d{1,2}:\d{2}:\d{2}""").containsMatchIn(h)) return true
        // VTT بلا ترويسة
        if (Regex("""\d{1,2}:\d{2}:\d{2}[.,]\d{3}\s*-->""").containsMatchIn(h)) return true
        return false
    }

    /** يحمّل نسخة من الملف (بكوكيز المتصفح ووكيلو) */
    fun fetch(url: String, cookies: String?, method: String = "GET"): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = if (method == "POST") "POST" else "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
            if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
            if (method == "POST") {
                conn.doOutput = true
                conn.outputStream.use { it.write(ByteArray(0)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                .use { it.readText() }
            if (text.isBlank()) return null
            return text
        } catch (e: Exception) {
            Log.d("IMT", "subs fetch failed: ${e.message}")
            return null
        } finally {
            try { conn?.disconnect() } catch (e: Exception) {}
        }
    }

    // ==================== فكّ الصيغ ====================

    private fun tcToMs(h: String, m: String, s: String, ms: String): Long =
        h.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 +
            ms.padEnd(3, '0').take(3).toLong()

    private val TIME_RE = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})"""
    )

    /** VTT / SRT */
    private fun parseCueFile(body: String): List<Cue> {
        val out = ArrayList<Cue>()
        val lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        var i = 0
        while (i < lines.size) {
            val m = TIME_RE.find(lines[i])
            if (m == null) { i++; continue }
            val start = tcToMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val end = tcToMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
            val sb = StringBuilder()
            i++
            while (i < lines.size && lines[i].isNotBlank() && TIME_RE.find(lines[i]) == null) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(lines[i].trim())
                i++
            }
            val text = cleanTags(sb.toString())
            if (text.isNotEmpty() && end > start) out.add(Cue(start, end, text))
        }
        return out
    }

    /** TTML / DFXP (نتفليكس وغيرو) */
    private fun parseTtml(body: String): List<Cue> {
        val out = ArrayList<Cue>()
        val pRe = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
        val bRe = Regex("""begin\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        val eRe = Regex("""end\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        val dRe = Regex("""dur\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        for (m in pRe.findAll(body)) {
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2]
            val start = ttmlTime(bRe.find(attrs)?.groupValues?.get(1)) ?: continue
            var end = ttmlTime(eRe.find(attrs)?.groupValues?.get(1))
            if (end == null) {
                val d = ttmlTime(dRe.find(attrs)?.groupValues?.get(1)) ?: 2000L
                end = start + d
            }
            val text = cleanTags(Regex("<[^>]+>").replace(inner, " "))
            if (text.isNotEmpty() && end > start) out.add(Cue(start, end, text))
        }
        return out
    }

    /** زمان TTML: إمّا ثوانٍ عشرية أو HH:MM:SS.mmm أو إطارات */
    private fun ttmlTime(v: String?): Long? {
        if (v.isNullOrBlank()) return null
        val t = v.trim()
        if (t.endsWith("ms")) return t.dropLast(2).toDoubleOrNull()?.toLong()
        if (t.endsWith("s")) return (t.dropLast(1).toDoubleOrNull()?.times(1000))?.toLong()
        val parts = t.split(":")
        return try {
            when (parts.size) {
                1 -> (parts[0].toDouble() * 1000).toLong()
                3 -> {
                    val sec = parts[2].toDouble()
                    parts[0].toLong() * 3600000 + parts[1].toLong() * 60000 + (sec * 1000).toLong()
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }

    /** json3 (يوتيوب) */
    private fun parseJson3(body: String): List<Cue> {
        val out = ArrayList<Cue>()
        val j = JSONObject(body)
        val ev = j.optJSONArray("events") ?: return out
        for (i in 0 until ev.length()) {
            val e = ev.optJSONObject(i) ?: continue
            val segs = e.optJSONArray("segs") ?: continue
            val sb = StringBuilder()
            for (k in 0 until segs.length()) sb.append(segs.optJSONObject(k)?.optString("utf8", "") ?: "")
            val text = cleanTags(sb.toString())
            if (text.isEmpty()) continue
            val start = e.optLong("tStartMs", 0L)
            var dur = e.optLong("dDurationMs", 1800L)
            if (dur <= 0) dur = 1800
            out.add(Cue(start, start + dur, text))
        }
        return out
    }

    private fun cleanTags(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ").trim()

    /** يفكّ أي صيغة مدعومة */
    fun parse(body: String): List<Cue> {
        val t = body.trimStart()
        return try {
            when {
                t.startsWith("{") -> parseJson3(body)
                t.contains("<tt") || t.contains("<p ") -> {
                    val r = parseTtml(body)
                    if (r.isNotEmpty()) r else parseCueFile(body)
                }
                else -> parseCueFile(body)
            }
        } catch (e: Exception) {
            Log.e("IMT", "subs parse failed", e)
            emptyList()
        }
    }

    /** يحوّل المقاطع لـ JSON للواجهة */
    fun toJson(cues: List<Cue>, translated: List<String>): String {
        val arr = JSONArray()
        cues.forEachIndexed { i, c ->
            val o = JSONObject()
            o.put("s", c.start)
            o.put("e", c.end)
            o.put("t", c.text)
            o.put("tr", translated.getOrElse(i) { "" })
            arr.put(o)
        }
        return arr.toString()
    }
}
