package com.m3browser

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class PageEntry(val title: String, val url: String, val time: Long = System.currentTimeMillis())

private val Context.dataStore by preferencesDataStore("m3browser")

class Store(private val context: Context) {
    private val kEngine = stringPreferencesKey("engine")
    private val kTheme = stringPreferencesKey("theme")
    private val kBookmarks = stringPreferencesKey("bookmarks")
    private val kHistory = stringPreferencesKey("history")

    val engine: Flow<SearchEngine> = context.dataStore.data.map { p ->
        p[kEngine]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() } ?: SearchEngine.BING
    }
    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    val bookmarks: Flow<List<PageEntry>> = context.dataStore.data.map { decode(it[kBookmarks]) }
    val history: Flow<List<PageEntry>> = context.dataStore.data.map { decode(it[kHistory]) }

    suspend fun setEngine(e: SearchEngine) = context.dataStore.edit { it[kEngine] = e.name }
    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[kTheme] = t.name }

    suspend fun toggleBookmark(entry: PageEntry) = context.dataStore.edit { p ->
        val list = decode(p[kBookmarks]).toMutableList()
        if (list.removeAll { it.url == entry.url }.not()) list.add(0, entry)
        p[kBookmarks] = encode(list)
    }
    suspend fun removeBookmark(url: String) = context.dataStore.edit { p ->
        p[kBookmarks] = encode(decode(p[kBookmarks]).filter { it.url != url })
    }
    suspend fun addHistory(entry: PageEntry) = context.dataStore.edit { p ->
        val list = decode(p[kHistory]).filter { it.url != entry.url }.toMutableList()
        list.add(0, entry)
        p[kHistory] = encode(list.take(500))
    }
    suspend fun clearHistory() = context.dataStore.edit { it.remove(kHistory) }

    private fun encode(list: List<PageEntry>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("t", it.title).put("u", it.url).put("d", it.time)) }
    }.toString()

    private fun decode(s: String?): List<PageEntry> {
        if (s.isNullOrEmpty()) return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                PageEntry(o.optString("t"), o.optString("u"), o.optLong("d"))
            }
        }.getOrDefault(emptyList())
    }
}
