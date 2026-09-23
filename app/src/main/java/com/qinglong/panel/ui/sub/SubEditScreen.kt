package com.qinglong.panel.ui.sub

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.imeAware
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.IntervalSchedule
import com.qinglong.panel.data.model.SubscriptionPayload
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubEditScreen(
    container: AppContainer,
    subId: Long?,
    onDone: () -> Unit,
) {
    val vm: SubViewModel = viewModel(factory = SubViewModel.factory(container.repository))

    // 表单字段（本地状态）
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("private") } // private | public
    var scheduleType by remember { mutableStateOf("crontab") } // crontab | interval
    var schedule by remember { mutableStateOf("") }
    var intervalType by remember { mutableStateOf("days") } // seconds | minutes | hours | days
    var intervalValue by remember { mutableStateOf("") }
    var whitelist by remember { mutableStateOf("") }
    var blacklist by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var dependences by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }
    var subBefore by remember { mutableStateOf("") }
    var subAfter by remember { mutableStateOf("") }
    var proxy by remember { mutableStateOf("") }
    var autoAddCron by remember { mutableStateOf(false) }
    var autoDelCron by remember { mutableStateOf(false) }

    var prefilled by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }

    LaunchedEffect(subId) {
        if (subId == null) {
            prefilled = true
        } else if (vm.state.items.isEmpty() && !vm.state.loading) {
            vm.load()
        }
    }
    LaunchedEffect(vm.state.loading) {
        if (subId != null && !vm.state.loading && !prefilled) {
            vm.findById(subId)?.let { sub ->
                name = sub.name.orEmpty()
                alias = sub.alias.orEmpty()
                url = sub.url.orEmpty()
                type = sub.type?.takeIf { it == "public" } ?: "private"
                scheduleType = sub.scheduleType?.takeIf { it == "interval" } ?: "crontab"
                schedule = sub.schedule.orEmpty()
                intervalType = sub.intervalSchedule?.type?.takeIf { it in listOf("seconds", "minutes", "hours", "days") } ?: "days"
                intervalValue = sub.intervalSchedule?.value?.takeIf { it > 0 }?.toString() ?: ""
                whitelist = sub.whitelist.orEmpty()
                blacklist = sub.blacklist.orEmpty()
                branch = sub.branch.orEmpty()
                dependences = sub.dependences.orEmpty()
                extensions = sub.extensions.orEmpty()
                subBefore = sub.subBefore.orEmpty()
                subAfter = sub.subAfter.orEmpty()
                proxy = sub.proxy.orEmpty()
                autoAddCron = sub.autoAddCron == 1
                autoDelCron = sub.autoDelCron == 1
                prefilled = true
            } ?: run {
                notFound = true
                prefilled = true
            }
        }
    }
    LaunchedEffect(vm.state.saved) {
        if (vm.state.saved) onDone()
    }

    fun buildPayload(): SubscriptionPayload = SubscriptionPayload(
        id = subId,
        type = type,
        scheduleType = scheduleType,
        alias = alias.trim(),
        url = url.trim(),
        schedule = if (scheduleType == "crontab") schedule.trim().ifBlank { null } else null,
        intervalSchedule = if (scheduleType == "interval") {
            IntervalSchedule(type = intervalType, value = intervalValue.toLongOrNull() ?: 0)
        } else null,
        name = name.trim().ifBlank { null },
        whitelist = whitelist.trim().ifBlank { null },
        blacklist = blacklist.trim().ifBlank { null },
        branch = branch.trim().ifBlank { null },
        dependences = dependences.trim().ifBlank { null },
        extensions = extensions.trim().ifBlank { null },
        subBefore = subBefore.trim().ifBlank { null },
        subAfter = subAfter.trim().ifBlank { null },
        proxy = proxy.trim().ifBlank { null },
        autoAddCron = autoAddCron,
        autoDelCron = autoDelCron,
    )

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(if (subId == null) "新增订阅" else "编辑订阅") },
                actions = {
                    Button(
                        onClick = {
                            if (alias.isBlank() || url.isBlank()) {
                                vm.reportError("别名与订阅地址均不能为空")
                            } else {
                                vm.save(subId, buildPayload())
                            }
                        },
                        enabled = !vm.state.saving,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (vm.state.saving) "保存中" else "保存")
                    }
                },
            )
        },
    ) { padding ->
        when {
            vm.state.loading && !prefilled -> LoadingBox(modifier = Modifier.padding(padding))
            notFound -> StateBox(
                message = "订阅不存在或已被删除",
                modifier = Modifier.padding(padding),
                onRetry = {
                    prefilled = false
                    notFound = false
                    vm.load()
                },
            )
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("别名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅地址 *") },
                    placeholder = { Text("https://example.com/sub.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                // 类型
                Text("类型", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioOption("私有", "private", type) { type = it }
                    RadioOption("公开", "public", type) { type = it }
                }
                Spacer(Modifier.height(16.dp))

                // 定时方式
                Text("定时方式", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioOption("Crontab", "crontab", scheduleType) { scheduleType = it }
                    RadioOption("间隔", "interval", scheduleType) { scheduleType = it }
                }
                Spacer(Modifier.height(8.dp))
                if (scheduleType == "crontab") {
                    OutlinedTextField(
                        value = schedule,
                        onValueChange = { schedule = it },
                        label = { Text("Cron 表达式") },
                        placeholder = { Text("0 8 * * *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = intervalValue,
                            onValueChange = { intervalValue = it.filter { c -> c.isDigit() } },
                            label = { Text("间隔值") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("单位", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioOption("秒", "seconds", intervalType) { intervalType = it }
                        RadioOption("分", "minutes", intervalType) { intervalType = it }
                        RadioOption("时", "hours", intervalType) { intervalType = it }
                        RadioOption("天", "days", intervalType) { intervalType = it }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("高级选项", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LabeledField(whitelist, { whitelist = it }, "白名单", "只同步白名单内的脚本")
                Spacer(Modifier.height(12.dp))
                LabeledField(blacklist, { blacklist = it }, "黑名单", "忽略黑名单内的脚本")
                Spacer(Modifier.height(12.dp))
                LabeledField(branch, { branch = it }, "分支（选填，默认 main）", "仓库分支名")
                Spacer(Modifier.height(12.dp))
                LabeledField(dependences, { dependences = it }, "依赖", "逗号分隔")
                Spacer(Modifier.height(12.dp))
                LabeledField(extensions, { extensions = it }, "扩展", "逗号分隔")
                Spacer(Modifier.height(12.dp))
                LabeledField(subBefore, { subBefore = it }, "前置命令", "订阅拉取前执行")
                Spacer(Modifier.height(12.dp))
                LabeledField(subAfter, { subAfter = it }, "后置命令", "订阅拉取后执行")
                Spacer(Modifier.height(12.dp))
                LabeledField(proxy, { proxy = it }, "代理", "http(s)://host:port")
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("自动添加 Cron", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = autoAddCron, onCheckedChange = { autoAddCron = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("自动删除 Cron", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = autoDelCron, onCheckedChange = { autoDelCron = it })
                }

                if (vm.state.message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = vm.state.message!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 单选行（label 展示中文，value 为后端取值） */
@Composable
private fun RadioOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .selectable(selected = selected == value, onClick = { onSelect(value) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 带标签与说明的输入框 */
@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().imeAware(),
    )
}
