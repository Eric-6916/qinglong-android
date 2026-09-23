package com.qinglong.panel.ui.settings

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.qinglong.panel.BuildConfig
import com.qinglong.panel.data.local.AuthMode
import com.qinglong.panel.data.local.ServerConfig
import com.qinglong.panel.data.local.ThemeMode
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.ConfirmDialog
import com.qinglong.panel.ui.component.InfoRow
import com.qinglong.panel.ui.component.SectionCard
import kotlinx.coroutines.launch

/**
 * 设置中心入口。展示当前服务器信息，并提供跳转到
 * 面板管理 / 系统配置 / 依赖管理 / 配置文件 / 日志目录 / 系统日志 的入口，以及退出登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onLogout: () -> Unit,
    onReconfigure: () -> Unit,
    onOpenPanels: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenDependence: () -> Unit,
    onOpenConfigs: () -> Unit,
    onOpenLogFiles: () -> Unit,
    onOpenSystemLog: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var logoutMsg by remember { mutableStateOf<String?>(null) }

    // ServerConfig 通过 DataStore Flow 读取（current() 为挂起函数，此处用 Flow 等效）；
    // v1.1.0 起 config / activeProfile 均指向当前激活面板，切换后面板信息自动刷新
    val cfg by container.settingsStore.config.collectAsState(initial = ServerConfig())
    val activeProfile by container.settingsStore.activeProfile.collectAsState(initial = null)

    // 主题模式（DataStore Flow，默认浅色）；设置页切换后 MainActivity 即时应用
    val themeMode by container.settingsStore.themeMode.collectAsState(initial = ThemeMode.LIGHT)
    val isDarkTheme = themeMode == ThemeMode.DARK

    LaunchedEffect(logoutMsg) {
        logoutMsg?.let {
            snackbarHostState.showSnackbar(it)
            logoutMsg = null
        }
    }

    Scaffold(containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置中心") },
                colors = auroraTopAppBarColors(),
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 当前服务器
            SectionCard {
                Text("当前服务器", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow(label = "面板名称", value = activeProfile?.name?.ifBlank { "-" } ?: "-")
                InfoRow(label = "服务器地址", value = cfg.serverUrl.ifBlank { "-" })
                val modeText = if (cfg.authMode == AuthMode.OPEN) "OpenAPI 应用授权" else "账号密码"
                val accountLabel = if (cfg.authMode == AuthMode.OPEN) "Client ID" else "账号"
                val accountValue = if (cfg.authMode == AuthMode.OPEN) cfg.clientId.ifBlank { "-" } else cfg.username.ifBlank { "-" }
                InfoRow(label = "认证模式", value = modeText)
                InfoRow(label = accountLabel, value = accountValue)
            }

            // 外观：深色 / 浅色切换（默认浅色）
            SectionCard {
                SettingsSwitchEntry(
                    icon = Icons.Filled.DarkMode,
                    title = "深色模式",
                    subtitle = "默认浅色主题；开启后切换为深色极光主题",
                    checked = isDarkTheme,
                    onCheckedChange = { dark ->
                        scope.launch {
                            container.settingsStore.saveThemeMode(
                                if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                            )
                        }
                    },
                )
            }

            // 功能入口
            SectionCard {
                SettingsEntry(
                    icon = Icons.Filled.Storage,
                    title = "面板管理",
                    subtitle = "添加 / 切换多个青龙面板，各自保留登录状态",
                    onClick = onOpenPanels,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingsEntry(
                    icon = Icons.Filled.Dns,
                    title = "重新配置服务器",
                    subtitle = "更换当前面板的地址或认证方式",
                    onClick = onReconfigure,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingsEntry(
                    icon = Icons.Filled.Settings,
                    title = "系统配置",
                    subtitle = "版本信息、日志清理、镜像源与任务并发设置",
                    onClick = onOpenSystem,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingsEntry(
                    icon = Icons.Filled.Extension,
                    title = "依赖管理",
                    subtitle = "查看、安装与卸载 nodejs / python3 / linux 依赖",
                    onClick = onOpenDependence,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingsEntry(
                    icon = Icons.Filled.Description,
                    title = "配置文件",
                    subtitle = "编辑 /ql/config 下的面板配置文件",
                    onClick = onOpenConfigs,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingsEntry(
                    icon = Icons.Filled.Folder,
                    title = "日志目录",
                    subtitle = "浏览脚本运行产生的日志文件",
                    onClick = onOpenLogFiles,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingsEntry(
                    icon = Icons.Filled.Article,
                    title = "系统日志",
                    subtitle = "查看面板系统运行日志",
                    onClick = onOpenSystemLog,
                )
            }

            // 退出登录
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { confirmLogout = true }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "退出登录",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                // 版本号唯一数据源：BuildConfig.VERSION_NAME（来自 build.gradle.kts）
                text = "青龙管家 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            text = "确认退出当前账号？退出后需要重新配置服务器。",
            confirmText = "退出",
            onConfirm = {
                confirmLogout = false
                scope.launch {
                    container.repository.logout().fold(
                        onSuccess = { onLogout() },
                        onFailure = { e -> logoutMsg = QinglongRepository.errorMessage(e) },
                    )
                }
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/** 设置中心开关条目（主题切换等） */
@Composable
private fun SettingsSwitchEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 设置中心单条入口 */
@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
