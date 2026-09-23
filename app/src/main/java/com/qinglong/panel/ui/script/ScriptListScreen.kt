package com.qinglong.panel.ui.script

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.ScriptFile
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    container: AppContainer,
    onOpenDetail: (path: String, file: String) -> Unit,
    onOpenUpload: (path: String) -> Unit,
) {
    val vm: ScriptViewModel = viewModel(factory = ScriptViewModel.factory(container.repository))
    val state = vm.state

    // 从上传/详情/新建目录等子页面返回时自动刷新当前目录（VM 随返回栈存活）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.load()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDelete by remember { mutableStateOf<ScriptFile?>(null) }
    var renameTarget by remember { mutableStateOf<ScriptFile?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = {
                    Column {
                        Text("脚本")
                        Spacer(Modifier.height(2.dp))
                        // 当前路径面包屑，根目录显示「根目录」
                        val path = state.currentPath
                        if (path.isBlank()) {
                            Text(
                                text = "根目录",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val segments = path.split("/")
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BreadcrumbItem("根目录") { vm.navigateTo("") }
                                segments.forEachIndexed { index, seg ->
                                    Text(
                                        text = " / ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    val prefix = segments.take(index + 1).joinToString("/")
                                    BreadcrumbItem(seg) { vm.navigateTo(prefix) }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (state.currentPath.isNotBlank()) {
                        IconButton(onClick = { vm.navigateUp() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回上级")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenUpload(state.currentPath) },
                // v1.2.0：上移 24dp 避开底部导航岛，并改为主色实心增强存在感
                modifier = Modifier.padding(bottom = 24.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("上传/新建") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchChange,
                placeholder = { Text("搜索文件名") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = vm::clearSearch) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            // 本地过滤：按文件名包含关键字
            val q = state.searchQuery.trim().lowercase()
            val visible = if (q.isBlank()) state.entries else
                state.entries.filter { it.title.lowercase().contains(q) }

            when {
                state.loading -> LoadingBox()
                state.error != null && state.entries.isEmpty() ->
                    StateBox(message = state.error, onRetry = { vm.load() })
                visible.isEmpty() -> EmptyHint(
                    if (state.searchQuery.isBlank()) "当前目录为空" else "没有匹配的文件",
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { "${it.type}:${it.parent}/${it.title}" }) { entry ->
                        ScriptRow(
                            entry = entry,
                            busy = state.actionInFlight,
                            onClick = {
                                if (entry.isDirectory) vm.navigateInto(entry.key)
                                else onOpenDetail(state.currentPath, entry.title)
                            },
                            onRun = { vm.run(entry.title, state.currentPath, after = { vm.load() }) },
                            onStop = { vm.stop(entry.title, state.currentPath, after = { vm.load() }) },
                            onRename = {
                                renameTarget = entry
                                renameText = entry.title
                            },
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }

    // 删除二次确认
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = if (entry.isDirectory) "删除目录" else "删除文件",
            text = if (entry.isDirectory)
                "确认删除目录「${entry.title}」及其全部内容？该操作不可恢复。"
            else
                "确认删除文件「${entry.title}」？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                vm.delete(
                    entry.title,
                    state.currentPath,
                    if (entry.isDirectory) "directory" else "file",
                    after = { vm.load() },
                )
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // 重命名输入对话框
    renameTarget?.let { entry ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameText.trim()
                        if (newName.isNotBlank() && newName != entry.title) {
                            vm.rename(entry.title, state.currentPath, newName, after = { vm.load() })
                        }
                        renameTarget = null
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BreadcrumbItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScriptRow(
    entry: ScriptFile,
    busy: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.isDirectory && entry.size > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatBytes(entry.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            // 目录无运行/停止菜单项
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("运行") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                            enabled = !busy,
                            onClick = { menuOpen = false; onRun() },
                        )
                        DropdownMenuItem(
                            text = { Text("停止") },
                            leadingIcon = { Icon(Icons.Filled.Stop, null) },
                            enabled = !busy,
                            onClick = { menuOpen = false; onStop() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        enabled = !busy,
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
