package com.m3browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.m3browser.AppSettings
import com.m3browser.BackupIO
import com.m3browser.BookmarkHtml
import com.m3browser.BuildConfig
import com.m3browser.DeviceCode
import com.m3browser.GitHubSync
import com.m3browser.PageEntry
import com.m3browser.SEED_OPTIONS
import com.m3browser.SearchEngine
import com.m3browser.Store
import com.m3browser.TRANSLATE_LANGUAGES
import com.m3browser.ThemeMode
import com.m3browser.TokenStore
import com.m3browser.languageLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    store: Store,
    settings: AppSettings,
    tokens: TokenStore,
    sync: GitHubSync,
    onBack: () -> Unit,
    onToolbar: () -> Unit,
    onCheckUpdate: () -> Unit,
    onAuthChanged: () -> Unit = {},
    onClearData: (cache: Boolean, cookies: Boolean, history: Boolean) -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var clearCache by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf<DeviceCode?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf<ImageBitmap?>(null) }
    val loggedIn = tokens.token != null

    LaunchedEffect(tokens.avatarUrl, loggedIn) {
        val url = tokens.avatarUrl
        avatar = if (loggedIn && !url.isNullOrBlank()) withContext(Dispatchers.IO) {
            runCatching {
                val bytes = java.net.URL(url).openStream().use { it.readBytes() }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }

    val importHtml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readText(context, uri) ?: return@launch onMessage("无法读取文件")
            val items = BookmarkHtml.parse(text)
            if (items.isEmpty()) return@launch onMessage("没有找到可导入的书签")
            val (added, skipped) = store.importBookmarks(items)
            onMessage("已导入 $added 条书签" + if (skipped > 0) "，跳过 $skipped 条重复" else "")
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readText(context, uri) ?: return@launch onMessage("无法读取文件")
            val bundle = BackupIO.parse(text) ?: return@launch onMessage("备份格式无法识别")
            val (bm, bmSkip) = store.importBookmarks(bundle.bookmarks)
            val (hi, _) = store.importHistory(bundle.history)
            bundle.settings?.let { store.updateSettings { _ -> it.copy(updatedAt = System.currentTimeMillis()) } }
            onMessage("已导入 $bm 条书签、$hi 条历史" + if (bmSkip > 0) "，跳过 $bmSkip 条重复书签" else "")
        }
    }
    val exportHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val items = store.snapshotRecords().second
            writeText(context, uri, BookmarkHtml.export(items))
            onMessage("书签已导出")
        }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (s, b, h) = store.snapshotRecords()
            writeText(context, uri, BackupIO.export(s, b, h))
            onMessage("备份已导出")
        }
    }

    fun edit(block: (AppSettings) -> AppSettings) { scope.launch { store.updateSettings(block) } }

    Scaffold(topBar = {
        LargeFlexibleTopAppBar(
            title = { Text("设置") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Section("外观") {
                Text("主题模式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Segmented(ThemeMode.entries, settings.theme, { it.label }) { edit { s -> s.copy(theme = it) } }
                SwitchRow("莫奈取色", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用壁纸的 Material You 配色" else "需要 Android 12 及以上，当前使用预设配色", settings.monet) {
                    edit { s -> s.copy(monet = it) }
                }
                if (!settings.monet || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Text("预设种子色", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        SEED_OPTIONS.forEach { seed ->
                            val selected = settings.seed == seed.argb
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(seed.argb.toInt()))
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable { edit { s -> s.copy(seed = seed.argb, monet = false) } },
                                contentAlignment = Alignment.Center,
                            ) {}
                        }
                    }
                }
                SwitchRow("纯黑深色", "仅在深色模式下把背景改为纯黑，适合 AMOLED", settings.amoled) { edit { s -> s.copy(amoled = it) } }
                SwitchRow("毛玻璃效果", "工具栏实时模糊网页，关闭后改为半透明底色", settings.blur) { edit { s -> s.copy(blur = it) } }
                Text("工具栏位置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))
                Segmented(listOf(false, true), settings.barAtTop, { if (it) "顶部" else "底部" }) { edit { s -> s.copy(barAtTop = it) } }
            }
            Section("工具栏") {
                Text("当前 ${settings.toolbar.size} 个按钮，地址栏和菜单始终显示。")
                TextButton(onClick = onToolbar) { Text("自定义工具栏") }
            }
            Section("浏览") {
                Text("搜索引擎", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Segmented(SearchEngine.entries, settings.engine, { it.label }) { edit { s -> s.copy(engine = it) } }
                SwitchRow("主页快捷方式", "在新标签页显示常用网站", settings.shortcuts) { edit { s -> s.copy(shortcuts = it) } }
                SwitchRow("启用 JavaScript", null, settings.javascript) { edit { s -> s.copy(javascript = it) } }
                SwitchRow("默认请求桌面版网站", "新打开的网页使用桌面版标识", settings.desktopDefault) { edit { s -> s.copy(desktopDefault = it) } }
                Text("文字缩放 ${settings.textZoom}%", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = settings.textZoom.toFloat(),
                    onValueChange = { v -> edit { s -> s.copy(textZoom = v.toInt()) } },
                    valueRange = 50f..200f,
                )
                SwitchRow("阻止第三方 Cookie", null, settings.blockThirdPartyCookies) { edit { s -> s.copy(blockThirdPartyCookies = it) } }
                SwitchRow("在新标签页中打开链接", "点击网页里的链接时新建标签", settings.openInNewTab) { edit { s -> s.copy(openInNewTab = it) } }
                TextButton(onClick = { showLang = true }) { Text("翻译目标语言：${languageLabel(settings.translateLang)}") }
            }
            Section("隐私") {
                SwitchRow("无痕浏览时禁止截屏", "有无痕标签页时启用 FLAG_SECURE", settings.flagSecure) { edit { s -> s.copy(flagSecure = it) } }
                TextButton(onClick = { showClear = true }) { Text("清除浏览数据") }
            }
            Section("导入 / 导出数据") {
                Text("可导入 Chrome、Edge、Firefox、Safari 导出的 Netscape 书签 HTML。不读取其他浏览器的私有数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { importHtml.launch(arrayOf("text/html", "text/*", "*/*")) }) { Text("导入书签 HTML") }
                TextButton(onClick = { exportHtml.launch("m3browser-bookmarks.html") }) { Text("导出书签 HTML") }
                TextButton(onClick = { exportJson.launch("m3browser-backup.json") }) { Text("导出 JSON 备份") }
                TextButton(onClick = { importJson.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("导入 JSON 备份") }
            }
            Section("同步") {
                if (!sync.configured()) {
                    Text("尚未配置 GitHub OAuth。请在 gradle.properties 填写 GITHUB_OAUTH_CLIENT_ID 后重新打包。这不是密钥，只是 OAuth 应用的 Client ID。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!loggedIn) {
                    Text("使用 GitHub 设备码登录，仅请求 gist 权限，把书签、设置和可选的历史记录同步到一个私密 Gist。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        scope.launch {
                            val code = runCatching { withContext(Dispatchers.IO) { sync.requestDeviceCode() } }
                            code.onSuccess { device = it }.onFailure { onMessage(it.message ?: "无法开始登录") }
                            code.getOrNull()?.let { started ->
                                launch {
                                    runCatching { sync.pollToken(started) }
                                        .onSuccess { device = null; onAuthChanged(); onMessage("已登录 GitHub") }
                                        .onFailure { device = null; onMessage(it.message ?: "登录失败") }
                                }
                            }
                        }
                    }) { Text("登录 GitHub") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (avatar != null) {
                            Image(avatar!!, null, Modifier.size(48.dp).clip(CircleShape))
                        } else {
                            Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                                Text(tokens.username?.take(1)?.uppercase() ?: "G", fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(tokens.username ?: "GitHub", style = MaterialTheme.typography.titleMedium)
                            val whenText = if (tokens.lastSync == 0L) "尚未同步" else "上次同步 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(tokens.lastSync))
                            Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (syncing) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    TextButton(enabled = !syncing, onClick = {
                        scope.launch {
                            syncing = true
                            val msg = runCatching { withContext(Dispatchers.IO) { sync.syncNow() } }.getOrElse { it.message ?: "同步失败" }
                            syncing = false
                            onMessage(msg)
                        }
                    }) { Text("立即同步") }
                    SwitchRow("自动同步", "启动、退出以及内容变化后同步", settings.autoSync) { edit { s -> s.copy(autoSync = it) } }
                    SwitchRow("同步历史记录", "无痕浏览的数据从不同步", settings.syncHistory) { edit { s -> s.copy(syncHistory = it) } }
                    TextButton(onClick = { tokens.clear(); onAuthChanged(); onMessage("已退出登录") }) { Text("退出登录") }
                }
            }
            Section("关于") {
                Text("M3 Browser ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text("versionCode ${BuildConfig.VERSION_CODE} · Material 3 Expressive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("自动检查更新", "每次打开应用时查询 GitHub Releases", settings.autoUpdateCheck) { edit { s -> s.copy(autoUpdateCheck = it) } }
                TextButton(onClick = onCheckUpdate) { Text("检查更新") }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showLang) {
        AlertDialog(
            onDismissRequest = { showLang = false },
            title = { Text("翻译目标语言") },
            text = {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    TRANSLATE_LANGUAGES.forEach { (code, label) ->
                        TextButton(onClick = { edit { s -> s.copy(translateLang = code) }; showLang = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (settings.translateLang == code) "$label  ✓" else label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text("关闭") } },
        )
    }
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清除浏览数据") },
            text = {
                Column {
                    CheckRow("缓存", clearCache) { clearCache = it }
                    CheckRow("Cookie", clearCookies) { clearCookies = it }
                    CheckRow("历史记录", clearHistory) { clearHistory = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearData(clearCache, clearCookies, clearHistory)
                    showClear = false
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } },
        )
    }
    device?.let { code ->
        AlertDialog(
            onDismissRequest = { device = null },
            title = { Text("登录 GitHub") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("在浏览器中打开 GitHub，输入下面的代码", textAlign = TextAlign.Center)
                    Text(code.userCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                    if (syncing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在等待授权…", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("GitHub", code.userCode))
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Rounded.ContentCopy, null); Text("复制并打开") }
            },
            dismissButton = { TextButton(onClick = { device = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(title)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val on = option == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { onSelect(option) },
            ) {
                Text(label(option), modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun readText(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

private fun writeText(context: android.content.Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}
