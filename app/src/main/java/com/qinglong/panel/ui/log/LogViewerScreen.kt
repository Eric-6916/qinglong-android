package com.qinglong.panel.ui.log

import androidx.compose.ui.graphics.Color

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qinglong.panel.data.model.LogChunk
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.LoadingBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通用日志查看器：进入即自动每秒轮询（尽可能实时同步），支持暂停/恢复、尾随滚动、一键复制。
 * loader 返回原始日志内容；失败时显示错误并保留旧内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    title: String,
    loader: suspend () -> Result<LogChunk>,
    onBack: () -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // 默认开启每秒自动刷新，尽可能实时同步任务运行日志
    var autoRefresh by remember { mutableStateOf(true) }
    // 用户停在日志底部时跟随尾部；向上翻看历史时自动暂停跟随
    var followTail by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loader().fold(
            onSuccess = { chunk ->
                content = chunk.data.orEmpty()
                error = null
            },
            onFailure = { e ->
                error = e.message ?: "加载失败"
            },
        )
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            delay(1000)
            // 每次轮询前判断用户是否仍停在日志尾部
            followTail = scrollState.value >= scrollState.maxValue - 80
            refresh()
        }
    }
    LaunchedEffect(content) {
        if (followTail && content.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(content))
                        },
                        enabled = content.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                    }
                    IconButton(
                        onClick = {
                            if (content.isNotEmpty()) {
                                scope.launch { loading = true; refresh() }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (autoRefresh) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "自动刷新（1 秒）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoRefresh, onCheckedChange = { autoRefresh = it })
            }
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                if (loading && content.isEmpty()) {
                    LoadingBox()
                } else if (content.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无日志内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = content,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
