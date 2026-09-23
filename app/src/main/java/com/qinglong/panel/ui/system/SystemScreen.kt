package com.qinglong.panel.ui.system

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonElement
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.InfoRow
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.SectionCard
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.util.formatTs
import kotlinx.coroutines.launch

/**
 * 系统信息 + 系统配置表单。
 * 每项可单独保存，也可点底部「保存全部」一次性提交。
 * 数字字段（logRemoveFrequency / cronConcurrency）以 Long 提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SystemViewModel = viewModel(factory = SystemViewModel.factory(container.repository))
    val state = vm.state
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 可编辑草稿（初始化自系统配置）
    var draft by remember {
        mutableStateOf(
            SysDraft(
                logRemoveFrequency = "",
                cronConcurrency = "",
                dependenceProxy = "",
                nodeMirror = "",
                pythonMirror = "",
                linuxMirror = "",
                panelTitle = "",
            ),
        )
    }

    LaunchedEffect(state.config) {
        state.config?.let { c ->
            draft = SysDraft(
                logRemoveFrequency = c.logRemoveFrequency.asLongValue().toString(),
                cronConcurrency = c.cronConcurrency.asLongValue().toString(),
                dependenceProxy = c.dependenceProxy.orEmpty(),
                nodeMirror = c.nodeMirror.orEmpty(),
                pythonMirror = c.pythonMirror.orEmpty(),
                linuxMirror = c.linuxMirror.orEmpty(),
                panelTitle = c.panelTitle.orEmpty(),
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    fun toast(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text("系统配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null && state.info == null && state.config == null ->
                StateBox(message = state.error, modifier = Modifier.padding(padding), onRetry = { vm.load() })
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 系统信息
                SectionCard {
                    Text("系统信息", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val info = state.info
                    if (info != null) {
                        InfoRow(label = "版本", value = info.version.ifBlank { "-" })
                        InfoRow(label = "分支", value = info.branch.ifBlank { "-" })
                        InfoRow(label = "发布时间", value = formatTs(info.publishTime))
                    } else {
                        Text("暂无系统信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 系统配置表单
                SectionCard {
                    Text("系统配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "日志清理频率（天）",
                        value = draft.logRemoveFrequency,
                        onValueChange = { draft = draft.copy(logRemoveFrequency = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) {
                        val v = draft.logRemoveFrequency.toLongOrNull()
                        if (v == null) toast("日志清理频率需为有效数字") else vm.saveLogRemoveFrequency(v)
                    }

                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "任务并发数",
                        value = draft.cronConcurrency,
                        onValueChange = { draft = draft.copy(cronConcurrency = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) {
                        val v = draft.cronConcurrency.toLongOrNull()
                        if (v == null) toast("任务并发数需为有效数字") else vm.saveCronConcurrency(v)
                    }

                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "依赖代理",
                        value = draft.dependenceProxy,
                        onValueChange = { draft = draft.copy(dependenceProxy = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) { vm.saveDependenceProxy(draft.dependenceProxy) }

                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "Node 镜像",
                        value = draft.nodeMirror,
                        onValueChange = { draft = draft.copy(nodeMirror = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) { vm.saveNodeMirror(draft.nodeMirror) }

                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "Python 镜像",
                        value = draft.pythonMirror,
                        onValueChange = { draft = draft.copy(pythonMirror = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) { vm.savePythonMirror(draft.pythonMirror) }

                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "Linux 镜像",
                        value = draft.linuxMirror,
                        onValueChange = { draft = draft.copy(linuxMirror = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) { vm.saveLinuxMirror(draft.linuxMirror) }

                    Spacer(Modifier.height(10.dp))

                    ConfigField(
                        label = "面板标题",
                        value = draft.panelTitle,
                        onValueChange = { draft = draft.copy(panelTitle = it) },
                        singleLine = true,
                        saving = state.saving,
                    ) { vm.savePanelTitle(draft.panelTitle) }
                }

                // 保存全部
                Button(
                    onClick = {
                        val freq = draft.logRemoveFrequency.toLongOrNull()
                        val conc = draft.cronConcurrency.toLongOrNull()
                        if (freq == null) {
                            toast("日志清理频率需为有效数字")
                            return@Button
                        }
                        if (conc == null) {
                            toast("任务并发数需为有效数字")
                            return@Button
                        }
                        vm.saveAll(
                            logRemoveFrequency = freq,
                            cronConcurrency = conc,
                            dependenceProxy = draft.dependenceProxy,
                            nodeMirror = draft.nodeMirror,
                            pythonMirror = draft.pythonMirror,
                            linuxMirror = draft.linuxMirror,
                            panelTitle = draft.panelTitle,
                        )
                    },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("保存全部")
                }

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** 单条配置项：输入框 + 保存按钮 */
@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSave, enabled = !saving) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
        }
    }
}

/** JsonElement（数字或数字字符串）转为 Long，失败返回 0 */
private fun JsonElement?.asLongValue(): Long {
    if (this == null || !isJsonPrimitive) return 0L
    return runCatching { asJsonPrimitive.asLong }.getOrNull()
        ?: runCatching { asJsonPrimitive.asFloat.toLong() }.getOrNull()
        ?: 0L
}

/** 系统配置编辑草稿 */
private data class SysDraft(
    val logRemoveFrequency: String,
    val cronConcurrency: String,
    val dependenceProxy: String,
    val nodeMirror: String,
    val pythonMirror: String,
    val linuxMirror: String,
    val panelTitle: String,
)
