package com.qinglong.panel.ui.script

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptDetailScreen(
    container: AppContainer,
    path: String,
    file: String,
    onBack: () -> Unit,
) {
    val vm: ScriptViewModel = viewModel(factory = ScriptViewModel.factory(container.repository))
    val snackbarHostState = remember { SnackbarHostState() }

    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var notFound by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm.state.message) {
        vm.state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // 加载脚本内容
    LaunchedEffect(path, file) {
        loading = true
        notFound = false
        error = null
        vm.readDetail(path, file).fold(
            onSuccess = { text ->
                content = text
                loading = false
            },
            onFailure = { e ->
                loading = false
                error = QinglongRepository.errorMessage(e)
            },
        )
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(file) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 运行 / 停止
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            vm.saveContent(file, path, content)
                        },
                        enabled = !vm.state.actionInFlight,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("运行") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                            enabled = !vm.state.actionInFlight,
                            onClick = { menuOpen = false; vm.run(file, path) },
                        )
                        DropdownMenuItem(
                            text = { Text("停止") },
                            leadingIcon = { Icon(Icons.Filled.Stop, null) },
                            enabled = !vm.state.actionInFlight,
                            onClick = { menuOpen = false; vm.stop(file, path) },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menuOpen = false; showDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            loading -> LoadingBox(modifier = Modifier.fillMaxSize().padding(padding))
            notFound -> StateBox(
                message = "文件不存在或已被删除",
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = onBack,
            )
            error != null -> StateBox(
                message = error ?: "加载失败",
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = onBack,
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // 无内容时空态提示
                if (content.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(
                            text = "（文件为空，可直接编辑后保存）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                )
            }
        }
    }

    // 删除二次确认，成功后返回
    if (showDelete) {
        ConfirmDialog(
            title = "删除文件",
            text = "确认删除「$file」？该操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                showDelete = false
                vm.delete(file, path, "file", after = onBack)
            },
            onDismiss = { showDelete = false },
        )
    }
}
