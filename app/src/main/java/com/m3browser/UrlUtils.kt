package com.m3browser

import java.net.URLEncoder

enum class SearchEngine(val label: String, val template: String) {
    BING("必应 Bing", "https://www.bing.com/search?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BAIDU("百度", "https://www.baidu.com/s?wd=%s");
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

object UrlUtils {
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
    private val hostLike = Regex("^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([\\w-]+\\.)+[a-zA-Z]{2,})(:\\d+)?(/.*)?$")

    /** Turns user input into a loadable URL or a search URL. */
    fun toUrl(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        if (!text.contains(' ')) {
            if (text.startsWith("http://", true) || text.startsWith("https://", true) ||
                text.startsWith("about:", true) || text.startsWith("file://", true)
            ) return text
            if (hostLike.matches(text)) return "https://$text"
            if (schemeRegex.matches(text) && !text.contains('.') ) return text
        }
        return engine.template.format(URLEncoder.encode(text, "UTF-8"))
    }

    fun isWebScheme(url: String): Boolean {
        val s = url.substringBefore(':').lowercase()
        return s in setOf("http", "https", "about", "data", "javascript", "file", "blob")
    }

    fun displayHost(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.")
}
