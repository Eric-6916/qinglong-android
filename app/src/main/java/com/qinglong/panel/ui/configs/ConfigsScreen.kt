package com.qinglong.panel.ui.configs

import androidx.compose.ui.graphics.Color

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.ConfigFile
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import kotlinx.coroutines.launch

/**
 * 配置文件：列表浏览 + 在线编辑。
 * 列表点击进入编辑界面（同一 composable 内维护导航状态）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigsScreen(container: AppContainer, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<ConfigFile?>(null) }

    if (editing != null) {
        ConfigEditor(
            container = container,
            file = editing!!,
            onBack = { editing = null },
        )
    } else {
        ConfigList(
            container = container,
            onBack = onBack,
            onOpen = { editing = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigList(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (ConfigFile) -> Unit,
) {
    val vm: ConfigViewModel = viewModel(factory = ConfigViewModel.factory(container.repository))
    val state = vm.state
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("配置文件") },
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
                state.error != null && state.files.isEmpty() ->
                    StateBox(message = state.error, onRetry = { vm.load() })
                state.files.isEmpty() -> EmptyHint("暂无配置文件")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.files, key = { it.value }) { file ->
                        Card(
                            onClick = { onOpen(file) },
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
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.title.ifBlank { file.value },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = file.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigEditor(
    container: AppContainer,
    file: ConfigFile,
    onBack: () -> Unit,
) {
    val repo = container.repository
    val snackbarHostState = remember { SnackbarHostState() }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 加载文件内容（path 取 value 字段，即 /ql/config/xxx 形式的路径）
    LaunchedEffect(file.value) {
        repo.configDetail(file.value).fold(
            onSuccess = { content = it; loading = false },
            onFailure = { e -> error = QinglongRepository.errorMessage(e); loading = false },
        )
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(file.title.ifBlank { file.value }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !saving && !loading,
                        onClick = {
                            if (saving) return@IconButton
                            saving = true
                            scope.launch {
                                repo.saveConfig(name = file.value, content = content).fold(
                                    onSuccess = {
                                        saving = false
                                        snackbarHostState.showSnackbar("已保存")
                                    },
                                    onFailure = { e ->
                                        saving = false
                                        snackbarHostState.showSnackbar(QinglongRepository.errorMessage(e))
                                    },
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "保存")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // imePadding：软键盘升起时收缩编辑区，光标行不被输入法遮挡
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        decorationBox = { inner ->
                            if (content.isEmpty()) {
                                Text(
                                    text = "（空文件）",
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
        }
    }
}
