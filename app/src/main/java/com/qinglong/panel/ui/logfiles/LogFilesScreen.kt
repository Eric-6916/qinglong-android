package com.qinglong.panel.ui.logfiles

import androidx.compose.ui.graphics.Color

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.LogChunk
import com.qinglong.panel.data.model.ScriptFile
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.theme.LogSurfaceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 日志目录：两级浏览（顶层目录 → 子文件）。
 * 点击日志文件在本 composable 内打开 [LogView] 查看器，不跳出本界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFilesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: LogFileViewModel = viewModel(factory = LogFileViewModel.factory(container.repository))
    val state = vm.state
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 已展开的目录 key 集合
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 正在查看的日志（目录路径, 文件）
    var viewing by remember { mutableStateOf<Pair<String, ScriptFile>?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<String, ScriptFile>?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // 查看日志：用 LogView 复用，loader 取 logDetail；日志面极光渐变底跟随全局主题（v1.0.6 起）
    viewing?.let { (dirPath, file) ->
        LogSurfaceTheme {
            LogView(
                title = file.title,
                loader = { container.repository.logDetail(path = dirPath, file = file.title) },
                onBack = { viewing = null },
            )
        }
        return
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text("日志目录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null && state.tree.isEmpty() ->
                    StateBox(message = state.error, onRetry = { vm.load() })
                state.tree.isEmpty() -> EmptyHint("暂无日志目录")
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.tree.forEach { dir ->
                        DirCard(
                            dir = dir,
                            expanded = expanded.contains(dir.key),
                            onToggle = {
                                expanded = if (expanded.contains(dir.key)) {
                                    expanded - dir.key
                                } else {
                                    expanded + dir.key
                                }
                            },
                            onOpenFile = { file -> viewing = (dir.key.ifBlank { dir.title }) to file },
                            onDeleteFile = { file -> pendingDelete = (dir.key.ifBlank { dir.title }) to file },
                        )
                    }
                }
            }
        }
    }

    // 删除日志确认
    pendingDelete?.let { (dirPath, file) ->
        ConfirmDialog(
            title = "删除日志",
            text = "确认删除日志「${file.title}」？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                scope.launch {
                    container.repository.deleteLog(filename = file.title, path = dirPath, type = "file")
                        .fold(
                            onSuccess = {
                                snackbarHostState.showSnackbar("已删除")
                                vm.load()
                            },
                            onFailure = { e -> snackbarHostState.showSnackbar(QinglongRepository.errorMessage(e)) },
                        )
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun DirCard(
    dir: ScriptFile,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenFile: (ScriptFile) -> Unit,
    onDeleteFile: (ScriptFile) -> Unit,
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = dir.title.ifBlank { dir.key }, style = MaterialTheme.typography.titleMedium)
                    val count = dir.children?.size ?: 0
                    Text(
                        text = "$count 个日志文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                )
            }

            if (expanded) {
                val children = dir.children ?: emptyList()
                if (children.isEmpty()) {
                    Text(
                        text = "（暂无日志文件）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    )
                } else {
                    children.forEach { file ->
                        FileRow(
                            file = file,
                            onOpen = { onOpenFile(file) },
                            onDelete = { onDeleteFile(file) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: ScriptFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 40.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = file.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "操作")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

// ============================================================
// 通用日志查看器（修正版，可在日志文件 / 系统日志场景复用）
// ============================================================

/**
 * 日志查看器：进入即自动轮询（默认 1 秒，尽可能实时同步），支持暂停/恢复、尾随滚动、一键复制。
 * loader 返回原始日志内容；失败时显示错误并保留旧内容。
 *
 * @param autoRefreshIntervalMs 自动刷新间隔（毫秒），默认 1000
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogView(
    title: String,
    loader: suspend () -> Result<LogChunk>,
    onBack: () -> Unit,
    autoRefreshIntervalMs: Long = 1000L,
) {
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // 默认开启自动刷新，尽可能实时同步日志
    var autoRefresh by remember { mutableStateOf(true) }
    // 用户停在日志底部时跟随尾部；向上翻看历史时自动暂停跟随
    var followTail by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loader().fold(
            onSuccess = { chunk ->
                content = chunk.data.orEmpty()
                error = null
            },
            onFailure = { e -> error = QinglongRepository.errorMessage(e) },
        )
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            delay(autoRefreshIntervalMs)
            // 每次轮询前判断用户是否仍停在日志尾部
            followTail = scrollState.value >= scrollState.maxValue - 80
            refresh()
        }
    }
    LaunchedEffect(content) {
        if (followTail && content.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(content)) },
                        enabled = content.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                    }
                    IconButton(
                        onClick = { if (content.isNotEmpty()) scope.launch { loading = true; refresh() } },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (autoRefresh) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "自动刷新（${autoRefreshIntervalMs / 1000} 秒）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoRefresh, onCheckedChange = { autoRefresh = it })
            }
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                if (loading && content.isEmpty()) {
                    LoadingBox()
                } else if (content.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无日志内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = content,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
