package com.minis.imt

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

/**
 * قارئ EPUB مبسّط.
 * EPUB هو مجرد ملف ZIP فيه صفحات XHTML + ملف OPF يحدّد ترتيب الفصول.
 * نفكّو، نجمع الفصول بصفحة HTML واحدة، ثم يعمل عليه نفس محرّك الترجمة.
 */
object EpubReader {

    data class Book(val title: String, val html: String, val chapters: Int)

    fun open(ctx: Context, uri: Uri): Book? {
        val files = HashMap<String, ByteArray>()
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zin ->
                    var e = zin.nextEntry
                    var guard = 0
                    while (e != null && guard < 3000) {
                        guard++
                        if (!e.isDirectory) {
                            val name = e.name.replace('\\', '/')
                            if (name.length < 300) {
                                try { files[name] = zin.readBytes() } catch (ex: Exception) {}
                            }
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
            }
        } catch (e: Exception) { return null }
        if (files.isEmpty()) return null

        fun text(name: String): String? = files[name]?.toString(Charsets.UTF_8)

        // ١) container.xml → مسار ملف OPF
        val container = text("META-INF/container.xml") ?: return null
        val opfPath = Regex("""full-path\s*=\s*"([^"]+)"""").find(container)?.groupValues?.get(1)
            ?: return null
        val opf = text(opfPath) ?: return null
        val base = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') else ""

        // ٢) العنوان
        val title = Regex("(?s)<dc:title[^>]*>(.*?)</dc:title>").find(opf)
            ?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "كتاب"

        // ٣) manifest: id → href
        val manifest = HashMap<String, String>()
        for (m in Regex("""<item\b[^>]*>""").findAll(opf)) {
            val tag = m.value
            val id = Regex("""\bid\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: continue
            val href = Regex("""\bhref\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: continue
            manifest[id] = href
        }

        // ٤) spine: ترتيب الفصول
        val order = Regex("""<itemref\b[^>]*idref\s*=\s*"([^"]+)"""")
            .findAll(opf).map { it.groupValues[1] }.toList()

        // ٥) نبني HTML موحّد
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html dir='auto'><head><meta charset='utf-8'>")
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
        sb.append("<style>body{font-size:16px;line-height:1.75;padding:14px 16px 60px;margin:0;}")
        sb.append("section{margin:0 0 28px;} img{max-width:100%;height:auto;}")
        sb.append("h1,h2,h3{line-height:1.35;}</style></head><body>")

        var chapters = 0
        for (id in order) {
            val href = manifest[id] ?: continue
            val clean = href.substringBefore('#')
            val candidates = listOf(
                if (base.isEmpty()) clean else "$base/$clean",
                clean, java.net.URLDecoder.decode(clean, "UTF-8")
            )
            val bytes = candidates.firstNotNullOfOrNull { files[it] } ?: continue
            var html = bytes.toString(Charsets.UTF_8)
            if (html.isBlank()) continue
            // نأخذ الـ body فقط
            val body = Regex("(?s)<body[^>]*>(.*?)</body>").find(html)?.groupValues?.get(1) ?: html
            // نشيل السكربتات والأنماط
            val safe = body
                .replace(Regex("(?s)<script[^>]*>.*?</script>"), "")
                .replace(Regex("(?s)<style[^>]*>.*?</style>"), "")
            sb.append("<section data-chapter='").append(chapters).append("'>")
            sb.append(safe).append("</section>")
            chapters++
        }
        sb.append("</body></html>")

        if (chapters == 0) return null
        return Book(title, sb.toString(), chapters)
    }
}
