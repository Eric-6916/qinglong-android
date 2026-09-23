package com.qinglong.panel.ui.cron

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.Cron
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.component.StatusPill
import com.qinglong.panel.ui.theme.LocalAuroraColors
import com.qinglong.panel.ui.util.formatTs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronListScreen(
    container: AppContainer,
    onEditCron: (Long?) -> Unit,
    onOpenLog: (Long, String) -> Unit,
) {
    val vm: CronViewModel = viewModel(factory = CronViewModel.factory(container.repository))
    val state = vm.state

    // 从新增/编辑子页面返回时自动刷新：列表 VM 随返回栈存活，init 不会重跑，
    // 过去必须切换一次底部页签才能看到新数据。repeatOnLifecycle 在生命周期
    // 已处于 RESUMED 时也会立即执行，因此每次返回本页必触发一次 load()。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.load()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Cron?>(null) }

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
                title = { Text("定时任务") },
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
                onClick = { onEditCron(null) },
                // v1.2.0：上移 24dp 避开底部导航岛，并改为主色实心增强存在感
                modifier = Modifier.padding(bottom = 24.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建任务") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("搜索名称 / 命令") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = vm::clearQuery) {
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
                state.items.isEmpty() -> EmptyHint("暂无任务，点击右下角新建")
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { cron ->
                        CronCard(
                            cron = cron,
                            busy = state.actionInFlight,
                            onClick = { onEditCron(cron.id) },
                            onRun = { vm.run(listOf(cron.id)) },
                            onStop = { vm.stop(listOf(cron.id)) },
                            onEnable = { vm.enable(listOf(cron.id)) },
                            onDisable = { vm.disable(listOf(cron.id)) },
                            onLog = { onOpenLog(cron.id, cron.displayName) },
                            onDelete = { pendingDelete = cron },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { cron ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除任务") },
            text = { Text("确认删除「${cron.displayName}」？该操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(listOf(cron.id))
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CronCard(
    cron: Cron,
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
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cron.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = cron.schedule?.ifBlank { "-" } ?: "-",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (cron.isPinned == 1) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "置顶",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                StatusPill(
                    text = when {
                        cron.running && !cron.enabled -> "运行中"
                        cron.running -> "运行中"
                        !cron.enabled -> "已禁用"
                        else -> "空闲"
                    },
                    color = when {
                        cron.running -> LocalAuroraColors.current.status.running
                        // v1.2.1：禁用=红（原灰 #566475）；空闲改用专用中性色
                        // （原 outline 在浅色主题下几乎不可见）
                        !cron.enabled -> LocalAuroraColors.current.status.disabled
                        else -> LocalAuroraColors.current.status.idle
                    },
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                        DropdownMenuItem(
                            text = { Text(if (cron.enabled) "禁用" else "启用") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                            enabled = !busy,
                            onClick = { menuOpen = false; if (cron.enabled) onDisable() else onEnable() },
                        )
                        DropdownMenuItem(
                            text = { Text("查看日志") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuOpen = false; onLog() },
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = cron.command,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (cron.lastExecutionTime > 0 || cron.lastRunningTime > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "上次执行：${formatTs(
                        if (cron.lastExecutionTime > 0) cron.lastExecutionTime else cron.lastRunningTime
                    )}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
