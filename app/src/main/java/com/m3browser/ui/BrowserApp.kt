@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, kotlinx.coroutines.FlowPreview::class)

package com.m3browser.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.m3browser.AppSettings
import com.m3browser.BrowserController
import com.m3browser.BrowserTab
import com.m3browser.BuildConfig
import com.m3browser.GithubRelease
import com.m3browser.GitHubSync
import com.m3browser.HistoryRange
import com.m3browser.PageEntry
import com.m3browser.SearchEngine
import com.m3browser.SemVer
import com.m3browser.Store
import com.m3browser.TokenStore
import com.m3browser.ToolbarAction
import com.m3browser.UpdateChecker
import com.m3browser.UrlUtils
import com.m3browser.chromeInsets
import com.m3browser.languageLabel
import com.m3browser.pillOverlapPx
import com.m3browser.shouldExpandBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Screen { BROWSER, TABS, SETTINGS, HISTORY, TOOLBAR }
private enum class Sheet { NONE, BOOKMARKS }

@Composable
fun BrowserApp(controller: BrowserController, store: Store) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by store.settings.collectAsState(initial = AppSettings())
    val bookmarks by store.bookmarks.collectAsState(initial = emptyList())
    val history by store.history.collectAsState(initial = emptyList())
    val tokens = remember { TokenStore(context) }
    val sync = remember { GitHubSync(tokens, store) }
    val snackbar = remember { SnackbarHostState() }
    val translate = remember { TranslateController(controller, scope) { scope.launch { snackbar.showSnackbar(it) } } }
    translate.targetLang = settings.translateLang
    translate.never = settings.neverTranslate
    translate.onNever = { host -> scope.launch { store.updateSettings { s -> s.copy(neverTranslate = s.neverTranslate + host) } } }
    controller.translateHost = translate

    var screen by remember { mutableStateOf(Screen.BROWSER) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var offer by remember { mutableStateOf<GithubRelease?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadName by remember { mutableStateOf("") }
    var downloadPct by remember { mutableIntStateOf(0) }
    var askInstall by remember { mutableStateOf<File?>(null) }
    var authGeneration by remember { mutableIntStateOf(0) }
    val tab = controller.current
    val checker = remember { UpdateChecker(context) }

    LaunchedEffect(settings.javascript, settings.textZoom, settings.blockThirdPartyCookies, settings.desktopDefault, settings.openInNewTab) {
        controller.apply(settings)
    }
    val secure = settings.flagSecure && controller.tabs.any { it.incognito }
    DisposableEffect(secure) {
        val window = (context as? Activity)?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
    LaunchedEffect(settings.autoUpdateCheck, settings.ignoredUpdate) {
        if (!settings.autoUpdateCheck) return@LaunchedEffect
        val rel = withContext(Dispatchers.IO) { runCatching { checker.fetchLatest() }.getOrNull() } ?: return@LaunchedEffect
        if (SemVer.isNewer(rel.tag, BuildConfig.VERSION_NAME) && !isIgnored(settings.ignoredUpdate, rel.tag)) offer = rel
    }
    LaunchedEffect(settings.autoSync, authGeneration) {
        if (!settings.autoSync || tokens.token == null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { sync.syncNow() } }
        store.changes.debounce(5000).collect {
            if (tokens.token != null) runCatching { withContext(Dispatchers.IO) { sync.syncNow() } }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, settings.autoSync) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && settings.autoSync && tokens.token != null) {
                scope.launch(Dispatchers.IO) { runCatching { sync.syncNow() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val (status, pct) = withContext(Dispatchers.IO) { checker.progress(id) }
            downloadPct = pct
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadId = null
                    val file = checker.downloadedFile(downloadName)
                    if (checker.canRequestInstalls()) checker.install(file) else askInstall = file
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadId = null
                    snackbar.showSnackbar("下载失败")
                    break
                }
            }
            delay(400)
        }
    }

    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun checkUpdate() {
        scope.launch {
            val rel = withContext(Dispatchers.IO) { runCatching { checker.fetchLatest() }.getOrNull() }
            when {
                rel == null -> message("检查失败，请稍后重试")
                SemVer.isNewer(rel.tag, BuildConfig.VERSION_NAME) -> offer = rel
                else -> message("已是最新版本")
            }
        }
    }
    val open: (String) -> Unit = { input ->
        val url = UrlUtils.toUrl(input, settings.engine)
        controller.current?.let { if (url.isNotEmpty()) controller.load(it, url) }
        screen = Screen.BROWSER
    }

    BackHandler(enabled = screen != Screen.BROWSER || (tab != null && !tab.isHome)) {
        if (screen != Screen.BROWSER) screen = Screen.BROWSER else tab?.let { controller.back(it) }
    }

    val motion = MaterialTheme.motionScheme
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.BROWSER -> if (tab != null) BrowserScreen(
                    controller, tab, settings, bookmarks.any { it.url == tab.url }, translate, open,
                    onShowTabs = { controller.captureThumb(tab); screen = Screen.TABS },
                    onToggleBookmark = { scope.launch { store.toggleBookmark(PageEntry(tab.title, tab.url)) } },
                    onBookmarks = { sheet = Sheet.BOOKMARKS },
                    onHistory = { screen = Screen.HISTORY },
                    onSettings = { screen = Screen.SETTINGS },
                )
                Screen.TABS -> TabSwitcher(controller,
                    onSelect = { controller.select(it); screen = Screen.BROWSER },
                    onNew = { controller.newTab(); screen = Screen.BROWSER },
                    onNewIncognito = { controller.newTab(incognito = true); screen = Screen.BROWSER },
                    onBack = { screen = Screen.BROWSER })
                Screen.SETTINGS -> SettingsScreen(
                    store, settings, tokens, sync,
                    onBack = { screen = Screen.BROWSER },
                    onToolbar = { screen = Screen.TOOLBAR },
                    onCheckUpdate = { checkUpdate() },
                    onAuthChanged = { authGeneration++ },
                    onClearData = { cache, cookies, historyOn ->
                        controller.clearBrowsingData(cache, cookies, false)
                        if (historyOn) scope.launch { store.clearHistory(HistoryRange.ALL) }
                        message("已清除所选数据")
                    },
                    onMessage = { message(it) },
                )
                Screen.HISTORY -> HistoryScreen(
                    history,
                    onOpen = { open(it.url) },
                    onDelete = { entry -> scope.launch { store.removeHistory(entry.url) } },
                    onClear = { range -> scope.launch { store.clearHistory(range) } },
                    onBack = { screen = Screen.BROWSER },
                )
                Screen.TOOLBAR -> ToolbarEditor(
                    settings.toolbar,
                    onChange = { list -> scope.launch { store.updateSettings { s -> s.copy(toolbar = list) } } },
                    onBack = { screen = Screen.SETTINGS },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }

    if (sheet == Sheet.BOOKMARKS) {
        PageListSheet(
            title = "书签",
            entries = bookmarks,
            emptyText = "还没有书签",
            onOpen = { sheet = Sheet.NONE; open(it.url) },
            onDelete = { e -> scope.launch { store.removeBookmark(e.url) } },
            onDismiss = { sheet = Sheet.NONE },
        )
    }
    offer?.let { rel ->
        ModalBottomSheet(onDismissRequest = { offer = null }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Text("发现新版本 ${rel.tag}", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text("当前版本 ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(rel.notes.ifBlank { "此版本没有发布说明。" }, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    offer = null
                    downloadName = rel.assetName.ifBlank { "M3Browser-${rel.tag}.apk" }
                    downloadPct = 0
                    downloadId = checker.enqueue(rel.copy(assetName = downloadName))
                }, modifier = Modifier.fillMaxWidth()) { Text("立即更新") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { offer = null }) { Text("稍后") }
                    TextButton(onClick = {
                        val tag = rel.tag
                        offer = null
                        scope.launch { store.updateSettings { s -> s.copy(ignoredUpdate = tag) } }
                    }) { Text("忽略此版本") }
                }
            }
        }
    }
    if (downloadId != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column {
                    LinearWavyProgressIndicator(progress = { downloadPct / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("$downloadPct%", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    downloadId?.let { (context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager).remove(it) }
                    downloadId = null
                }) { Text("取消") }
            },
        )
    }
    askInstall?.let { file ->
        AlertDialog(
            onDismissRequest = { askInstall = null },
            title = { Text("需要允许安装未知应用") },
            text = { Text("下载已完成。请在系统设置中允许 M3 Browser 安装应用，然后返回继续。") },
            confirmButton = {
                TextButton(onClick = {
                    if (checker.canRequestInstalls()) {
                        checker.install(file)
                        askInstall = null
                    } else context.startActivity(checker.unknownSourcesIntent())
                }) { Text(if (checker.canRequestInstalls()) "安装" else "去设置") }
            },
            dismissButton = { TextButton(onClick = { askInstall = null }) { Text("稍后") } },
        )
    }
}

private fun isIgnored(ignored: String, tag: String): Boolean {
    if (ignored.isBlank()) return false
    return ignored == tag || SemVer.same(ignored, tag)
}

@Composable
private fun BrowserScreen(
    controller: BrowserController,
    tab: BrowserTab,
    settings: AppSettings,
    isBookmarked: Boolean,
    translate: TranslateController,
    onSubmit: (String) -> Unit,
    onShowTabs: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var findMode by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var expanded by remember(tab.id) { mutableStateOf(true) }
    var barPx by remember { mutableIntStateOf(with(density) { 148.dp.roundToPx() }) }
    var pillPx by remember { mutableIntStateOf(with(density) { 44.dp.roundToPx() }) }
    val statusPx = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density)
    val navPx = WindowInsets.navigationBars.getBottom(density)
    val imePx = WindowInsets.ime.getBottom(density)
    val force = focused || menu || findMode || tab.isHome || translate.prompt?.tabId == tab.id || translate.busy
    LaunchedEffect(tab.scroll.seq, force, tab.id, tab.isHome) {
        expanded = if (tab.isHome) true else shouldExpandBar(expanded, tab.scroll.dy, tab.scroll.atTop, tab.scroll.atBottom, force)
    }
    val overlap = pillOverlapPx(pillPx, if (settings.barAtTop) statusPx else navPx)
    val insets = chromeInsets(settings.barAtTop, expanded, if (expanded) barPx else 0, statusPx, navPx, imePx, if (expanded) 0 else overlap)
    LaunchedEffect(insets.jsTop, insets.jsBottom, tab.id, tab.url, expanded, tab.scroll.atBottom) {
        controller.updateInset(insets.jsTop, insets.jsBottom, expanded && tab.scroll.atBottom && !tab.isHome)
    }
    val outerBottom = with(density) { maxOf(navPx, imePx).toDp() }
    val outerTop = with(density) { statusPx.toDp() }
    val shift: Dp = if (!expanded) with(density) { (pillPx - overlap).coerceAtLeast(0).toDp() } else 0.dp

    fun dispatch(action: ToolbarAction) {
        when (action) {
            ToolbarAction.BACK -> controller.back(tab)
            ToolbarAction.FORWARD -> controller.forward(tab)
            ToolbarAction.HOME -> controller.goHome(tab)
            ToolbarAction.RELOAD -> if (tab.loading) controller.stop(tab) else controller.reload(tab)
            ToolbarAction.TABS -> onShowTabs()
            ToolbarAction.SHARE -> sharePage(context, tab)
            ToolbarAction.TRANSLATE -> if (tab.translated) translate.showOriginal(tab) else translate.translate(tab)
            ToolbarAction.BOOKMARKS -> onBookmarks()
            ToolbarAction.ADD_BOOKMARK -> if (!tab.isHome) onToggleBookmark()
            ToolbarAction.HISTORY -> onHistory()
            ToolbarAction.INCOGNITO -> controller.newTab(incognito = true)
            ToolbarAction.NEW_TAB -> controller.newTab()
            ToolbarAction.DESKTOP -> controller.toggleDesktop(tab)
            ToolbarAction.FIND -> { findMode = true; expanded = true }
            ToolbarAction.SETTINGS -> onSettings()
            ToolbarAction.MENU -> menu = true
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxSize().padding(bottom = with(density) { insets.viewPadBottom.toDp() })) {
            if (tab.isHome) {
                HomePage(settings.engine, settings.shortcuts, onSubmit, Modifier.padding(
                    top = with(density) { insets.jsTop.toDp() },
                    bottom = with(density) { insets.jsBottom.toDp() },
                ))
            } else key(tab.id, tab.webView) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        val wv = tab.webView
                        if (wv != null && wv.parent !== frame) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            frame.removeAllViews()
                            frame.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Scrim(top = true, height = outerTop + 32.dp)
        Scrim(top = false, height = outerBottom + 40.dp)
        translate.prompt?.takeIf { it.tabId == tab.id }?.let { prompt ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .align(if (settings.barAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(
                        top = if (settings.barAtTop) outerTop + 8.dp else 0.dp,
                        bottom = if (settings.barAtTop) 0.dp else outerBottom + 72.dp,
                        start = 16.dp, end = 16.dp,
                    )
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("此页面语言与${languageLabel(settings.translateLang)}不同，要翻译吗？", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { translate.acceptPrompt() }) { Text("翻译") }
                        TextButton(onClick = { translate.dismiss() }) { Text("稍后") }
                        TextButton(onClick = { translate.neverSite() }) { Text("此站不再提示") }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(if (settings.barAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = if (settings.barAtTop) outerTop else 0.dp, bottom = if (settings.barAtTop) 0.dp else outerBottom)
                .offset(y = if (!expanded && settings.barAtTop) -shift else if (!expanded) shift else 0.dp)
                .onSizeChanged { if (expanded) barPx = it.height else pillPx = it.height },
            contentAlignment = Alignment.Center,
        ) {
            UnifiedBar(
                tab, settings, controller.tabs.size, expanded, isBookmarked, menu, findMode, findQuery, translate.busy,
                onExpand = { expanded = true },
                onMenu = { menu = it },
                onSubmit = onSubmit,
                onFocused = { focused = it },
                onReloadStop = { if (tab.loading) controller.stop(tab) else controller.reload(tab) },
                onAction = { dispatch(it) },
                onFindQuery = { findQuery = it; controller.find(tab, it) },
                onFindNext = { controller.findNext(tab, true) },
                onCloseFind = { findMode = false; findQuery = ""; controller.clearFind(tab) },
                onDispatchMenu = { id -> menu = false; dispatch(id) },
                incognitoNew = { menu = false; controller.newTab(incognito = true) },
                bookmarkToggle = { menu = false; onToggleBookmark() },
                share = { menu = false; sharePage(context, tab) },
                translateToggle = {
                    menu = false
                    if (tab.translated) translate.showOriginal(tab) else translate.translate(tab)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnifiedBar(
    tab: BrowserTab,
    settings: AppSettings,
    tabCount: Int,
    expanded: Boolean,
    isBookmarked: Boolean,
    menu: Boolean,
    findMode: Boolean,
    findQuery: String,
    translating: Boolean,
    onExpand: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onFocused: (Boolean) -> Unit,
    onReloadStop: () -> Unit,
    onAction: (ToolbarAction) -> Unit,
    onFindQuery: (String) -> Unit,
    onFindNext: () -> Unit,
    onCloseFind: () -> Unit,
    onDispatchMenu: (ToolbarAction) -> Unit,
    incognitoNew: () -> Unit,
    bookmarkToggle: () -> Unit,
    share: () -> Unit,
    translateToggle: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val shape = if (expanded) RoundedCornerShape(28.dp) else CircleShape
    val tint = if (tab.incognito) Color(0xFF1A1A1E).copy(alpha = 0.78f)
    else MaterialTheme.colorScheme.surface.copy(alpha = if (settings.blur) 0.70f else 0.94f)
    val contentColor = if (tab.incognito) Color(0xFFF5F5F5) else MaterialTheme.colorScheme.onSurface
    Frosted(
        enabled = settings.blur && !tab.isHome,
        webView = tab.webView,
        tint = tint,
        shape = shape,
        modifier = Modifier
            .animateContentSize(motion.defaultSpatialSpec())
            .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 6.dp)
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.widthIn(max = 260.dp)),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (!expanded) {
                Row(
                    Modifier.clickable(onClick = onExpand).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeadingMark(tab, false)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (tab.isHome) "新标签页" else UrlUtils.displayHost(tab.url).ifBlank { tab.title },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor,
                    )
                    if (tab.loading) LoadingIndicator(Modifier.padding(start = 8.dp).size(18.dp))
                }
            } else {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    if (!settings.barAtTop && tab.loading && !tab.isHome) {
                        val p by animateFloatAsState(tab.progress, spring(stiffness = Spring.StiffnessLow), label = "p")
                        LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    }
                    if (tab.incognito) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) {
                            Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(14.dp), tint = contentColor)
                            Text(" 无痕", style = MaterialTheme.typography.labelMedium, color = contentColor)
                        }
                    }
                    if (findMode) {
                        FindRow(findQuery, tab.findActive, tab.findCount, onFindQuery, onFindNext, onCloseFind, contentColor)
                    } else {
                        AddressField(tab, contentColor, onSubmit, onFocused, onReloadStop)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        settings.toolbar.forEach { action ->
                            val active = (action == ToolbarAction.DESKTOP && tab.desktop) ||
                                (action == ToolbarAction.TRANSLATE && tab.translated) ||
                                (action == ToolbarAction.ADD_BOOKMARK && isBookmarked)
                            ActionIcon(action, tab, tabCount, active, translating) { onAction(action) }
                        }
                        Box {
                            IconButton(onClick = { onMenu(true) }) { Icon(Icons.Rounded.MoreVert, "菜单", tint = contentColor) }
                            DropdownMenu(expanded = menu, onDismissRequest = { onMenu(false) }, shape = RoundedCornerShape(20.dp)) {
                                MenuRow("新标签页", Icons.Rounded.Add) { onDispatchMenu(ToolbarAction.NEW_TAB) }
                                MenuRow("无痕标签页", Icons.Rounded.VisibilityOff) { incognitoNew() }
                                if (!tab.isHome) MenuRow(if (isBookmarked) "移除书签" else "添加书签", Icons.Rounded.Search) { bookmarkToggle() }
                                MenuRow("分享", Icons.Rounded.Search) { share() }
                                MenuRow("书签", Icons.Rounded.Search) { onDispatchMenu(ToolbarAction.BOOKMARKS) }
                                MenuRow("历史记录", Icons.Rounded.Search) { onDispatchMenu(ToolbarAction.HISTORY) }
                                if (!tab.isHome) MenuRow(if (tab.translated) "显示原文" else "翻译此页", Icons.Rounded.Search) { translateToggle() }
                                if (!tab.isHome) MenuRow("页内查找", Icons.Rounded.Search) { onDispatchMenu(ToolbarAction.FIND) }
                                if (!tab.isHome) MenuRow(if (tab.desktop) "请求移动版网站" else "请求桌面版网站", Icons.Rounded.Search) { onDispatchMenu(ToolbarAction.DESKTOP) }
                                MenuRow("设置", Icons.Rounded.Search) { onDispatchMenu(ToolbarAction.SETTINGS) }
                            }
                        }
                    }
                    if (settings.barAtTop && tab.loading && !tab.isHome) {
                        val p by animateFloatAsState(tab.progress, spring(stiffness = Spring.StiffnessLow), label = "p2")
                        LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(iconFor(iconToAction(text)), null) }, onClick = onClick)
}

private fun iconToAction(text: String): ToolbarAction = when {
    text.contains("无痕") -> ToolbarAction.INCOGNITO
    text.contains("书签") && text.contains("添加") || text.contains("移除书签") -> ToolbarAction.ADD_BOOKMARK
    text.contains("书签") -> ToolbarAction.BOOKMARKS
    text.contains("分享") -> ToolbarAction.SHARE
    text.contains("历史") -> ToolbarAction.HISTORY
    text.contains("翻译") || text.contains("原文") -> ToolbarAction.TRANSLATE
    text.contains("查找") -> ToolbarAction.FIND
    text.contains("桌面") || text.contains("移动") -> ToolbarAction.DESKTOP
    text.contains("设置") -> ToolbarAction.SETTINGS
    else -> ToolbarAction.NEW_TAB
}

@Composable
private fun ActionIcon(action: ToolbarAction, tab: BrowserTab, tabCount: Int, active: Boolean, translating: Boolean, onClick: () -> Unit) {
    val enabled = when (action) {
        ToolbarAction.BACK -> !tab.isHome
        ToolbarAction.FORWARD -> tab.canGoForward
        ToolbarAction.ADD_BOOKMARK, ToolbarAction.SHARE, ToolbarAction.TRANSLATE, ToolbarAction.FIND, ToolbarAction.DESKTOP, ToolbarAction.RELOAD -> !tab.isHome || action == ToolbarAction.RELOAD
        else -> true
    }
    IconButton(onClick = onClick, enabled = enabled) {
        if (action == ToolbarAction.TABS) {
            Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(tabCountLabel(tabCount), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(
                when (action) {
                    ToolbarAction.RELOAD -> if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh
                    ToolbarAction.ADD_BOOKMARK -> iconFor(action)
                    else -> iconFor(action)
                },
                action.label,
                tint = if (active || (action == ToolbarAction.TRANSLATE && translating)) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun tabCountBox(count: Int) {
    Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(if (count > 99) ":D" else "$count", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private fun tabCountLabel(count: Int) = if (count > 99) ":D" else "$count"

@Composable
private fun AddressField(
    tab: BrowserTab,
    contentColor: Color,
    onSubmit: (String) -> Unit,
    onFocused: (Boolean) -> Unit,
    onReloadStop: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var value by remember(tab.id) { mutableStateOf(TextFieldValue("")) }
    val focus = LocalFocusManager.current
    LaunchedEffect(focused, tab.url) {
        if (!focused) value = TextFieldValue(if (tab.isHome) "" else UrlUtils.displayHost(tab.url))
    }
    Surface(shape = CircleShape, color = if (tab.incognito) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 2.dp)) {
            LeadingMark(tab, focused)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.text.isEmpty()) Text("搜索或输入网址", color = contentColor.copy(alpha = 0.6f), maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (value.text.isNotBlank()) { onSubmit(value.text); focus.clearFocus() }
                    }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused && !focused) {
                            val full = if (tab.isHome) "" else tab.url
                            value = TextFieldValue(full, TextRange(0, full.length))
                        }
                        focused = it.isFocused
                        onFocused(it.isFocused)
                    },
                )
            }
            if (focused && value.text.isNotEmpty()) {
                IconButton(onClick = { value = TextFieldValue("") }) { Icon(Icons.Rounded.Close, "清除", tint = contentColor) }
            } else if (!tab.isHome) {
                IconButton(onClick = onReloadStop) {
                    Icon(if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, if (tab.loading) "停止" else "刷新", tint = contentColor)
                }
            }
        }
    }
}

@Composable
private fun LeadingMark(tab: BrowserTab, focused: Boolean) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        when {
            tab.loading && !tab.isHome && !focused -> LoadingIndicator(Modifier.size(22.dp))
            tab.favicon != null && !focused && !tab.isHome -> Image(tab.favicon!!.asImageBitmap(), null, Modifier.size(18.dp).clip(CircleShape))
            tab.url.startsWith("https") && !focused && !tab.isHome -> Icon(Icons.Rounded.Lock, "安全连接", Modifier.size(18.dp))
            else -> Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FindRow(
    query: String,
    active: Int,
    count: Int,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(if (count > 0) "$active/$count" else "", color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = onNext) { Text("下一个") }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭", tint = color) }
    }
}

@Composable
private fun BoxScope.Scrim(top: Boolean, height: Dp) {
    val base = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    if (top) listOf(base.copy(alpha = 0.72f), base.copy(alpha = 0.28f), Color.Transparent)
                    else listOf(Color.Transparent, base.copy(alpha = 0.28f), base.copy(alpha = 0.72f)),
                ),
            ),
    )
}

@Composable
private fun HomePage(engine: SearchEngine, shortcutsOn: Boolean, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..10 -> "早上好"; in 11..12 -> "中午好"; in 13..17 -> "下午好"; else -> "晚上好" }
    val shortcuts = remember {
        listOf(
            "必应" to "https://www.bing.com",
            "百度" to "https://www.baidu.com",
            "知乎" to "https://www.zhihu.com",
            "哔哩哔哩" to "https://www.bilibili.com",
            "GitHub" to "https://github.com",
            "维基百科" to "https://zh.wikipedia.org",
            "微博" to "https://m.weibo.cn",
            "Google" to "https://www.google.com",
        )
    }
    var query by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Text(greeting, style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        Text("今天想去哪里看看？", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("用${engine.label}搜索", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSubmit(query) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (shortcutsOn) {
            Spacer(Modifier.height(28.dp))
            LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(280.dp)) {
                itemsIndexed(shortcuts) { _, s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSubmit(s.second) }.padding(4.dp)) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Text(s.first.take(1), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(s.first, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabSwitcher(
    controller: BrowserController,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onNewIncognito: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("标签页") },
                subtitle = { Text("共 ${controller.tabs.size} 个") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = onNewIncognito) { Icon(Icons.Rounded.VisibilityOff, "新建无痕标签") }
                    TextButton(onClick = { controller.closeAll(); onBack() }) { Text("全部关闭") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("新标签页") })
        },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad),
        ) {
            itemsIndexed(controller.tabs, key = { _, t -> t.id }) { i, t ->
                val selected = i == controller.currentIndex
                val container = when {
                    t.incognito -> Color(0xFF1C1C22)
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val on = if (t.incognito) Color(0xFFF2F2F2) else if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                Card(
                    onClick = { onSelect(i) },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = container, contentColor = on),
                    modifier = Modifier.animateItem().aspectRatio(0.72f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 2.dp, top = 4.dp)) {
                        if (t.incognito) Icon(Icons.Rounded.VisibilityOff, null, Modifier.padding(end = 4.dp).size(14.dp), tint = on)
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), color = on)
                        IconButton(onClick = { controller.close(t) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "关闭", Modifier.size(18.dp), tint = on) }
                    }
                    Box(Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(22.dp)).background(if (t.incognito) Color(0xFF111114) else MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        val thumb = t.thumbnail
                        if (!t.isHome && thumb != null && !t.incognito) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                        else Icon(if (t.isHome) Icons.Rounded.Home else if (t.incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Public, null, tint = on)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageListSheet(
    title: String,
    entries: List<PageEntry>,
    emptyText: String,
    onOpen: (PageEntry) -> Unit,
    onDelete: (PageEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(horizontal = 24.dp))
        if (entries.isEmpty()) Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        LazyColumn(Modifier.fillMaxWidth().height(480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { it.url + it.folder }) { e ->
                ListItem(
                    headlineContent = { Text(e.title.ifBlank { e.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val host = UrlUtils.displayHost(e.url)
                        val extra = if (e.folder.isNotBlank()) "${e.folder} · $host" else host
                        Text(extra, maxLines = 1)
                    },
                    trailingContent = { IconButton(onClick = { onDelete(e) }) { Icon(Icons.Rounded.Close, "删除") } },
                    modifier = Modifier.clickable { onOpen(e) },
                )
            }
        }
    }
}

private fun sharePage(context: android.content.Context, tab: BrowserTab) {
    val url = tab.url
    if (tab.isHome || !(url.startsWith("http://") || url.startsWith("https://"))) {
        android.widget.Toast.makeText(context, "当前没有可分享的网页", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val title = tab.title.takeIf { it.isNotBlank() && it != "新标签页" && it != "无痕标签页" }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, if (title != null) "$title\n$url" else url)
    if (title != null) send.putExtra(Intent.EXTRA_SUBJECT, title)
    val chooser = Intent.createChooser(send, "分享网页")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(chooser) } catch (e: Exception) {
        android.widget.Toast.makeText(context, "无法分享：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
