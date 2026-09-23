package com.qinglong.panel.ui.env

import androidx.compose.ui.graphics.Color

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.Env
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.component.StatusPill
import com.qinglong.panel.ui.theme.LocalAuroraColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvListScreen(
    container: AppContainer,
    onEdit: (Long?) -> Unit,
) {
    val vm: EnvViewModel = viewModel(factory = EnvViewModel.factory(container.repository))
    val state = vm.state

    // 从新增/编辑子页面返回时自动刷新（VM 随返回栈存活，init 不会重跑）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.load()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Env?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 选择本地 JSON 文件后读取为字节数组上传
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取文件")
                fileNameFromUri(context, uri) to bytes
            }.onSuccess { (name, bytes) -> vm.uploadEnvJson(name, bytes) }
                .onFailure { e -> vm.reportError("读取文件失败：${e.message ?: "未知错误"}") }
        }
    }

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
                title = { Text("环境变量") },
                actions = {
                    if (state.items.isNotEmpty()) {
                        Text(
                            text = "共 ${state.items.size} 个",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    IconButton(onClick = { picker.launch("*/*") }) {
                        Icon(Icons.Filled.Upload, contentDescription = "上传 JSON")
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
                text = { Text("新增变量") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchChange,
                placeholder = { Text("搜索名称 / 值 / 备注") },
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
                    if (state.searchQuery.isBlank()) "暂无环境变量，点击右下角新增" else "未找到匹配的环境变量",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.filtered, key = { it.id }) { env ->
                        EnvCard(
                            env = env,
                            busy = state.actionInFlight,
                            onClick = { onEdit(env.id) },
                            onEnable = { vm.enable(listOf(env.id)) },
                            onDisable = { vm.disable(listOf(env.id)) },
                            onPin = { vm.pin(listOf(env.id)) },
                            onUnpin = { vm.unpin(listOf(env.id)) },
                            onDelete = { pendingDelete = env },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { env ->
        ConfirmDialog(
            title = "删除环境变量",
            text = "确认删除「${env.name ?: "(未命名)"}」？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                vm.delete(listOf(env.id))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun EnvCard(
    env: Env,
    busy: Boolean,
    onClick: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
                    text = env.name ?: "(未命名)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = env.value ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!env.remarks.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = env.remarks,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = if (env.enabled) "正常" else "禁用",
                color = if (env.enabled) {
                    LocalAuroraColors.current.status.running
                } else {
                    LocalAuroraColors.current.status.disabled
                },
            )
            if (env.isPinned == 1) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "置顶",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (env.enabled) "禁用" else "启用") },
                        enabled = !busy,
                        onClick = { menuOpen = false; if (env.enabled) onDisable() else onEnable() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (env.isPinned == 1) "取消置顶" else "置顶") },
                        enabled = !busy,
                        onClick = { menuOpen = false; if (env.isPinned == 1) onUnpin() else onPin() },
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

/** 从 content:// Uri 解析展示文件名（用于上传） */
private fun fileNameFromUri(context: Context, uri: Uri): String {
    var name = "env.json"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.let { name = it }
            }
        }
    }
    return name
}
