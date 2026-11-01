package com.m3browser

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class DeviceCode(val userCode: String, val verificationUri: String, val deviceCode: String, val interval: Int)

class GitHubSync(private val tokens: TokenStore, private val store: Store) {
    fun clientId(): String = BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim()
    fun configured(): Boolean = clientId().isNotBlank()

    fun requestDeviceCode(): DeviceCode {
        val body = post("https://github.com/login/device/code", JSONObject()
            .put("client_id", clientId())
            .put("scope", "gist")
            .toString(), null)
        val json = JSONObject(body)
        if (json.has("error")) error(json.optString("error_description", json.optString("error")))
        return DeviceCode(
            userCode = json.getString("user_code"),
            verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
            deviceCode = json.getString("device_code"),
            interval = json.optInt("interval", 5).coerceAtLeast(5),
        )
    }

    suspend fun pollToken(code: DeviceCode): String {
        var interval = code.interval
        val deadline = System.currentTimeMillis() + 15 * 60 * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val body = post("https://github.com/login/oauth/access_token", JSONObject()
                .put("client_id", clientId())
                .put("device_code", code.deviceCode)
                .put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .toString(), null)
            val json = JSONObject(body)
            if (json.has("access_token")) {
                val token = json.getString("access_token")
                tokens.token = token
                loadProfile(token)
                return token
            }
            when (json.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("授权已过期，请重试")
                "access_denied" -> error("已取消授权")
                else -> if (json.optString("error").isNotBlank()) error(json.optString("error_description", json.optString("error")))
            }
        }
        error("授权超时")
    }

    fun loadProfile(token: String? = tokens.token) {
        val auth = token ?: return
        val json = JSONObject(get("https://api.github.com/user", auth))
        tokens.username = json.optString("login")
        tokens.avatarUrl = json.optString("avatar_url")
    }

    suspend fun syncNow(): String {
        val token = tokens.token ?: return "尚未登录"
        val (settings, bookmarks, history) = store.snapshotRecords()
        val remote = loadGist(token)
        val remoteSettings = remote?.optJSONObject("settings")?.let { decodeSettingsJson(it) }
        val remoteBm = remote?.optJSONArray("bookmarks")?.let { decodeEntries(it) }.orEmpty()
        val remoteHist = if (settings.syncHistory) remote?.optJSONArray("history")?.let { decodeEntries(it) } else null
        store.mergeFromRemote(remoteBm, remoteHist, remoteSettings)
        val (mergedSettings, mergedBm, mergedHist) = store.snapshotRecords()
        val payload = JSONObject()
            .put("bookmarks", encodeEntries(mergedBm))
            .put("settings", encodeSettingsJson(mergedSettings))
        if (mergedSettings.syncHistory) payload.put("history", encodeEntries(mergedHist))
        saveGist(token, payload)
        tokens.lastSync = System.currentTimeMillis()
        return "同步完成"
    }

    private fun loadGist(token: String): JSONObject? {
        val id = tokens.gistId ?: findGistId(token)
        if (id.isNullOrBlank()) return null
        tokens.gistId = id
        val gist = JSONObject(get("https://api.github.com/gists/$id", token))
        val file = gist.optJSONObject("files")?.optJSONObject(FILE) ?: return null
        val content = file.optString("content")
        if (content.isBlank()) return null
        return runCatching { JSONObject(content) }.getOrNull()
    }

    private fun findGistId(token: String): String? {
        val list = JSONArray(get("https://api.github.com/gists?per_page=100", token))
        for (i in 0 until list.length()) {
            val g = list.getJSONObject(i)
            val files = g.optJSONObject("files")
            if (g.optString("description") == DESC || files?.has(FILE) == true) return g.optString("id")
        }
        return null
    }

    private fun saveGist(token: String, payload: JSONObject) {
        val files = JSONObject().put(FILE, JSONObject().put("content", payload.toString()))
        val body = JSONObject().put("description", DESC).put("public", false).put("files", files)
        val id = tokens.gistId
        if (id.isNullOrBlank()) {
            val created = JSONObject(post("https://api.github.com/gists", body.toString(), token))
            tokens.gistId = created.optString("id")
        } else {
            patch("https://api.github.com/gists/$id", body.toString(), token)
        }
    }

    private fun encodeEntries(items: List<PageEntry>): JSONArray = JSONArray().apply {
        items.forEach { e ->
            put(JSONObject()
                .put("title", e.title).put("url", e.url).put("folder", e.folder)
                .put("time", e.time).put("updatedAt", e.updatedAt).put("deleted", e.deleted))
        }
    }

    private fun decodeEntries(arr: JSONArray): List<PageEntry> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val time = o.optLong("time")
            add(PageEntry(
                title = o.optString("title"),
                url = o.optString("url"),
                time = time,
                folder = o.optString("folder"),
                updatedAt = if (o.has("updatedAt")) o.optLong("updatedAt") else time,
                deleted = o.optBoolean("deleted"),
            ))
        }
    }

    private fun encodeSettingsJson(s: AppSettings): JSONObject = JSONObject()
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

    private fun decodeSettingsJson(o: JSONObject): AppSettings {
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

    private fun get(url: String, token: String?): String = request("GET", url, null, token)
    private fun post(url: String, json: String, token: String?): String = request("POST", url, json, token)
    private fun patch(url: String, json: String, token: String?): String = request("PATCH", url, json, token)

    private fun request(method: String, url: String, json: String?, token: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "M3Browser/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15000
            readTimeout = 20000
            if (json != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (json != null) OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            if (code == 401) error("登录已失效，请重新登录")
            if (code !in 200..299 && !text.trim().startsWith("{") && !text.trim().startsWith("[")) {
                error("GitHub 请求失败（$code）")
            }
            text.ifBlank { "{}" }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val FILE = "m3browser-sync.json"
        const val DESC = "m3browser-sync"
    }
}
