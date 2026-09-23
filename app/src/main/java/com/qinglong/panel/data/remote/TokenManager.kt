package com.qinglong.panel.data.remote

import android.util.Log
import com.qinglong.panel.data.local.AuthMode
import com.qinglong.panel.data.local.SecureStore
import com.qinglong.panel.data.local.ServerConfig
import com.qinglong.panel.data.local.SettingsStore
import com.qinglong.panel.data.model.ApiResponse
import com.qinglong.panel.data.model.LoginData
import com.qinglong.panel.data.model.TokenData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 认证相关事件 */
sealed interface AuthEvent {
    /** token 失效且无法自动恢复，需要重新登录 */
    data object SessionExpired : AuthEvent

    /** 账号密码模式需要两步验证码 */
    data class TwoFactorRequired(val username: String, val password: String) : AuthEvent
}

class TwoFactorRequiredException(val username: String, val password: String) : Exception()

/**
 * Token 管理：缓存 / 刷新 / 失效广播。
 *
 * - Open 模式：token 来自 GET /open/auth/token，30 天有效，临近过期自动续期
 * - 账密模式：token 来自登录接口（JWT），过期时间不可见，401 时由 Authenticator 刷新
 */
class TokenManager(
    private val secureStore: SecureStore,
    private val settingsStore: SettingsStore,
    private val configProvider: suspend () -> ServerConfig,
    private val authApiProvider: () -> AuthApi,
) {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiresAt: Long = 0L

    init {
        cachedToken = secureStore.token
        expiresAt = secureStore.tokenExpiresAt
    }

    fun cachedToken(): String? = cachedToken

    /**
     * 激活面板切换后调用（SecureStore.activeProfileId 已指向新面板）：
     * 重新从 SecureStore 加载新面板的 token 缓存，实现"切换不重新登录"——
     * 新面板 token 未过期则直接复用，过期才用各自持久化的凭据重换。
     */
    fun onActiveProfileChanged() {
        cachedToken = secureStore.token
        expiresAt = secureStore.tokenExpiresAt
    }

    /** 确保 token 有效（持锁；必要时续期/重登） */
    suspend fun validToken(forceRefresh: Boolean = false): String =
        mutex.withLock { validTokenLocked(forceRefresh) }

    /** 持锁执行（调用方必须已持有 [mutex]） */
    private suspend fun validTokenLocked(forceRefresh: Boolean): String {
        if (!forceRefresh && cachedToken != null && !expiredSoon()) return cachedToken!!
        val config = configProvider()
        val token = when (config.authMode) {
            AuthMode.OPEN -> fetchOpenToken(config)
            AuthMode.ACCOUNT -> fetchAccountToken(config.username, secureStore.password)
        }
        if (token.isNullOrBlank()) {
            logout()
            _events.emit(AuthEvent.SessionExpired)
            error("无法获取访问令牌")
        }
        return token
    }

    /**
     * 供 OkHttp Authenticator 的同步 runBlocking 调用。
     *
     * 致命坑（曾导致连接向导无限转圈）：mutex 不可重入。若某协程正持锁等待
     * HTTP 响应，而该响应是 401，OkHttp 会在交付响应的同一线程上调用
     * authenticator；此时若在此阻塞抢锁，响应永远交不出去 → 协程永不释放锁
     * → 死锁。因此用非阻塞 tryLock：抢不到说明已有刷新在进行，放弃本次自动
     * 刷新，让请求以 401 正常失败（上层会转译为用户可读错误）。
     */
    suspend fun refreshTokenForAuthenticator(): String? {
        if (!mutex.tryLock()) return null
        return try {
            validTokenLocked(forceRefresh = true)
        } catch (t: Throwable) {
            Log.w(TAG, "authenticator token refresh failed", t)
            null
        } finally {
            mutex.unlock()
        }
    }

    /**
     * 冷启动静默恢复会话（不触发 SessionExpired 事件）：
     * 优先复用未过期的缓存 token；否则用已持久化凭据重新换取
     * （open: client_secret / account: password）。
     *
     * 失败仅代表无法直达首页，由调用方决定留在配置向导——
     * 启动瞬间网络抖动不应把用户踢到"重新登录"。
     */
    suspend fun restoreSessionToken(): String? = mutex.withLock {
        if (cachedToken != null && !expiredSoon()) return@withLock cachedToken!!
        val config = configProvider()
        runCatching {
            when (config.authMode) {
                AuthMode.OPEN -> fetchOpenToken(config)
                AuthMode.ACCOUNT -> fetchAccountToken(config.username, secureStore.password)
            }
        }.onFailure { Log.w(TAG, "silent session restore failed", it) }
            .getOrNull()
    }

    /** 主动登录（账号密码模式，可能触发 2FA） */
    suspend fun login(username: String, password: String): Result<String> = runCatching {
        mutex.withLock {
            val config = configProvider()
            val response = authApiProvider().login(
                mapOf("username" to username, "password" to password)
            )
            when (response.code) {
                200 -> {
                    val token = response.data?.token.orEmpty()
                    persist(config.authMode, token, 0L)
                    token
                }
                420 -> throw TwoFactorRequiredException(username, password)
                else -> throw IllegalStateException(response.message ?: "登录失败(${response.code})")
            }
        }
    }

    /** 两步验证码登录 */
    suspend fun loginWithCode(code: String): Result<String> = runCatching {
        mutex.withLock {
            val config = configProvider()
            val username = config.username
            val password = secureStore.password
            val response = authApiProvider().twoFactorLogin(
                mapOf("username" to username, "password" to password, "code" to code)
            )
            when (response.code) {
                200 -> {
                    val token = response.data?.token.orEmpty()
                    persist(config.authMode, token, 0L)
                    token
                }
                else -> throw IllegalStateException(response.message ?: "验证失败(${response.code})")
            }
        }
    }

    /** Open 模式激活：换取首个 token */
    suspend fun activateOpen(): Result<String> = runCatching {
        mutex.withLock {
            val config = configProvider()
            fetchOpenToken(config) ?: error("获取 token 失败")
        }
    }

    private suspend fun fetchOpenToken(config: ServerConfig): String? {
        val response = authApiProvider().openToken(config.clientId, secureStore.clientSecret)
        if (response.code == 200) {
            val data: TokenData = response.data ?: return null
            persist(config.authMode, data.token, data.expiration)
            return data.token
        }
        Log.w(TAG, "open token error: ${response.code} ${response.message}")
        throw IllegalStateException(response.message ?: "client_id 或 client_secret 有误")
    }

    private suspend fun fetchAccountToken(username: String, password: String): String? {
        if (username.isBlank() || password.isBlank()) return null
        val response = authApiProvider().login(mapOf("username" to username, "password" to password))
        if (response.code == 200) {
            val data: LoginData = response.data ?: return null
            persist(AuthMode.ACCOUNT, data.token, 0L)
            return data.token
        }
        if (response.code == 420) throw TwoFactorRequiredException(username, password)
        Log.w(TAG, "account login error: ${response.code} ${response.message}")
        return null
    }

    /** 供 Repository 使用：上报会话失效事件（UI 收到后跳转重新登录） */
    suspend fun reportSessionExpired() {
        _events.emit(AuthEvent.SessionExpired)
    }

    private fun persist(mode: AuthMode, token: String, expiration: Long) {
        cachedToken = token
        expiresAt = expiration
        secureStore.token = token
        secureStore.tokenExpiresAt = expiration
    }

    private fun expiredSoon(): Boolean {
        if (expiresAt <= 0L) return false // 账密模式 JWT 过期时间不可见，靠 401 刷新
        return expiresAt - System.currentTimeMillis() / 1000 < 300
    }

    fun logout() {
        cachedToken = null
        expiresAt = 0L
        secureStore.token = null
        secureStore.tokenExpiresAt = 0L
    }

    companion object {
        private const val TAG = "TokenManager"
    }
}
