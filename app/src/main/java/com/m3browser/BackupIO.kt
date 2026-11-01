package com.m3browser

import org.json.JSONArray
import org.json.JSONObject

data class BackupBundle(
    val bookmarks: List<PageEntry>,
    val history: List<PageEntry>,
    val settings: AppSettings?,
)

object BackupIO {
    fun export(settings: AppSettings, bookmarks: List<PageEntry>, history: List<PageEntry>): String =
        JSONObject()
            .put("version", 1)
            .put("bookmarks", encode(bookmarks.filter { !it.deleted }))
            .put("history", encode(history.filter { !it.deleted }))
            .put("settings", encodeSettings(settings))
            .toString(2)

    fun parse(raw: String): BackupBundle? = runCatching {
        val o = JSONObject(raw)
        BackupBundle(
            bookmarks = decode(o.optJSONArray("bookmarks")),
            history = decode(o.optJSONArray("history")),
            settings = o.optJSONObject("settings")?.let { decodeSettings(it) },
        )
    }.getOrNull()

    private fun encode(items: List<PageEntry>): JSONArray = JSONArray().apply {
        items.forEach { e ->
            put(JSONObject()
                .put("title", e.title).put("url", e.url).put("folder", e.folder)
                .put("time", e.time).put("updatedAt", e.updatedAt))
        }
    }

    private fun decode(arr: JSONArray?): List<PageEntry> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val time = o.optLong("time", System.currentTimeMillis())
                add(PageEntry(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    time = time,
                    folder = o.optString("folder"),
                    updatedAt = if (o.has("updatedAt")) o.optLong("updatedAt") else time,
                ))
            }
        }
    }

    private fun encodeSettings(s: AppSettings): JSONObject = JSONObject()
        .put("engine", s.engine.name)
        .put("theme", s.theme.name)
        .put("monet", s.monet)
        .put("seed", s.seed)
        .put("amoled", s.amoled)
        .put("barAtTop", s.barAtTop)
        .put("shortcuts", s.shortcuts)
        .put("javascript", s.javascript)
        .put("desktop", s.desktopDefault)
        .put("textZoom", s.textZoom)
        .put("block3p", s.blockThirdPartyCookies)
        .put("newTab", s.openInNewTab)
        .put("trLang", s.translateLang)
        .put("never", JSONArray(s.neverTranslate.toList()))
        .put("autoUpd", s.autoUpdateCheck)
        .put("ignored", s.ignoredUpdate)
        .put("secure", s.flagSecure)
        .put("blur", s.blur)
        .put("toolbar", JSONArray(sanitizeToolbar(s.toolbar).map { it.name }))
        .put("syncHist", s.syncHistory)
        .put("autoSync", s.autoSync)
        .put("updatedAt", s.updatedAt)

    private fun decodeSettings(o: JSONObject): AppSettings {
        val toolbar = buildList {
            val arr = o.optJSONArray("toolbar")
            if (arr != null) for (i in 0 until arr.length()) ToolbarAction.fromId(arr.optString(i))?.let { add(it) }
        }
        val never = buildSet {
            val arr = o.optJSONArray("never")
            if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
        }
        return AppSettings(
            engine = runCatching { SearchEngine.valueOf(o.optString("engine")) }.getOrDefault(SearchEngine.BING),
            theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
            monet = o.optBoolean("monet", true),
            seed = o.optLong("seed", SEED_OPTIONS.first().argb),
            amoled = o.optBoolean("amoled"),
            barAtTop = o.optBoolean("barAtTop"),
            shortcuts = o.optBoolean("shortcuts", true),
            javascript = o.optBoolean("javascript", true),
            desktopDefault = o.optBoolean("desktop"),
            textZoom = o.optInt("textZoom", 100).coerceIn(50, 200),
            blockThirdPartyCookies = o.optBoolean("block3p"),
            openInNewTab = o.optBoolean("newTab"),
            translateLang = o.optString("trLang", "zh").ifBlank { "zh" },
            neverTranslate = never,
            autoUpdateCheck = o.optBoolean("autoUpd", true),
            ignoredUpdate = o.optString("ignored"),
            flagSecure = o.optBoolean("secure", true),
            blur = o.optBoolean("blur", true),
            toolbar = sanitizeToolbar(toolbar),
            syncHistory = o.optBoolean("syncHist", true),
            autoSync = o.optBoolean("autoSync", true),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}
