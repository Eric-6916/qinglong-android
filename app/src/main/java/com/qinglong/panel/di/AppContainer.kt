package com.qinglong.panel.di

import android.content.Context
import com.qinglong.panel.BuildConfig
import com.qinglong.panel.data.local.PanelProfile
import com.qinglong.panel.data.local.SecureStore
import com.qinglong.panel.data.local.SettingsStore
import com.qinglong.panel.data.remote.ApiClient
import com.qinglong.panel.data.remote.TokenManager
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手写依赖容器。全局单例，Application 持有。
 *
 * 循环依赖用 lazy 化解：
 * TokenManager 需要 AuthApi（来自 ApiClient），ApiClient 需要 TokenManager（拦截器取 token）。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val secureStore: SecureStore by lazy { SecureStore(appContext) }

    val tokenManager: TokenManager by lazy {
        TokenManager(
            secureStore = secureStore,
            settingsStore = settingsStore,
            configProvider = { settingsStore.current() },
            authApiProvider = { apiClient.authApi },
        )
    }

    val apiClient: ApiClient by lazy { ApiClient(tokenManager, BuildConfig.DEBUG) }

    val repository: QinglongRepository by lazy {
        QinglongRepository(settingsStore, secureStore, tokenManager, apiClient)
    }

    /** 会话失效事件（401 且无法自动恢复） */
    val authEvents: SharedFlow<com.qinglong.panel.data.remote.AuthEvent>
        get() = tokenManager.events

    private val _sessionRestored = MutableStateFlow<Boolean?>(null)

    /**
     * 启动会话恢复结果：null = 恢复进行中；true = 已登录，UI 直达首页；
     * false = 未配置或恢复失败，留在配置向导。
     */
    val sessionRestored: StateFlow<Boolean?> = _sessionRestored.asStateFlow()

    /** 当前激活面板（设置中心 / 面板管理页观察） */
    private val _activePanel = MutableStateFlow<PanelProfile?>(null)
    val activePanel: StateFlow<PanelProfile?> = _activePanel.asStateFlow()

    /**
     * 应用启动时调用：
     * 1. 旧版单配置迁移为默认面板（含 SecureStore 旧密钥迁移）；
     * 2. 按激活面板重建 Retrofit 基址，并静默恢复登录会话
     *    （复用未过期 token，或用该面板持久化的凭据重换）。
     *
     * @return true = 会话就绪，UI 可直接进首页，无需重新登录
     */
    suspend fun initFromPersisted(): Boolean {
        val migratedId = settingsStore.ensureInitialized()
        if (migratedId != null) secureStore.migrateLegacyTo(migratedId)
        val active = settingsStore.activeProfile()
        _activePanel.value = active
        if (active == null || !active.configured) {
            _sessionRestored.value = false
            return false
        }
        secureStore.activeProfileId = active.id
        // v1.2.2：切换前缀后立即重载 TokenManager 的 token 缓存。
        // TokenManager 的 init 块可能在 QinglongApp 收集 authEvents 时就被触发
        // （早于本行），当时读到的仍是默认前缀 p_default_ 的 token；不重载的话
        // restoreSessionToken 会复用错误面板的 token → 请求 401 → 误报"登录已过期"。
        tokenManager.onActiveProfileChanged()
        apiClient.configure(active.serverUrl, active.authMode)
        val restored = tokenManager.restoreSessionToken() != null
        _sessionRestored.value = restored
        return restored
    }

    /**
     * 切换面板（设置 → 面板管理）：保留各自登录态，切换不重新登录。
     * @return true = 会话就绪；false = 目标面板登录失效，已发射 SessionExpired
     */
    suspend fun switchPanel(id: String): Result<Boolean> {
        val result = repository.switchPanel(id)
        result.onSuccess { _activePanel.value = settingsStore.activeProfile() }
        return result
    }

    /** 添加面板并连接；失败自动回滚到原面板 */
    suspend fun addPanelAndConnect(
        name: String,
        serverUrl: String,
        authMode: com.qinglong.panel.data.local.AuthMode,
        clientId: String,
        clientSecret: String,
        username: String,
        password: String,
    ): Result<Unit> {
        val result = repository.addPanelAndConnect(
            name, serverUrl, authMode, clientId, clientSecret, username, password,
        )
        result.onSuccess { _activePanel.value = settingsStore.activeProfile() }
        return result
    }

    /** 删除面板配置（当前激活面板不可删除） */
    suspend fun removePanel(id: String): Result<Unit> {
        val result = repository.removePanel(id)
        result.onSuccess { _activePanel.value = settingsStore.activeProfile() }
        return result
    }

    /** 清除全部本地状态（全部面板配置 + 凭据 + token），用于重新配置服务器 */
    suspend fun resetAll() {
        tokenManager.logout()
        settingsStore.clear()
        secureStore.clear()
        _activePanel.value = null
    }
}
