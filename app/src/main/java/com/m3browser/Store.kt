package com.m3browser

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class PageEntry(
    val title: String,
    val url: String,
    val time: Long = System.currentTimeMillis(),
    val folder: String = "",
    val updatedAt: Long = time,
    val deleted: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("m3browser")

class Store(private val context: Context) {
    private val kSettings = stringPreferencesKey("settings")
    private val kBookmarks = stringPreferencesKey("bookmarks")
    private val kHistory = stringPreferencesKey("history")
    private val kEngine = stringPreferencesKey("engine")
    private val kTheme = stringPreferencesKey("theme")

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: Flow<Unit> = _changes

    val settings: Flow<AppSettings> = context.dataStore.data.map { decodeSettings(it[kSettings], it[kEngine], it[kTheme]) }
    val bookmarks: Flow<List<PageEntry>> = context.dataStore.data.map { decode(it[kBookmarks]).filter { e -> !e.deleted } }
    val history: Flow<List<PageEntry>> = context.dataStore.data.map { decode(it[kHistory]).filter { e -> !e.deleted } }
    val bookmarkRecords: Flow<List<PageEntry>> = context.dataStore.data.map { decode(it[kBookmarks]) }
    val historyRecords: Flow<List<PageEntry>> = context.dataStore.data.map { decode(it[kHistory]) }

    private fun touch() { _changes.tryEmit(Unit) }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun updateSettings(block: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = decodeSettings(p[kSettings], p[kEngine], p[kTheme])
            val next = block(cur).copy(updatedAt = System.currentTimeMillis())
            p[kSettings] = encodeSettings(next)
        }
        touch()
    }

    suspend fun toggleBookmark(entry: PageEntry) = context.dataStore.edit { p ->
        val list = decode(p[kBookmarks]).toMutableList()
        val idx = list.indexOfFirst { it.url == entry.url }
        val now = System.currentTimeMillis()
        if (idx >= 0 && !list[idx].deleted) {
            list[idx] = list[idx].copy(deleted = true, updatedAt = now)
        } else if (idx >= 0) {
            list[idx] = entry.copy(deleted = false, updatedAt = now, time = list[idx].time)
        } else {
            list.add(0, entry.copy(deleted = false, updatedAt = now))
        }
        p[kBookmarks] = encode(list)
    }.also { touch() }

    suspend fun removeBookmark(url: String) = tombstone(kBookmarks, url)

    suspend fun addHistory(entry: PageEntry) = context.dataStore.edit { p ->
        val now = entry.time
        val list = decode(p[kHistory]).filter { it.url != entry.url }.toMutableList()
        list.add(0, entry.copy(deleted = false, updatedAt = now, time = now))
        p[kHistory] = encode(list.take(500))
    }.also { touch() }

    suspend fun removeHistory(url: String) = tombstone(kHistory, url)

    suspend fun clearHistory(range: HistoryRange) {
        val now = System.currentTimeMillis()
        val startOfDay = startOfToday(now)
        context.dataStore.edit { p ->
            val list = decode(p[kHistory]).map { e ->
                val hit = when (range) {
                    HistoryRange.HOUR -> !e.deleted && now - e.time <= 60 * 60 * 1000
                    HistoryRange.TODAY -> !e.deleted && e.time >= startOfDay
                    HistoryRange.ALL -> !e.deleted
                }
                if (hit) e.copy(deleted = true, updatedAt = now) else e
            }
            p[kHistory] = encode(list)
        }
        touch()
    }

    /** Additive bookmark import. Returns how many new URLs were added (duplicates skipped). */
    suspend fun importBookmarks(items: List<PageEntry>): Pair<Int, Int> = mergeImport(kBookmarks, items)

    suspend fun importHistory(items: List<PageEntry>): Pair<Int, Int> = mergeImport(kHistory, items)

    suspend fun mergeFromRemote(remoteBookmarks: List<PageEntry>, remoteHistory: List<PageEntry>?, remoteSettings: AppSettings?) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { p ->
            val mergedBm = SyncMerge.pruneTombstones(
                SyncMerge.mergeEntries(decode(p[kBookmarks]), remoteBookmarks), now,
            )
            p[kBookmarks] = encode(mergedBm)
            if (remoteHistory != null) {
                val mergedH = SyncMerge.pruneTombstones(
                    SyncMerge.mergeEntries(decode(p[kHistory]), remoteHistory), now,
                )
                p[kHistory] = encode(mergedH.take(500))
            }
            if (remoteSettings != null) {
                val cur = decodeSettings(p[kSettings], p[kEngine], p[kTheme])
                p[kSettings] = encodeSettings(SyncMerge.mergeSettings(cur, remoteSettings))
            }
        }
    }

    suspend fun snapshotRecords(): Triple<AppSettings, List<PageEntry>, List<PageEntry>> =
        Triple(settings.first(), bookmarkRecords.first(), historyRecords.first())

    private suspend fun tombstone(key: androidx.datastore.preferences.core.Preferences.Key<String>, url: String) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { p ->
            val list = decode(p[key]).toMutableList()
            val idx = list.indexOfFirst { it.url == url }
            if (idx >= 0) list[idx] = list[idx].copy(deleted = true, updatedAt = now)
            p[key] = encode(list)
        }
        touch()
    }

    private suspend fun mergeImport(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        items: List<PageEntry>,
    ): Pair<Int, Int> {
        var added = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        context.dataStore.edit { p ->
            val list = decode(p[key]).toMutableList()
            items.forEach { incoming ->
                if (incoming.url.isBlank()) return@forEach
                val idx = list.indexOfFirst { it.url == incoming.url }
                if (idx >= 0 && !list[idx].deleted) {
                    skipped++
                } else if (idx >= 0) {
                    list[idx] = incoming.copy(deleted = false, updatedAt = now)
                    added++
                } else {
                    list.add(0, incoming.copy(deleted = false, updatedAt = now))
                    added++
                }
            }
            p[key] = encode(if (key == kHistory) list.take(500) else list)
        }
        touch()
        return added to skipped
    }

    private fun encode(list: List<PageEntry>): String = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject()
                .put("t", e.title)
                .put("u", e.url)
                .put("d", e.time)
                .put("f", e.folder)
                .put("m", e.updatedAt)
                .put("x", e.deleted))
        }
    }.toString()

    private fun decode(s: String?): List<PageEntry> {
        if (s.isNullOrEmpty()) return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val time = o.optLong("d")
                PageEntry(
                    title = o.optString("t"),
                    url = o.optString("u"),
                    time = time,
                    folder = o.optString("f"),
                    updatedAt = if (o.has("m")) o.optLong("m") else time,
                    deleted = o.optBoolean("x"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSettings(s: AppSettings): String = JSONObject()
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
        .toString()

    private fun decodeSettings(raw: String?, legacyEngine: String?, legacyTheme: String?): AppSettings {
        if (raw.isNullOrEmpty()) {
            val engine = legacyEngine?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() } ?: SearchEngine.BING
            val theme = legacyTheme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
            return AppSettings(engine = engine, theme = theme)
        }
        return runCatching {
            val o = JSONObject(raw)
            val toolbar = buildList {
                val arr = o.optJSONArray("toolbar")
                if (arr != null) for (i in 0 until arr.length()) {
                    ToolbarAction.fromId(arr.optString(i))?.let { add(it) }
                }
            }
            val never = buildSet {
                val arr = o.optJSONArray("never")
                if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
            }
            AppSettings(
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
        }.getOrDefault(AppSettings())
    }

    companion object {
        fun startOfToday(now: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = now
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
