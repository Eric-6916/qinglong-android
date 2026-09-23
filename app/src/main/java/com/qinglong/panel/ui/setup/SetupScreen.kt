package com.qinglong.panel.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.local.AuthMode
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.AuroraBackground
import com.qinglong.panel.ui.component.GhostButton
import com.qinglong.panel.ui.component.GlassCard
import com.qinglong.panel.ui.component.GlassTextField
import com.qinglong.panel.ui.component.PrimaryButton
import com.qinglong.panel.ui.component.SegmentedToggle
import com.qinglong.panel.ui.component.imeAware
import com.qinglong.panel.ui.theme.LocalAuroraColors
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

/**
 * 服务器配置向导（Aurora Glass）：极光背景 → 品牌 Hero → 玻璃表单卡 →
 * 认证方式分段开关 → 凭据录入 → 渐变主按钮连接。2FA 与首屏同屏切换。
 *
 * IME 适配（v1.2.0）：targetSdk 35 边到边下软键盘为覆盖式弹出，
 * 滚动列消费 [imePadding] 收缩视野，输入框经 [imeAware] 在键盘升起后
 * 自动滚入可见区域，账号/密码不再被输入法遮挡。
 *
 * @param addMode true = 添加面板模式：额外录入面板名称，连接成功后切换并激活新面板
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    onConnected: () -> Unit,
    addMode: Boolean = false,
) {
    val vm: SetupViewModel = viewModel(factory = SetupViewModel.factory(container.repository, addMode))
    val state = vm.state
    val aurora = LocalAuroraColors.current

    LaunchedEffect(Unit) { vm.prefill() }
    LaunchedEffect(state.connected) {
        if (state.connected) onConnected()
    }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            // ---- Hero：玻璃徽标 + 品牌名 ----
            Column(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(aurora.glassFillStrong),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "青龙管家",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (addMode) "添加一个新的青龙面板" else "连接你的青龙面板",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            // ---- 玻璃表单卡 ----
            GlassCard {
                if (!state.twoFactor) {
                    if (addMode) {
                        GlassTextField(
                            value = state.panelName,
                            onValueChange = vm::onPanelNameChange,
                            label = "面板名称",
                            placeholder = "留空则取服务器地址",
                            modifier = Modifier.imeAware(),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    GlassTextField(
                        value = state.serverUrl,
                        onValueChange = vm::onServerChange,
                        label = "服务器地址",
                        placeholder = "http://192.168.1.10:5700",
                        supportingText = { Text("支持 http / https，无需填写 /api 后缀") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.imeAware(),
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "认证方式",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedToggle(
                        options = listOf("应用授权", "账号密码"),
                        selectedIndex = if (state.authMode == AuthMode.OPEN) 0 else 1,
                        onSelect = { index ->
                            vm.onModeChange(if (index == 0) AuthMode.OPEN else AuthMode.ACCOUNT)
                        },
                    )
                    Spacer(Modifier.height(16.dp))

                    when (state.authMode) {
                        AuthMode.OPEN -> {
                            GlassTextField(
                                value = state.clientId,
                                onValueChange = vm::onClientIdChange,
                                label = "client_id",
                                modifier = Modifier.imeAware(),
                            )
                            Spacer(Modifier.height(12.dp))
                            PasswordField(
                                value = state.clientSecret,
                                onValueChange = vm::onClientSecretChange,
                                label = "client_secret",
                                modifier = Modifier.imeAware(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "在青龙「应用设置 → OpenAPI」中创建应用获取，勾选所需权限（crons / envs / scripts / subscriptions / dependencies / configs / logs / system / dashboard）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AuthMode.ACCOUNT -> {
                            GlassTextField(
                                value = state.username,
                                onValueChange = vm::onUsernameChange,
                                label = "用户名",
                                modifier = Modifier.imeAware(),
                            )
                            Spacer(Modifier.height(12.dp))
                            PasswordField(
                                value = state.password,
                                onValueChange = vm::onPasswordChange,
                                label = "密码",
                                modifier = Modifier.imeAware(),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "两步验证",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "该账号已开启 2FA，请输入身份验证器生成的 6 位验证码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassTextField(
                        value = state.code,
                        onValueChange = vm::onCodeChange,
                        label = "验证码",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.imeAware(),
                    )
                }

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = when {
                    state.twoFactor -> "验证并登录"
                    addMode -> "连接并切换"
                    else -> "连接"
                },
                onClick = { if (state.twoFactor) vm.submitCode() else vm.submit() },
                enabled = !state.submitting,
                loading = state.submitting,
            )

            // 已保存过配置时（如重启瞬间网络抖动导致静默恢复失败）：
            // 提供一键重连兜底，无需重新输入 client_secret / 密码。
            // 添加面板模式下新面板本就无已存凭据，不展示该入口。
            if (state.hasSavedConfig && !state.twoFactor && !addMode) {
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    text = "使用已保存的凭据重连",
                    onClick = vm::reconnectSaved,
                    enabled = !state.submitting,
                )
            }

            if (state.twoFactor) {
                Spacer(Modifier.height(4.dp))
                GhostButton(text = "返回修改配置", onClick = vm::backToCredentials)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "提示：凭据仅保存在本机（EncryptedSharedPreferences），不会上传。自签名证书的内网面板需在系统浏览器中信任证书后再连接。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "隐藏" else "显示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
