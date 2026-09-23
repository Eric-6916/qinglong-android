package com.qinglong.panel.ui.env

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StateBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvEditScreen(
    container: AppContainer,
    envId: Long?,
    onDone: () -> Unit,
) {
    val vm: EnvViewModel = viewModel(factory = EnvViewModel.factory(container.repository))
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }

    // 编辑模式：复用 VM 的列表加载，加载完成后按 id 预填
    LaunchedEffect(envId) {
        if (envId == null) {
            prefilled = true
        } else if (vm.state.items.isEmpty() && !vm.state.loading) {
            vm.load()
        }
    }
    LaunchedEffect(vm.state.loading) {
        if (envId != null && !vm.state.loading && !prefilled) {
            vm.findById(envId)?.let { env ->
                name = env.name.orEmpty()
                value = env.value.orEmpty()
                remarks = env.remarks.orEmpty()
                prefilled = true
            } ?: run {
                notFound = true
                prefilled = true
            }
        }
    }
    // 保存成功后退出
    LaunchedEffect(vm.state.saved) {
        if (vm.state.saved) onDone()
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(if (envId == null) "新增变量" else "编辑变量") },
                actions = {
                    Button(
                        onClick = {
                            if (name.isBlank() || value.isBlank()) {
                                vm.reportError("名称与值均不能为空")
                            } else {
                                vm.save(envId, name, value, remarks)
                            }
                        },
                        enabled = !vm.state.saving,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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
                message = "环境变量不存在或已被删除",
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值 *") },
                    placeholder = { Text("环境变量的值") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("备注（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().imeAware(),
                )
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
