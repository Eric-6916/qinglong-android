package com.qinglong.panel.ui.dependence

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.Dependence
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.component.StatusPill
import com.qinglong.panel.ui.theme.LocalAuroraColors
import kotlinx.coroutines.launch

/**
 * 依赖管理：列表展示 / 搜索 / 安装 / 重新安装 / 取消 / 卸载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependenceScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DependenceViewModel = viewModel(factory = DependenceViewModel.factory(container.repository))
    val state = vm.state
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Dependence?>(null) }
    var showInstall by remember { mutableStateOf(false) }

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
                title = { Text("依赖管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.items.isNotEmpty()) {
                        Text(
                            text = "共 ${state.items.size} 个",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInstall = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("安装依赖") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("搜索依赖名称") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            when {
                state.loading -> LoadingBox()
                state.error != null && state.items.isEmpty() ->
                    StateBox(message = state.error, onRetry = { vm.load() })
                vm.filtered.isEmpty() -> EmptyHint("暂无依赖，点击右下角安装")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.filtered, key = { it.id }) { dep ->
                        DepCard(
                            dep = dep,
                            busy = state.actionInFlight,
                            onReinstall = { vm.reinstall(listOf(dep.id)) },
                            onCancel = { vm.cancel(listOf(dep.id)) },
                            onDelete = { pendingDelete = dep },
                        )
                    }
                }
            }
        }
    }

    // 卸载确认
    pendingDelete?.let { dep ->
        ConfirmDialog(
            title = "卸载依赖",
            text = "确认卸载「${dep.name}」？该操作不可恢复。",
            confirmText = "卸载",
            onConfirm = {
                vm.delete(listOf(dep.id))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // 安装依赖对话框
    if (showInstall) {
        InstallDialog(
            onDismiss = { showInstall = false },
            onConfirm = { names, type -> vm.install(names, type); showInstall = false },
        )
    }
}

@Composable
private fun DepCard(
    dep: Dependence,
    busy: Boolean,
    onReinstall: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 安装中(0) / 排队(6) 才允许取消
    val canCancel = dep.status == 0 || dep.status == 6
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dep.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = depTypeText(dep.type),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusPill(
                            text = depStatusText(dep.status),
                            color = depStatusColor(dep.status),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重新安装") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            enabled = !busy,
                            onClick = { menuOpen = false; onReinstall() },
                        )
                        DropdownMenuItem(
                            text = { Text("取消") },
                            leadingIcon = { Icon(Icons.Filled.Close, null) },
                            enabled = !busy && canCancel,
                            onClick = { menuOpen = false; onCancel() },
                        )
                        DropdownMenuItem(
                            text = { Text("卸载") },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            enabled = !busy,
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            dep.remark?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InstallDialog(
    onDismiss: () -> Unit,
    onConfirm: (names: String, type: Int) -> Unit,
) {
    var names by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(0) }
    val types = listOf("nodejs" to 0, "python3" to 1, "linux" to 2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装依赖") },
        text = {
            Column {
                Text("每行输入一个依赖名称（也可使用逗号分隔）：", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = names,
                    onValueChange = { names = it },
                    placeholder = { Text("例如：\npnpm\nrequests") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("依赖类型：", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { (label, idx) ->
                        OutlinedButton(
                            onClick = { type = idx },
                            colors = if (type == idx) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(names, type) },
                enabled = names.trim().isNotEmpty(),
            ) {
                Text("安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 依赖类型 → 文案（0=nodejs 1=python3 2=linux） */
private fun depTypeText(t: Int): String = when (t) {
    0 -> "nodejs"
    1 -> "python3"
    2 -> "linux"
    else -> "未知"
}

/** 依赖状态 → 文案 */
private fun depStatusText(s: Int): String = when (s) {
    0 -> "安装中"
    1 -> "已安装"
    2 -> "安装失败"
    3 -> "卸载中"
    4 -> "已卸载"
    5 -> "卸载失败"
    6 -> "排队中"
    7 -> "已取消"
    else -> "未知"
}

/** 依赖状态 → 语义色（随主题切换深浅档）
 *  v1.2.1：已卸载/已取消/未知等非问题终态由 disabled(灰→红) 改映射 idle 中性色 */
@Composable
private fun depStatusColor(s: Int): Color {
    val status = LocalAuroraColors.current.status
    return when (s) {
        0 -> status.queued
        1 -> status.running
        2 -> status.failed
        3 -> status.queued
        4 -> status.idle
        5 -> status.failed
        6 -> status.queued
        7 -> status.idle
        else -> status.idle
    }
}
