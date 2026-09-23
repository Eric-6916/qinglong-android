package com.qinglong.panel.ui.script

import androidx.compose.ui.graphics.Color

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.EmptyHint

private const val MODE_CONTENT = 0
private const val MODE_FILE = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptUploadScreen(
    container: AppContainer,
    initialPath: String,
    onBack: () -> Unit,
) {
    val vm: ScriptViewModel = viewModel(factory = ScriptViewModel.factory(container.repository))
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var path by remember { mutableStateOf(initialPath) }
    var fileName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(MODE_CONTENT) }
    var content by remember { mutableStateOf("") }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var createDir by remember { mutableStateOf(false) }
    var dirName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.state.message) {
        vm.state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // 选择本地文件
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            readPickedFile(context, uri)?.let { (name, bytes) ->
                pickedName = name
                pickedBytes = bytes
                if (fileName.isBlank()) fileName = name
                error = null
            } ?: run { error = "无法读取所选文件" }
        }
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text("上传/新建脚本") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
            // 目标路径（根目录为空串）
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("目标路径（根目录留空）") },
                placeholder = { Text("例如 scripts/collect") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // 文件名
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("文件名 *") },
                placeholder = { Text("例如 task.js") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            // 保存模式单选
            Text("保存模式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == MODE_CONTENT, onClick = { mode = MODE_CONTENT })
                Spacer(Modifier.width(4.dp))
                Text("直接编写内容")
                Spacer(Modifier.width(20.dp))
                RadioButton(selected = mode == MODE_FILE, onClick = { mode = MODE_FILE })
                Spacer(Modifier.width(4.dp))
                Text("选择本地文件")
            }
            Spacer(Modifier.height(12.dp))

            if (mode == MODE_CONTENT) {
                Text("脚本内容", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            } else {
                // 文件模式：选择本地文件
                Button(onClick = { fileLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (pickedName == null) "选择本地文件" else "重新选择")
                }
                Spacer(Modifier.height(8.dp))
                if (pickedName != null) {
                    Text(
                        text = "已选择：$pickedName（${pickedBytes?.size ?: 0} 字节）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    EmptyHint("尚未选择文件", Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(16.dp))

            // 同时创建新目录
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = createDir, onCheckedChange = { createDir = it })
                Spacer(Modifier.width(8.dp))
                Text("同时创建新目录")
            }
            if (createDir) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dirName,
                    onValueChange = { dirName = it },
                    label = { Text("新目录名") },
                    placeholder = { Text("例如 my_scripts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val name = fileName.trim()
                    if (name.isBlank()) {
                        error = "请输入文件名"
                        return@Button
                    }
                    val directory = if (createDir) dirName.trim().ifBlank { null } else null
                    if (createDir && directory == null) {
                        error = "请输入新目录名"
                        return@Button
                    }
                    error = null
                    if (mode == MODE_CONTENT) {
                        vm.uploadContent(name, path, content, directory, after = onBack)
                    } else {
                        val bytes = pickedBytes
                        if (bytes == null) {
                            error = "请先选择本地文件"
                            return@Button
                        }
                        vm.uploadFile(name, path, bytes, directory, after = onBack)
                    }
                },
                enabled = !vm.state.actionInFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (vm.state.actionInFlight) "上传中" else "上传")
            }
        }
    }
}

/** 从 Uri 读取文件名与字节内容（支持大于 1MB 的文件） */
private fun readPickedFile(context: Context, uri: Uri): Pair<String, ByteArray>? {
    val cr: ContentResolver = context.contentResolver
    val name = cr.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    } ?: uri.lastPathSegment ?: "file"
    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return name to bytes
}
