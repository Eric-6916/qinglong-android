package com.qinglong.panel.ui.cron

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.imeAware
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox
import com.qinglong.panel.ui.setup.PasswordField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CronEditScreen(
    container: AppContainer,
    cronId: Long?,
    onDone: () -> Unit,
) {
    val vm: CronEditViewModel = viewModel(factory = CronEditViewModel.factory(container.repository))
    val state = vm.state

    LaunchedEffect(cronId) { cronId?.let { vm.load(it) } }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(if (cronId == null) "新建任务" else "编辑任务") },
                actions = {
                    Button(
                        onClick = vm::save,
                        enabled = !state.saving,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.saving) "保存中" else "保存")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding))
            state.notFound -> StateBox(
                message = "任务不存在或已被删除",
                modifier = Modifier.padding(padding),
                onRetry = onDone,
            )
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onName,
                    label = { Text("名称（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.command,
                    onValueChange = vm::onCommand,
                    label = { Text("命令 *") },
                    placeholder = { Text("task python /ql/scripts/xxx.js") },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.schedule,
                    onValueChange = vm::onSchedule,
                    label = { Text("定时规则 *") },
                    placeholder = { Text("0 8 * * *") },
                    singleLine = true,
                    supportingText = { Text("5 段 Cron 表达式：分 时 日 月 周") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchedulePreset("每分") { vm.onSchedule("* * * * *") }
                    SchedulePreset("每 10 分钟") { vm.onSchedule("*/10 * * * *") }
                    SchedulePreset("每小时") { vm.onSchedule("0 * * * *") }
                    SchedulePreset("每天 8 点") { vm.onSchedule("0 8 * * *") }
                    SchedulePreset("每天 0 点") { vm.onSchedule("0 0 * * *") }
                    SchedulePreset("每周一 8 点") { vm.onSchedule("0 8 * * 1") }
                    SchedulePreset("每月 1 号") { vm.onSchedule("0 8 1 * *") }
                }
                Spacer(Modifier.height(16.dp))
                Text("高级选项", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.labels,
                    onValueChange = vm::onLabels,
                    label = { Text("标签（逗号分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.taskBefore,
                    onValueChange = vm::onTaskBefore,
                    label = { Text("任务前置命令（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.taskAfter,
                    onValueChange = vm::onTaskAfter,
                    label = { Text("任务后置命令（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.logName,
                    onValueChange = vm::onLogName,
                    label = { Text("自定义日志文件名（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SchedulePreset(label: String, onPick: () -> Unit) {
    AssistChip(
        onClick = onPick,
        label = { Text(label) },
    )
}
