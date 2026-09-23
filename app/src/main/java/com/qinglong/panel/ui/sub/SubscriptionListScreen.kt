package com.qinglong.panel.ui.sub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.Subscription
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.component.StatusPill
import com.qinglong.panel.ui.theme.LocalAuroraColors
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionListScreen(
    container: AppContainer,
    onEdit: (Long?) -> Unit,
    onOpenLog: (Long, String) -> Unit,
) {
    val vm: SubViewModel = viewModel(factory = SubViewModel.factory(container.repository))
    val state = vm.state

    // 从新增/编辑子页面返回时自动刷新（VM 随返回栈存活，init 不会重跑）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.load()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }

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
                title = { Text("订阅管理") },
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
                onClick = { onEdit(null) },
                // v1.2.0：上移 24dp 避开底部导航岛，并改为主色实心增强存在感
                modifier = Modifier.padding(bottom = 24.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新增订阅") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchChange,
                placeholder = { Text("搜索别名 / 名称 / 地址") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchChange("") }) {
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
                vm.filtered.isEmpty() -> EmptyHint(
                    if (state.searchQuery.isBlank()) "暂无订阅，点击右下角新增" else "未找到匹配的订阅",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.filtered, key = { it.id }) { sub ->
                        SubCard(
                            sub = sub,
                            busy = state.actionInFlight,
                            onClick = { onEdit(sub.id) },
                            onRun = { vm.run(listOf(sub.id)) },
                            onStop = { vm.stop(listOf(sub.id)) },
                            onEnable = { vm.enable(listOf(sub.id)) },
                            onDisable = { vm.disable(listOf(sub.id)) },
                            onLog = { onOpenLog(sub.id, sub.displayName) },
                            onDelete = { pendingDelete = sub },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { sub ->
        ConfirmDialog(
            title = "删除订阅",
            text = "确认删除「${sub.displayName}」？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                vm.delete(listOf(sub.id))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 订阅状态映射：0=运行中 1=空闲 2=禁用 3=排队 */
@Composable
private fun SubStatus(sub: Subscription): Pair<String, Color> {
    return when {
        sub.status == 2 || !sub.enabled -> "已禁用" to LocalAuroraColors.current.status.disabled
        sub.status == 0 -> "运行中" to LocalAuroraColors.current.status.running
        sub.status == 3 -> "排队" to LocalAuroraColors.current.status.queued
        // v1.2.1：空闲改用专用中性色（原 outline 浅色下几乎不可见）
        else -> "空闲" to LocalAuroraColors.current.status.idle
    }
}

@Composable
private fun SubCard(
    sub: Subscription,
    busy: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onLog: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (statusText, statusColor) = SubStatus(sub)
    val running = sub.status == 0 || sub.status == 3
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub.url ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(text = statusText, color = statusColor)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("运行") },
                        enabled = !busy && !running,
                        onClick = { menuOpen = false; onRun() },
                    )
                    DropdownMenuItem(
                        text = { Text("停止") },
                        enabled = !busy && running,
                        onClick = { menuOpen = false; onStop() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (sub.enabled) "禁用" else "启用") },
                        enabled = !busy,
                        onClick = { menuOpen = false; if (sub.enabled) onDisable() else onEnable() },
                    )
                    DropdownMenuItem(
                        text = { Text("查看日志") },
                        leadingIcon = { Icon(Icons.Filled.Description, null) },
                        onClick = { menuOpen = false; onLog() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}
