package com.qinglong.panel.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.local.AuthMode
import com.qinglong.panel.data.repository.QinglongRepository
import com.qinglong.panel.data.remote.TwoFactorRequiredException
import kotlinx.coroutines.launch

data class SetupUiState(
    /** 面板名称（仅"添加面板"模式使用） */
    val panelName: String = "",
    val serverUrl: String = "",
    /** 默认账号密码登录（v1.1.0 起推荐） */
    val authMode: AuthMode = AuthMode.ACCOUNT,
    val clientId: String = "",
    val clientSecret: String = "",
    val username: String = "",
    val password: String = "",
    val twoFactor: Boolean = false,
    val code: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    /** 本机已保存过配置（用于展示"使用已保存的凭据重连"兜底入口） */
    val hasSavedConfig: Boolean = false,
)

/**
 * 配置向导 ViewModel。
 *
 * @param addMode true = "添加面板"模式：提交后创建新面板并切换过去；
 *   false = 编辑当前激活面板（初始配置 / 重新配置）
 */
class SetupViewModel(
    private val repo: QinglongRepository,
    private val addMode: Boolean = false,
) : ViewModel() {

    var state by mutableStateOf(SetupUiState())
        private set

    /** 已保存过配置时预填，方便查看/修改（添加面板模式不预填） */
    fun prefill() {
        if (addMode) return
        viewModelScope.launch {
            val config = repo.serverConfig()
            state = state.copy(
                serverUrl = config.serverUrl,
                authMode = config.authMode,
                clientId = config.clientId,
                username = config.username,
                hasSavedConfig = config.configured,
            )
        }
    }

    /**
     * 用已持久化的配置与凭据重新连接（启动静默恢复失败后的兜底）：
     * 网络抖动导致重启时无法自动恢复，用户一键重连而无需手输密码。
     */
    fun reconnectSaved() {
        val s = state
        if (s.submitting) return
        state = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            repo.reconnectSaved().fold(
                onSuccess = { state = state.copy(submitting = false, connected = true) },
                onFailure = { e ->
                    state = state.copy(
                        submitting = false,
                        error = e.message ?: "重连失败",
                    )
                },
            )
        }
    }

    fun onPanelNameChange(v: String) { state = state.copy(panelName = v, error = null) }
    fun onServerChange(v: String) { state = state.copy(serverUrl = v, error = null) }
    fun onModeChange(mode: AuthMode) { state = state.copy(authMode = mode, error = null) }
    fun onClientIdChange(v: String) { state = state.copy(clientId = v, error = null) }
    fun onClientSecretChange(v: String) { state = state.copy(clientSecret = v, error = null) }
    fun onUsernameChange(v: String) { state = state.copy(username = v, error = null) }
    fun onPasswordChange(v: String) { state = state.copy(password = v, error = null) }
    fun onCodeChange(v: String) { state = state.copy(code = v, error = null) }

    fun submit() {
        val s = state
        if (s.submitting) return
        state = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            val result = when {
                addMode && s.authMode == AuthMode.OPEN ->
                    repo.addPanelAndConnect(
                        name = s.panelName,
                        serverUrl = s.serverUrl,
                        authMode = AuthMode.OPEN,
                        clientId = s.clientId,
                        clientSecret = s.clientSecret,
                        username = "",
                        password = "",
                    )
                addMode ->
                    repo.addPanelAndConnect(
                        name = s.panelName,
                        serverUrl = s.serverUrl,
                        authMode = AuthMode.ACCOUNT,
                        clientId = "",
                        clientSecret = "",
                        username = s.username,
                        password = s.password,
                    )
                s.authMode == AuthMode.OPEN -> repo.setupOpen(s.serverUrl, s.clientId, s.clientSecret)
                else -> repo.setupAccount(s.serverUrl, s.username, s.password)
            }
            result.fold(
                onSuccess = { state = state.copy(submitting = false, connected = true) },
                onFailure = { e ->
                    if (e is TwoFactorRequiredException) {
                        state = state.copy(submitting = false, twoFactor = true)
                    } else {
                        state = state.copy(
                            submitting = false,
                            error = e.message ?: "连接失败",
                        )
                    }
                },
            )
        }
    }

    fun submitCode() {
        val s = state
        if (s.submitting) return
        state = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            repo.submitTwoFactor(s.code).fold(
                onSuccess = { state = state.copy(submitting = false, connected = true) },
                onFailure = { e ->
                    state = state.copy(
                        submitting = false,
                        error = e.message ?: "验证失败",
                    )
                },
            )
        }
    }

    fun backToCredentials() {
        state = state.copy(twoFactor = false, code = "", error = null)
    }

    companion object {
        fun factory(repo: QinglongRepository, addMode: Boolean = false): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SetupViewModel(repo, addMode) }
            }
    }
}
