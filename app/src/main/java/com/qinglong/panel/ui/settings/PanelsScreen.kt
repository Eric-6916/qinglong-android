package com.qinglong.panel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qinglong.panel.data.local.AuthMode
import com.qinglong.panel.data.local.PanelProfile
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.EmptyHint
import com.qinglong.panel.ui.component.GlassCard
import com.qinglong.panel.ui.component.PrimaryButton
import com.qinglong.panel.ui.component.StatusPill
import kotlinx.coroutines.launch

/**
 * 面板管理（v1.1.0）：多青龙面板配置的列表 / 切换 / 添加 / 删除。
 *
 * 每个面板独立保存地址、凭据与登录状态：切换面板直接复用各自未过期的 token，
 * 无需重新登录；仅当目标面板登录失效时，由会话失效事件引导回向导重连。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onAddPanel: () -> Unit,
    onSwitched: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val profiles by container.settingsStore.profiles.collectAsState(initial = emptyList())
    val activeId by container.settingsStore.activeProfileId.collectAsState(initial = null)
    var switchingId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PanelProfile?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(msg) {
        msg?.let {
            snackbarHostState.showSnackbar(it)
            msg = null
        }
    }

    fun switchTo(id: String) {
        if (switchingId != null) return
        switchingId = id
        scope.launch {
            container.switchPanel(id).fold(
                onSuccess = { ready ->
                    switchingId = null
                    if (ready) onSwitched()
                    // false：会话失效事件已由 TokenManager 上报，QinglongApp 自动跳回向导
                },
                onFailure = { e ->
                    switchingId = null
                    msg = QinglongRepository.errorMessage(e)
                },
            )
        }
    }

    fun deletePanel(profile: PanelProfile) {
        scope.launch {
            container.removePanel(profile.id).fold(
                onSuccess = { msg = "已删除「${profile.name}」" },
                onFailure = { e -> msg = QinglongRepository.errorMessage(e) },
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopAppBarColors(),
                title = { Text("面板管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "每个面板独立保存地址、凭据与登录状态，切换后无需重新登录；仅登录失效时才需要重新连接。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            profiles.forEach { profile ->
                PanelProfileCard(
                    profile = profile,
                    isActive = profile.id == activeId,
                    switching = switchingId == profile.id,
                    onClick = { if (profile.id != activeId) switchTo(profile.id) },
                    onDelete = { pendingDelete = profile },
                )
            }

            if (profiles.isEmpty()) {
                EmptyHint("还没有面板配置，点击下方按钮添加")
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = "添加面板",
                onClick = onAddPanel,
                enabled = switchingId == null,
            )
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "删除面板",
            text = "确认删除「${target.name}」（${target.serverUrl}）？该面板保存的登录信息将一并清除。",
            confirmText = "删除",
            onConfirm = {
                pendingDelete = null
                deletePanel(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun PanelProfileCard(
    profile: PanelProfile,
    isActive: Boolean,
    switching: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (profile.authMode == AuthMode.OPEN) {
                        "OpenAPI 应用授权 · ${profile.clientId}"
                    } else {
                        "账号密码 · ${profile.username}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                switching -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                isActive -> StatusPill(
                    text = "当前",
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
