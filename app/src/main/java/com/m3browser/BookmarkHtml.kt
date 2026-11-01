package com.m3browser

object BookmarkHtml {
    fun parse(html: String): List<PageEntry> {
        val text = html.replace("\r\n", "\n").replace('\r', '\n')
        val lower = text.lowercase()
        val folders = ArrayDeque<String>()
        val byUrl = LinkedHashMap<String, PageEntry>()
        var i = 0
        while (i < text.length) {
            when {
                lower.startsWith("</dl", i) -> {
                    if (folders.isNotEmpty()) folders.removeLast()
                    i += 4
                }
                lower.startsWith("<h3", i) -> {
                    val close = lower.indexOf("</h3>", i)
                    if (close < 0) break
                    val gt = text.indexOf('>', i)
                    if (gt < 0 || gt > close) break
                    val name = decode(text.substring(gt + 1, close)).trim()
                    if (name.isNotEmpty()) folders.addLast(name)
                    i = close + 5
                }
                lower.startsWith("<a", i) && (i + 2 >= text.length || text[i + 2].isWhitespace() || text[i + 2] == '>') -> {
                    val close = lower.indexOf("</a>", i)
                    if (close < 0) break
                    val gt = text.indexOf('>', i)
                    if (gt < 0 || gt > close) {
                        i = close + 4
                        continue
                    }
                    val href = hrefOf(text.substring(i, gt))
                    if (href != null) {
                        val title = decode(text.substring(gt + 1, close)).trim().ifBlank { href }
                        byUrl[href] = PageEntry(
                            title = title,
                            url = href,
                            folder = folders.joinToString("/"),
                        )
                    }
                    i = close + 4
                }
                else -> i++
            }
        }
        return byUrl.values.toList()
    }

    fun export(items: List<PageEntry>): String {
        val live = items.filter { !it.deleted && it.url.isNotBlank() }
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        fun links(list: List<PageEntry>) {
            list.forEach { e ->
                sb.append("    <DT><A HREF=\"").append(escape(e.url)).append("\">")
                    .append(escape(e.title.ifBlank { e.url })).append("</A>\n")
            }
        }
        val groups = live.groupBy { it.folder }
        links(groups[""].orEmpty())
        groups.filterKeys { it.isNotEmpty() }.toSortedMap().forEach { (folder, list) ->
            sb.append("    <DT><H3>").append(escape(folder)).append("</H3>\n    <DL><p>\n")
            links(list)
            sb.append("    </DL><p>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun hrefOf(tag: String): String? {
        val match = Regex("(?i)href\\s*=\\s*([\"'])(.*?)\\1").find(tag) ?: return null
        val href = decode(match.groupValues[2]).trim()
        if (href.isBlank()) return null
        if (href.startsWith("http://", true) || href.startsWith("https://", true)) return href
        return null
    }

    fun decode(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
