@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.m3browser.ui

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.m3browser.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Screen { BROWSER, TABS, SETTINGS }
private enum class Sheet { NONE, BOOKMARKS, HISTORY }

@Composable
fun BrowserApp(controller: BrowserController, store: Store) {
    val scope = rememberCoroutineScope()
    val engine by store.engine.collectAsState(initial = SearchEngine.BING)
    val bookmarks by store.bookmarks.collectAsState(initial = emptyList())
    val history by store.history.collectAsState(initial = emptyList())
    var screen by remember { mutableStateOf(Screen.BROWSER) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    val tab = controller.current

    val open: (String) -> Unit = { input ->
        val url = UrlUtils.toUrl(input, engine)
        controller.current?.let { controller.load(it, url) }
        screen = Screen.BROWSER
    }

    BackHandler(enabled = screen != Screen.BROWSER || (tab != null && !tab.isHome)) {
        if (screen != Screen.BROWSER) screen = Screen.BROWSER
        else tab?.let { controller.back(it) }
    }

    val motion = MaterialTheme.motionScheme
    val fxIn = motion.defaultEffectsSpec<Float>()
    val spIn = motion.defaultSpatialSpec<Float>()
    val fxOut = motion.fastEffectsSpec<Float>()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(fxIn) + scaleIn(spIn, initialScale = 0.92f)) togetherWith fadeOut(fxOut)
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.BROWSER -> if (tab != null) BrowserScreen(
                    controller, tab, engine,
                    isBookmarked = bookmarks.any { it.url == tab.url },
                    onSubmit = open,
                    onShowTabs = { controller.captureThumb(tab); screen = Screen.TABS },
                    onToggleBookmark = { scope.launch { store.toggleBookmark(PageEntry(tab.title, tab.url)) } },
                    onBookmarks = { sheet = Sheet.BOOKMARKS },
                    onHistory = { sheet = Sheet.HISTORY },
                    onSettings = { screen = Screen.SETTINGS },
                )
                Screen.TABS -> TabSwitcher(controller,
                    onSelect = { controller.select(it); screen = Screen.BROWSER },
                    onNew = { controller.newTab(); screen = Screen.BROWSER },
                    onBack = { screen = Screen.BROWSER })
                Screen.SETTINGS -> SettingsScreen(store, onBack = { screen = Screen.BROWSER })
            }
        }
    }

    if (sheet != Sheet.NONE) {
        val isBm = sheet == Sheet.BOOKMARKS
        PageListSheet(
            title = if (isBm) "书签" else "历史记录",
            entries = if (isBm) bookmarks else history,
            emptyText = if (isBm) "还没有书签" else "暂无历史记录",
            onOpen = { sheet = Sheet.NONE; open(it.url) },
            onDelete = if (isBm) ({ e -> scope.launch { store.removeBookmark(e.url) } }) else null,
            onClear = if (!isBm) ({ scope.launch { store.clearHistory() } }) else null,
            onDismiss = { sheet = Sheet.NONE },
        )
    }
}

@Composable
private fun BrowserScreen(
    controller: BrowserController, tab: BrowserTab, engine: SearchEngine, isBookmarked: Boolean,
    onSubmit: (String) -> Unit, onShowTabs: () -> Unit, onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        AddressBar(tab, onSubmit,
            onReloadStop = { if (tab.loading) controller.stop(tab) else controller.reload(tab) })
        AnimatedVisibility(tab.loading && !tab.isHome) {
            val p by animateFloatAsState(tab.progress, spring(stiffness = Spring.StiffnessLow), label = "p")
            LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp))) {
            if (tab.isHome) HomePage(engine, onSubmit)
            else key(tab.id, tab.webView) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { fl ->
                        val wv = tab.webView
                        if (wv != null && wv.parent !== fl) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            fl.removeAllViews()
                            fl.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            HorizontalFloatingToolbar(expanded = true) {
                ToolIcon(Icons.AutoMirrored.Rounded.ArrowBack, "后退", enabled = !tab.isHome) { controller.back(tab) }
                ToolIcon(Icons.AutoMirrored.Rounded.ArrowForward, "前进", enabled = tab.canGoForward) { controller.forward(tab) }
                ToolIcon(Icons.Rounded.Home, "主页") { controller.goHome(tab) }
                TabsCountButton(controller.tabs.size, onShowTabs)
                Box {
                    ToolIcon(Icons.Rounded.MoreVert, "更多") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                        MenuRow("新标签页", Icons.Rounded.Add) { menu = false; controller.newTab() }
                        if (!tab.isHome) {
                            MenuRow(if (isBookmarked) "移除书签" else "添加书签",
                                if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder) { menu = false; onToggleBookmark() }
                        }
                        MenuRow("分享", Icons.Rounded.Share) {
                            menu = false
                            sharePage(context, tab)
                        }
                        MenuRow("书签", Icons.Rounded.Bookmarks) { menu = false; onBookmarks() }
                        MenuRow("历史记录", Icons.Rounded.History) { menu = false; onHistory() }
                        MenuRow("设置", Icons.Rounded.Settings) { menu = false; onSettings() }
                    }
                }
            }
        }
    }
}

private fun sharePage(context: android.content.Context, tab: BrowserTab) {
    val url = tab.url
    if (tab.isHome || !(url.startsWith("http://") || url.startsWith("https://"))) {
        android.widget.Toast.makeText(context, "首页没有可分享的网页", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val title = tab.title.takeIf { it.isNotBlank() && it != "新标签页" }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, if (title != null) "$title\n$url" else url)
    if (title != null) send.putExtra(Intent.EXTRA_SUBJECT, title)
    val chooser = Intent.createChooser(send, "分享网页")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "无法分享：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)

@Composable
private fun ToolIcon(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(icon, desc) }
}

@Composable
private fun TabsCountButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            AnimatedContent(count, label = "count") {
                Text(if (it > 99) ":D" else "$it", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun AddressBar(tab: BrowserTab, onSubmit: (String) -> Unit, onReloadStop: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var value by remember(tab.id) { mutableStateOf(TextFieldValue("")) }
    val focus = LocalFocusManager.current
    LaunchedEffect(focused, tab.url) {
        if (!focused) value = TextFieldValue(if (tab.isHome) "" else UrlUtils.displayHost(tab.url))
    }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "s")
    Surface(
        shape = CircleShape,
        color = if (focused) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(56.dp).scale(scale),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                when {
                    tab.loading && !tab.isHome -> LoadingIndicator(Modifier.size(32.dp))
                    tab.favicon != null && !focused -> Image(tab.favicon!!.asImageBitmap(), null, Modifier.size(20.dp).clip(CircleShape))
                    tab.url.startsWith("https") && !focused -> Icon(Icons.Rounded.Lock, "安全连接", Modifier.size(20.dp))
                    else -> Icon(Icons.Rounded.Search, null, Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.text.isEmpty()) Text("搜索或输入网址", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
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
                    },
                )
            }
            if (focused && value.text.isNotEmpty()) {
                IconButton(onClick = { value = TextFieldValue("") }) { Icon(Icons.Rounded.Close, "清除") }
            } else if (!tab.isHome) {
                IconButton(onClick = onReloadStop, shapes = IconButtonDefaults.shapes()) {
                    Icon(if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, if (tab.loading) "停止" else "刷新")
                }
            }
        }
    }
}

private data class Shortcut(val name: String, val url: String, val shape: androidx.graphics.shapes.RoundedPolygon)

@Composable
private fun HomePage(engine: SearchEngine, onSubmit: (String) -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..10 -> "早上好"; in 11..12 -> "中午好"; in 13..17 -> "下午好"; else -> "晚上好" }
    val shortcuts = remember {
        listOf(
            Shortcut("必应", "https://www.bing.com", MaterialShapes.Cookie9Sided),
            Shortcut("百度", "https://www.baidu.com", MaterialShapes.Clover4Leaf),
            Shortcut("知乎", "https://www.zhihu.com", MaterialShapes.Sunny),
            Shortcut("哔哩哔哩", "https://www.bilibili.com", MaterialShapes.SoftBurst),
            Shortcut("GitHub", "https://github.com", MaterialShapes.Cookie6Sided),
            Shortcut("维基百科", "https://zh.wikipedia.org", MaterialShapes.Pentagon),
            Shortcut("微博", "https://m.weibo.cn", MaterialShapes.Flower),
            Shortcut("Google", "https://www.google.com", MaterialShapes.Arch),
        )
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    var query by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(greeting, style = MaterialTheme.typography.displayMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        Text("今天想去哪里看看？", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("用${engine.label}搜索", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSubmit(query) }),
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(shortcuts) { i, s ->
                val (bg, fg) = colors[i % colors.size]
                var pressed by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSubmit(s.url) }.padding(4.dp)) {
                    Box(Modifier.size(60.dp).clip(s.shape.toShape()).background(bg), contentAlignment = Alignment.Center) {
                        Text(s.name.take(1), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TabSwitcher(controller: BrowserController, onSelect: (Int) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("标签页") },
                subtitle = { Text("共 ${controller.tabs.size} 个") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = { TextButton(onClick = { controller.closeAll(); onBack() }) { Text("全部关闭") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("新标签页") })
        },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad),
        ) {
            itemsIndexed(controller.tabs, key = { _, t -> t.id }) { i, t ->
                val selected = i == controller.currentIndex
                Card(
                    onClick = { onSelect(i) },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor =
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.animateItem().aspectRatio(0.72f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 2.dp, top = 4.dp)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { controller.close(t) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, "关闭", Modifier.size(18.dp))
                        }
                    }
                    Box(Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        val thumb = t.thumbnail
                        if (!t.isHome && thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                        else Box(Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                            Icon(if (t.isHome) Icons.Rounded.Home else Icons.Rounded.Public, null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageListSheet(
    title: String, entries: List<PageEntry>, emptyText: String, onOpen: (PageEntry) -> Unit,
    onDelete: ((PageEntry) -> Unit)?, onClear: (() -> Unit)?, onDismiss: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.weight(1f))
            if (onClear != null && entries.isNotEmpty()) TextButton(onClick = onClear) { Text("清空") }
        }
        if (entries.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { it.url + it.time }) { e ->
                ListItem(
                    headlineContent = { Text(e.title.ifBlank { e.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${UrlUtils.displayHost(e.url)} · ${fmt.format(Date(e.time))}", maxLines = 1) },
                    leadingContent = {
                        Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center) { Text(UrlUtils.displayHost(e.url).take(1).uppercase()) }
                    },
                    trailingContent = onDelete?.let { d -> { IconButton(onClick = { d(e) }) { Icon(Icons.Rounded.Delete, "删除") } } },
                    modifier = Modifier.animateItem().clickable { onOpen(e) },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: Store, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine by store.engine.collectAsState(initial = SearchEngine.BING)
    val theme by store.theme.collectAsState(initial = ThemeMode.SYSTEM)
    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text("设置") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            SettingGroup("搜索引擎", SearchEngine.entries, engine, { it.label }) { scope.launch { store.setEngine(it) } }
            Spacer(Modifier.height(24.dp))
            SettingGroup("主题模式", ThemeMode.entries, theme, { it.label }) { scope.launch { store.setTheme(it) } }
            Spacer(Modifier.height(24.dp))
            Text("Android 12 及以上自动使用壁纸取色（Material You）。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("M3 Browser 1.0 · Material 3 Expressive", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> SettingGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            ToggleButton(
                checked = o == selected, onCheckedChange = { onSelect(o) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) { Text(label(o), maxLines = 1) }
        }
    }
}
