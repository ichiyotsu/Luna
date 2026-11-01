package com.m3browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.m3browser.ToolbarAction
import com.m3browser.sanitizeToolbar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolbarEditor(
    actions: List<ToolbarAction>,
    onChange: (List<ToolbarAction>) -> Unit,
    onBack: () -> Unit,
) {
    val current = sanitizeToolbar(actions)
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("自定义工具栏") },
                subtitle = { Text("最多 ${ToolbarAction.MAX_SLOTS} 个，地址栏和菜单始终保留") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    TextButton(onClick = { onChange(ToolbarAction.DEFAULT) }) {
                        Icon(Icons.Rounded.RestartAlt, null)
                        Text("重置")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("预览", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text("example.com", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), maxLines = 1)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        current.forEach { action ->
                            Icon(iconFor(action), action.label, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Icon(Icons.Rounded.MoreVert, "菜单")
                    }
                }
            }
            Text("已添加", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (current.isEmpty()) Text("工具栏只保留地址栏和菜单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            current.forEach { action ->
                DragRow(action, current, onChange)
            }
            Text("可添加", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            ToolbarAction.CATALOG.filter { it !in current }.forEach { action ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconFor(action), null, modifier = Modifier.padding(8.dp))
                        Text(action.label, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { if (current.size < ToolbarAction.MAX_SLOTS) onChange(current + action) },
                            enabled = current.size < ToolbarAction.MAX_SLOTS,
                        ) { Icon(Icons.Rounded.Add, "添加") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragRow(action: ToolbarAction, actions: List<ToolbarAction>, onChange: (List<ToolbarAction>) -> Unit) {
    val latest by rememberUpdatedState(actions)
    val change by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val itemPx = with(density) { 56.dp.toPx() }
    var dragY by remember(action) { mutableFloatStateOf(0f) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .zIndex(if (dragY != 0f) 1f else 0f)
            .graphicsLayer { translationY = dragY },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .pointerInput(action) {
                        detectDragGestures(
                            onDragStart = { dragY = 0f },
                            onDragCancel = { dragY = 0f },
                            onDragEnd = { dragY = 0f },
                            onDrag = { changeEvent, amount ->
                                changeEvent.consume()
                                dragY += amount.y
                                val list = latest
                                val from = list.indexOf(action)
                                if (from < 0) return@detectDragGestures
                                val steps = (dragY / itemPx).toInt()
                                if (steps != 0) {
                                    val to = (from + steps).coerceIn(0, list.lastIndex)
                                    if (to != from) {
                                        val next = list.toMutableList().apply { add(to, removeAt(from)) }
                                        change(next)
                                        dragY -= (to - from) * itemPx
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.DragHandle, "拖动排序") }
            Icon(iconFor(action), null)
            Spacer(Modifier.width(8.dp))
            Text(action.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onChange(latest.filter { it != action }) }) { Icon(Icons.Rounded.Close, "移除") }
        }
    }
}

@Composable
internal fun iconFor(action: ToolbarAction) = when (action) {
    ToolbarAction.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
    ToolbarAction.FORWARD -> Icons.AutoMirrored.Rounded.ArrowForward
    ToolbarAction.HOME -> Icons.Rounded.Home
    ToolbarAction.RELOAD -> Icons.Rounded.Refresh
    ToolbarAction.TABS -> Icons.Rounded.Tab
    ToolbarAction.SHARE -> Icons.Rounded.Share
    ToolbarAction.TRANSLATE -> Icons.Rounded.Translate
    ToolbarAction.BOOKMARKS -> Icons.Rounded.Bookmarks
    ToolbarAction.ADD_BOOKMARK -> Icons.Rounded.BookmarkAdd
    ToolbarAction.HISTORY -> Icons.Rounded.History
    ToolbarAction.INCOGNITO -> Icons.Rounded.VisibilityOff
    ToolbarAction.NEW_TAB -> Icons.Rounded.Add
    ToolbarAction.DESKTOP -> Icons.Rounded.DesktopWindows
    ToolbarAction.FIND -> Icons.Rounded.ManageSearch
    ToolbarAction.SETTINGS -> Icons.Rounded.Settings
    ToolbarAction.MENU -> Icons.Rounded.MoreVert
}
