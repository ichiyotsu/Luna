package com.m3browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class GithubRelease(val tag: String, val notes: String, val apkUrl: String, val assetName: String)

/** Prefer the current Luna asset, and still accept the 1.0.2 M3Browser file name. */
fun pickReleaseApk(names: List<String>): String? {
    val apks = names.filter { it.endsWith(".apk", ignoreCase = true) }
    return apks.firstOrNull { it.startsWith("Luna-", ignoreCase = true) }
        ?: apks.firstOrNull { it.startsWith("M3Browser-", ignoreCase = true) }
        ?: apks.firstOrNull()
}

class UpdateChecker(private val context: Context) {
    fun fetchLatest(): GithubRelease? {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "M3Browser/${BuildConfig.VERSION_NAME}")
            connectTimeout = 12000
            readTimeout = 12000
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name")
            val notes = json.optString("body")
            val assets = json.optJSONArray("assets") ?: return null
            val named = buildList {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val n = a.optString("name")
                    if (n.endsWith(".apk", true)) add(n to a.optString("browser_download_url"))
                }
            }
            val chosen = pickReleaseApk(named.map { it.first })
            val url = named.firstOrNull { it.first == chosen }?.second.orEmpty()
            if (url.isBlank() || tag.isBlank()) null else GithubRelease(tag, notes, url, chosen.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    fun enqueue(release: GithubRelease): Long {
        val name = release.assetName.ifBlank { "Luna-${release.tag}.apk" }
        val req = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
            setTitle(name)
            setDescription("正在下载 Luna ${release.tag}")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            addRequestHeader("User-Agent", "M3Browser/${BuildConfig.VERSION_NAME}")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }

    fun progress(id: Long): Pair<Int, Int> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return -1 to 0
        cursor.use {
            if (!it.moveToFirst()) return -1 to 0
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val got = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val pct = if (total > 0) ((got * 100) / total).toInt().coerceIn(0, 100) else 0
            return status to pct
        }
    }

    fun downloadedFile(name: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)

    fun canRequestInstalls(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun unknownSourcesIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val LATEST = "https://api.github.com/repos/ichiyotsu/Luna/releases/latest"
    }
}
