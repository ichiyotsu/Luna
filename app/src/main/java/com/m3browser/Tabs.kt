package com.m3browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BrowserTab(val id: Long) {
    var url by mutableStateOf("")
    var title by mutableStateOf("新标签页")
    var progress by mutableFloatStateOf(1f)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)
    /** null while the tab shows the home page and no page has been loaded yet */
    var webView: WebView? = null
    val isHome get() = url.isEmpty()
}

class BrowserController(
    private val context: Context,
    private val onVisited: (PageEntry) -> Unit,
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var currentIndex by mutableIntStateOf(0)
    private var nextId = 1L
    val current: BrowserTab? get() = tabs.getOrNull(currentIndex)

    fun newTab(url: String? = null) {
        val t = BrowserTab(nextId++)
        tabs.add(t)
        currentIndex = tabs.lastIndex
        if (!url.isNullOrEmpty()) load(t, url)
    }

    fun select(index: Int) { if (index in tabs.indices) currentIndex = index }

    fun close(tab: BrowserTab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        destroyWebView(tab)
        tabs.removeAt(idx)
        if (tabs.isEmpty()) newTab()
        else if (currentIndex >= idx && currentIndex > 0) currentIndex--
        currentIndex = currentIndex.coerceIn(0, tabs.lastIndex)
    }

    fun closeAll() { tabs.toList().forEach { destroyWebView(it) }; tabs.clear(); newTab() }

    fun load(tab: BrowserTab, url: String) {
        if (url.isEmpty()) return
        tab.url = url
        val wv = tab.webView ?: createWebView(tab).also { tab.webView = it }
        wv.loadUrl(url)
    }

    fun goHome(tab: BrowserTab) {
        captureThumb(tab)
        destroyWebView(tab)
        tab.url = ""; tab.title = "新标签页"; tab.loading = false; tab.progress = 1f
        tab.canGoBack = false; tab.canGoForward = false; tab.favicon = null
    }

    fun back(tab: BrowserTab): Boolean {
        val wv = tab.webView ?: return false
        if (wv.canGoBack()) { wv.goBack(); return true }
        goHome(tab); return true
    }

    fun forward(tab: BrowserTab) { tab.webView?.takeIf { it.canGoForward() }?.goForward() }
    fun reload(tab: BrowserTab) { tab.webView?.reload() }
    fun stop(tab: BrowserTab) { tab.webView?.stopLoading(); tab.loading = false; tab.progress = 1f }

    fun captureThumb(tab: BrowserTab) {
        val wv = tab.webView ?: return
        if (wv.width <= 0 || wv.height <= 0) return
        runCatching {
            val scale = 0.35f
            val bmp = Bitmap.createBitmap((wv.width * scale).toInt().coerceAtLeast(1),
                (wv.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            tab.thumbnail = bmp
        }
    }

    fun onPause() { tabs.forEach { it.webView?.onPause() } }
    fun onResume() { tabs.forEach { it.webView?.onResume() } }
    fun destroyAll() { tabs.forEach { destroyWebView(it) } }

    private fun destroyWebView(tab: BrowserTab) {
        tab.webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading(); wv.destroy()
        }
        tab.webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (UrlUtils.isWebScheme(u)) return false
                openExternal(view, u)
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.loading = true; tab.progress = 0.05f
                url?.let { tab.url = it }
                tab.favicon = favicon
                updateNav(view)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                tab.loading = false; tab.progress = 1f
                url?.let { tab.url = it }
                view.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
                updateNav(view)
                if (url != null && url.startsWith("http")) onVisited(PageEntry(tab.title, url))
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                url?.let { tab.url = it }; updateNav(view)
            }
            private fun updateNav(v: WebView) { tab.canGoBack = v.canGoBack(); tab.canGoForward = v.canGoForward() }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress / 100f
                tab.loading = newProgress < 100
            }
            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { tab.favicon = icon }
        }
        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun openExternal(view: WebView, url: String) {
        try {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                .apply { addCategory(Intent.CATEGORY_BROWSABLE); component = null; selector = null }
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (fallback != null && URLUtil.isNetworkUrl(fallback)) view.loadUrl(fallback)
                else toast("没有可以打开此链接的应用")
            }
        } catch (e: Exception) {
            toast("无法打开链接")
        }
    }

    private fun startDownload(url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                ua?.let { addRequestHeader("User-Agent", it) }
                setTitle(name)
                setDescription("M3 Browser 下载")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            toast("开始下载：$name")
        } catch (e: Exception) {
            toast("下载失败：${e.message}")
        }
    }

    private fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()
}
