package com.m3browser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3browser.HistoryRange
import com.m3browser.PageEntry
import com.m3browser.Store
import com.m3browser.UrlUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    entries: List<PageEntry>,
    onOpen: (PageEntry) -> Unit,
    onDelete: (PageEntry) -> Unit,
    onClear: (HistoryRange) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PageEntry?>(null) }
    val filtered = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries else entries.filter {
            it.title.contains(q, true) || it.url.contains(q, true)
        }
    }
    val groups = remember(filtered) { groupHistory(filtered) }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("历史记录") },
                subtitle = { Text(if (filtered.isEmpty()) "没有记录" else "共 ${filtered.size} 条") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Delete, "清除") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                        DropdownMenuItem(text = { Text("最近一小时") }, onClick = { menu = false; onClear(HistoryRange.HOUR) })
                        DropdownMenuItem(text = { Text("今天") }, onClick = { menu = false; onClear(HistoryRange.TODAY) })
                        DropdownMenuItem(text = { Text("全部") }, onClick = { menu = false; onClear(HistoryRange.ALL) })
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("搜索标题或网址") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除") } }
                } else null,
                shape = RoundedCornerShape(28.dp),
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                        Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    groups.forEach { (label, list) ->
                        item(key = "h-$label") {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                        items(list, key = { it.url + it.time }) { entry ->
                            HistoryRow(entry, onOpen = { onOpen(entry) }, onDelete = { pending = entry })
                        }
                    }
                }
            }
        }
    }
    pending?.let { entry ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("删除这条记录？") },
            text = { Text(entry.title.ifBlank { entry.url }, maxLines = 2) },
            confirmButton = { TextButton(onClick = { onDelete(entry); pending = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(entry: PageEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                false
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = onOpen, onLongClick = onDelete)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(UrlUtils.displayHost(entry.url).take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(UrlUtils.displayHost(entry.url), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "删除") }
        }
    }
}

private fun groupHistory(entries: List<PageEntry>): List<Pair<String, List<PageEntry>>> {
    val now = System.currentTimeMillis()
    val today = Store.startOfToday(now)
    val yesterday = today - 24L * 60 * 60 * 1000
    val fmt = SimpleDateFormat("M月d日", Locale.CHINA)
    val buckets = linkedMapOf<String, MutableList<PageEntry>>()
    entries.sortedByDescending { it.time }.forEach { e ->
        val label = when {
            e.time >= today -> "今天"
            e.time >= yesterday -> "昨天"
            else -> fmt.format(Date(e.time))
        }
        buckets.getOrPut(label) { mutableListOf() }.add(e)
    }
    return buckets.map { it.key to it.value }
}
