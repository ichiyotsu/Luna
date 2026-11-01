package com.m3browser

/** Actions that can be placed on the unified toolbar. The URL field and menu are always shown. */
enum class ToolbarAction(val label: String) {
    BACK("后退"),
    FORWARD("前进"),
    HOME("主页"),
    RELOAD("刷新"),
    TABS("标签页"),
    SHARE("分享"),
    TRANSLATE("翻译"),
    BOOKMARKS("书签"),
    ADD_BOOKMARK("添加书签"),
    HISTORY("历史"),
    INCOGNITO("无痕"),
    NEW_TAB("新标签页"),
    DESKTOP("桌面版"),
    FIND("页内查找"),
    SETTINGS("设置"),
    MENU("菜单");

    companion object {
        const val MAX_SLOTS = 5
        val DEFAULT = listOf(BACK, FORWARD, HOME, TABS)
        val CATALOG = entries.filter { it != MENU }
        fun fromId(id: String): ToolbarAction? = entries.firstOrNull { it.name == id }
    }
}

data class SeedOption(val name: String, val argb: Long)

val SEED_OPTIONS = listOf(
    SeedOption("紫", 0xFF6A3DE8),
    SeedOption("蓝", 0xFF1565C0),
    SeedOption("青", 0xFF00897B),
    SeedOption("绿", 0xFF2E7D32),
    SeedOption("橙", 0xFFEF6C00),
    SeedOption("红", 0xFFC62828),
    SeedOption("粉", 0xFFAD1457),
    SeedOption("棕", 0xFF6D4C41),
)

val TRANSLATE_LANGUAGES = listOf(
    "zh" to "简体中文",
    "en" to "英语",
    "ja" to "日语",
    "ko" to "韩语",
    "fr" to "法语",
    "de" to "德语",
    "es" to "西班牙语",
    "ru" to "俄语",
    "pt" to "葡萄牙语",
    "it" to "意大利语",
    "vi" to "越南语",
    "th" to "泰语",
    "ar" to "阿拉伯语",
    "hi" to "印地语",
)

fun languageLabel(code: String): String =
    TRANSLATE_LANGUAGES.firstOrNull { it.first == primaryLang(code) }?.second ?: code

fun primaryLang(tag: String): String =
    tag.trim().lowercase().substringBefore('-').substringBefore('_')

/** True when a page language tag should be treated as different from the translation target. */
fun languageDiffers(pageLang: String, target: String): Boolean {
    if (pageLang.isBlank()) return false
    val page = primaryLang(pageLang)
    val goal = primaryLang(target)
    if (page.isBlank() || page == "und") return false
    if (page == goal) return false
    if ((page == "zh" || page == "cmn") && goal == "zh") return false
    return true
}

fun sanitizeToolbar(actions: List<ToolbarAction>): List<ToolbarAction> {
    val cleaned = actions.filter { it != ToolbarAction.MENU }.distinct()
    return cleaned.take(ToolbarAction.MAX_SLOTS).ifEmpty { ToolbarAction.DEFAULT }
}

data class AppSettings(
    val engine: SearchEngine = SearchEngine.BING,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val monet: Boolean = true,
    val seed: Long = SEED_OPTIONS.first().argb,
    val amoled: Boolean = false,
    val barAtTop: Boolean = false,
    val shortcuts: Boolean = true,
    val javascript: Boolean = true,
    val desktopDefault: Boolean = false,
    val textZoom: Int = 100,
    val blockThirdPartyCookies: Boolean = false,
    val openInNewTab: Boolean = false,
    val translateLang: String = "zh",
    val neverTranslate: Set<String> = emptySet(),
    val autoUpdateCheck: Boolean = true,
    val ignoredUpdate: String = "",
    val flagSecure: Boolean = true,
    val blur: Boolean = true,
    val toolbar: List<ToolbarAction> = ToolbarAction.DEFAULT,
    val syncHistory: Boolean = true,
    val autoSync: Boolean = true,
    val updatedAt: Long = 0L,
)

enum class HistoryRange { HOUR, TODAY, ALL }
