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
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class ScrollSample(val dy: Int, val atTop: Boolean, val atBottom: Boolean, val seq: Int)

class BrowserTab(val id: Long, val incognito: Boolean = false) {
    var url by mutableStateOf("")
    var title by mutableStateOf(if (incognito) "无痕标签页" else "新标签页")
    var progress by mutableFloatStateOf(1f)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)
    var webView: WebView? = null
    var desktop by mutableStateOf(false)
    var translated by mutableStateOf(false)
    var scroll by mutableStateOf(ScrollSample(0, true, false, 0))
    var findActive by mutableIntStateOf(0)
    var findCount by mutableIntStateOf(0)
    var isolatedProfile by mutableStateOf(false)
    val isHome get() = url.isEmpty()
}

interface TranslateHost {
    fun onLanguage(tabId: Long, lang: String, sample: String)
    fun onBatch(tabId: Long, json: String)
}

class BrowserController(
    private val context: Context,
    private val onVisited: (PageEntry) -> Unit,
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var currentIndex by mutableIntStateOf(0)
    private var nextId = 1L
    val current: BrowserTab? get() = tabs.getOrNull(currentIndex)

    var openInNewTab: Boolean = false
    var desktopDefault: Boolean = false
    var translateHost: TranslateHost? = null
    private var runtime = AppSettings()
    private var stockUserAgent: String? = null

    fun newTab(url: String? = null, incognito: Boolean = false) {
        val t = BrowserTab(nextId++, incognito)
        t.desktop = desktopDefault
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
        wipeIncognitoIfIdle()
    }

    fun closeAll() {
        tabs.toList().forEach { destroyWebView(it) }
        tabs.clear()
        wipeIncognitoIfIdle()
        newTab()
    }

    fun load(tab: BrowserTab, url: String) {
        if (url.isEmpty()) return
        tab.url = url
        tab.translated = false
        val wv = tab.webView ?: createWebView(tab).also { tab.webView = it }
        wv.loadUrl(url)
    }

    fun goHome(tab: BrowserTab) {
        captureThumb(tab)
        destroyWebView(tab)
        tab.url = ""
        tab.title = if (tab.incognito) "无痕标签页" else "新标签页"
        tab.loading = false
        tab.progress = 1f
        tab.canGoBack = false
        tab.canGoForward = false
        tab.favicon = null
        tab.translated = false
        tab.scroll = ScrollSample(0, true, false, tab.scroll.seq + 1)
    }

    fun back(tab: BrowserTab): Boolean {
        val wv = tab.webView ?: return false
        if (wv.canGoBack()) { wv.goBack(); return true }
        goHome(tab)
        return true
    }

    fun forward(tab: BrowserTab) { tab.webView?.takeIf { it.canGoForward() }?.goForward() }
    fun reload(tab: BrowserTab) { tab.webView?.reload() }
    fun stop(tab: BrowserTab) { tab.webView?.stopLoading(); tab.loading = false; tab.progress = 1f }

    fun toggleDesktop(tab: BrowserTab) {
        tab.desktop = !tab.desktop
        val wv = tab.webView ?: return
        applyUserAgent(wv, tab.desktop)
        wv.reload()
    }

    fun find(tab: BrowserTab, query: String) {
        if (query.isBlank()) tab.webView?.clearMatches() else tab.webView?.findAllAsync(query)
    }

    fun findNext(tab: BrowserTab, forward: Boolean) { tab.webView?.findNext(forward) }
    fun clearFind(tab: BrowserTab) { tab.webView?.clearMatches(); tab.findActive = 0; tab.findCount = 0 }

    fun apply(settings: AppSettings) {
        runtime = settings
        openInNewTab = settings.openInNewTab
        desktopDefault = settings.desktopDefault
        tabs.forEach { tab ->
            if (tab.isHome && tab.webView == null) tab.desktop = settings.desktopDefault
            tab.webView?.let { applyWebSettings(it, tab, settings, reloadIfScriptChanged = true) }
        }
    }

    fun captureThumb(tab: BrowserTab) {
        val wv = tab.webView ?: return
        if (wv.width <= 0 || wv.height <= 0) return
        runCatching {
            val scale = 0.35f
            val bmp = Bitmap.createBitmap(
                (wv.width * scale).toInt().coerceAtLeast(1),
                (wv.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.RGB_565,
            )
            val c = android.graphics.Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            tab.thumbnail = bmp
        }
    }

    fun onPause() { tabs.forEach { it.webView?.onPause() } }
    fun onResume() { tabs.forEach { it.webView?.onResume() } }
    fun destroyAll() { tabs.forEach { destroyWebView(it) }; wipeIncognitoIfIdle() }

    fun clearBrowsingData(cache: Boolean, cookies: Boolean, history: Boolean) {
        if (cache) {
            tabs.forEach { it.webView?.clearCache(true) }
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            incognitoProfile()?.cookieManager?.removeAllCookies(null)
        }
        if (history) {
            tabs.forEach { it.webView?.clearHistory() }
        }
    }

    private fun incognitoProfile() = runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getProfile(INCOGNITO_PROFILE)
        } else null
    }.getOrNull()

    private fun wipeIncognitoIfIdle() {
        if (tabs.any { it.incognito }) return
        val profile = incognitoProfile() ?: return
        runCatching { profile.cookieManager.removeAllCookies(null) }
        runCatching { profile.cookieManager.flush() }
        runCatching { profile.webStorage.deleteAllData() }
    }

    private fun destroyWebView(tab: BrowserTab) {
        tab.webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading()
            if (tab.incognito) {
                wv.clearCache(true)
                wv.clearHistory()
                wv.clearFormData()
            }
            wv.destroy()
        }
        tab.webView = null
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun createWebView(tab: BrowserTab): WebView = M3WebView(context, tab) { attached ->
        applyWebSettings(attached, tab, runtime, reloadIfScriptChanged = false)
    }.apply {
        if (tab.incognito && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE)
                WebViewCompat.setProfile(this, INCOGNITO_PROFILE)
                tab.isolatedProfile = true
            }
        }
        if (tab.incognito) settings.cacheMode = WebSettings.LOAD_NO_CACHE
        setBackgroundColor(android.graphics.Color.WHITE)
        applyWebSettings(this, tab, runtime, reloadIfScriptChanged = false)
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        addJavascriptInterface(JsBridge(), "M3Browser")
        setFindListener { active, number, _ ->
            tab.findActive = active
            tab.findCount = number
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (!UrlUtils.isWebScheme(u)) {
                    openExternal(view, u)
                    return true
                }
                if (openInNewTab && request.isForMainFrame && request.hasGesture() && u != view.url) {
                    newTab(u, incognito = tab.incognito)
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // Re-apply after navigation. Some WebView builds drop JavaScript when the user agent changes.
                applyWebSettings(view, tab, runtime, reloadIfScriptChanged = false)
                tab.loading = true
                tab.progress = 0.05f
                tab.translated = false
                url?.let { tab.url = it }
                tab.favicon = favicon
                updateNav(view)
                injectHelpers(view, tab.id)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                applyWebSettings(view, tab, runtime, reloadIfScriptChanged = false)
                tab.loading = false
                tab.progress = 1f
                url?.let { tab.url = it }
                view.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
                updateNav(view)
                injectHelpers(view, tab.id)
                if (!tab.incognito && url != null && url.startsWith("http")) onVisited(PageEntry(tab.title, url))
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                url?.let { tab.url = it }
                updateNav(view)
                injectHelpers(view, tab.id)
            }
            private fun updateNav(v: WebView) {
                tab.canGoBack = v.canGoBack()
                tab.canGoForward = v.canGoForward()
            }
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

    private fun applyWebSettings(wv: WebView, tab: BrowserTab, settings: AppSettings, reloadIfScriptChanged: Boolean) {
        val wasJs = wv.settings.javaScriptEnabled
        val js = settings.javascript
        if (stockUserAgent == null) {
            val current = wv.settings.userAgentString
            if (!current.isNullOrBlank() && !current.contains("Windows NT 10.0; Win64")) stockUserAgent = current
        }
        // Change the user agent before JavaScript. Restoring a null user agent has been observed
        // to clear javaScriptEnabled on some WebView builds, so JavaScript is always set last.
        wv.settings.userAgentString = if (tab.desktop) DESKTOP_UA else stockUserAgent
        wv.settings.textZoom = settings.textZoom.coerceIn(50, 200)
        wv.settings.domStorageEnabled = true
        wv.settings.loadsImagesAutomatically = true
        wv.settings.javaScriptCanOpenWindowsAutomatically = true
        wv.settings.useWideViewPort = true
        wv.settings.loadWithOverviewMode = true
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            wv.settings.isAlgorithmicDarkeningAllowed = false
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            wv.settings.forceDark = WebSettings.FORCE_DARK_OFF
        }
        wv.settings.javaScriptEnabled = js
        val cookies = cookieManager(tab)
        cookies?.setAcceptCookie(true)
        runCatching { cookies?.setAcceptThirdPartyCookies(wv, !settings.blockThirdPartyCookies) }
        if (reloadIfScriptChanged && wasJs != js && !tab.isHome) wv.reload()
    }

    private fun cookieManager(tab: BrowserTab): CookieManager? = if (tab.incognito && tab.isolatedProfile) {
        incognitoProfile()?.cookieManager ?: CookieManager.getInstance()
    } else CookieManager.getInstance()

    private fun applyUserAgent(wv: WebView, desktop: Boolean) {
        if (stockUserAgent == null) {
            val current = wv.settings.userAgentString
            if (!current.isNullOrBlank() && !current.contains("Windows NT 10.0; Win64")) stockUserAgent = current
        }
        wv.settings.userAgentString = if (desktop) DESKTOP_UA else stockUserAgent
        wv.settings.javaScriptEnabled = runtime.javascript
        wv.settings.domStorageEnabled = true
    }

    private fun injectHelpers(wv: WebView, tabId: Long) {
        wv.evaluateJavascript(PageScripts.bootstrap(tabId), null)
    }

    fun runPageJs(tab: BrowserTab, js: String) {
        tab.webView?.evaluateJavascript(js, null)
    }

    private inner class JsBridge {
        @android.webkit.JavascriptInterface
        fun onLang(tabId: String, lang: String, sample: String) {
            translateHost?.onLanguage(tabId.toLongOrNull() ?: return, lang, sample)
        }

        @android.webkit.JavascriptInterface
        fun onBatch(tabId: String, json: String) {
            translateHost?.onBatch(tabId.toLongOrNull() ?: return, json)
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
                setDescription("Luna 下载")
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

    private class M3WebView(
        context: Context,
        val tab: BrowserTab,
        private val onAttach: (WebView) -> Unit,
    ) : WebView(context) {
        private var seq = 0
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            onAttach(this)
        }
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            val dy = t - oldt
            val contentPx = (contentHeight * scale).toInt()
            val atBottom = height > 0 && contentPx > height && t + height >= contentPx - 8
            val atTop = t <= 2
            tab.scroll = ScrollSample(dy, atTop, atBottom, ++seq)
        }
    }

    companion object {
        const val INCOGNITO_PROFILE = "m3_incognito"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
