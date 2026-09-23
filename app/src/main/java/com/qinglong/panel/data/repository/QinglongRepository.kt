package com.qinglong.panel.data.repository

import android.util.Log
import com.qinglong.panel.data.local.AuthMode
import com.qinglong.panel.data.local.PanelProfile
import com.qinglong.panel.data.local.SecureStore
import com.qinglong.panel.data.local.ServerConfig
import com.qinglong.panel.data.local.SettingsStore
import com.qinglong.panel.data.model.ApiResponse
import com.qinglong.panel.data.model.ConfigFile
import com.qinglong.panel.data.model.ConfigSavePayload
import com.qinglong.panel.data.model.Cron
import com.qinglong.panel.data.model.CronPayload
import com.qinglong.panel.data.model.DependencePayload
import com.qinglong.panel.data.model.DashboardOverview
import com.qinglong.panel.data.model.DashboardRuntime
import com.qinglong.panel.data.model.DashboardSystem
import com.qinglong.panel.data.model.Dependence
import com.qinglong.panel.data.model.Env
import com.qinglong.panel.data.model.EnvPayload
import com.qinglong.panel.data.model.LogChunk
import com.qinglong.panel.data.model.MovePayload
import com.qinglong.panel.data.model.NotifyPayload
import com.qinglong.panel.data.model.NumberPayload
import com.qinglong.panel.data.model.PagedData
import com.qinglong.panel.data.model.ScriptDeletePayload
import com.qinglong.panel.data.model.ScriptFile
import com.qinglong.panel.data.model.ScriptRenamePayload
import com.qinglong.panel.data.model.ScriptRunPayload
import com.qinglong.panel.data.model.ScriptSavePayload
import com.qinglong.panel.data.model.ScriptStopPayload
import com.qinglong.panel.data.model.SubLogFile
import com.qinglong.panel.data.model.Subscription
import com.qinglong.panel.data.model.SubscriptionPayload
import com.qinglong.panel.data.model.SystemConfig
import com.qinglong.panel.data.model.SystemInfo
import com.qinglong.panel.data.model.TextPayload
import com.qinglong.panel.data.model.TrendPoint
import com.qinglong.panel.data.remote.ApiClient
import com.qinglong.panel.data.remote.QinglongApi
import com.qinglong.panel.data.remote.TokenManager
import com.qinglong.panel.data.remote.TwoFactorRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 业务异常：消息可直达用户 */
class ApiException(message: String) : Exception(message)

/**
 * 青龙面板仓库层。
 *
 * 职责：
 * 1. 调用前确保 token 有效（必要时自动续期/重登）
 * 2. 统一收敛青龙的响应信封：HTTP 200 + code != 200 一律转为 [ApiException]
 * 3. HTTP 401（写操作或刷新失败）→ 上报会话失效事件，UI 收到后回到配置向导
 * 4. IO 异常转译为用户可读消息
 */
class QinglongRepository(
    private val settingsStore: SettingsStore,
    private val secureStore: SecureStore,
    private val tokenManager: TokenManager,
    private val apiClient: ApiClient,
) {
    private val api: QinglongApi get() = apiClient.api

    // ------------------------------------------------------------ 内部：统一调用封装

    /** 需要非空 data 的调用 */
    private suspend fun <T> dataCall(block: suspend (QinglongApi) -> ApiResponse<T>): T {
        tokenManager.validToken()
        val response = try {
            block(api)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw e.toApiException()
        } catch (e: IOException) {
            throw ApiException("网络连接失败：请检查服务器地址与网络")
        }
        if (response.code == 200) {
            return response.data ?: throw ApiException("响应数据为空")
        }
        throw ApiException(response.message ?: "请求失败(${response.code})")
    }

    /** data 可空的调用（返回 Unit 语义） */
    private suspend fun unitCall(block: suspend (QinglongApi) -> ApiResponse<*>): Unit {
        tokenManager.validToken()
        val response = try {
            block(api)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw e.toApiException()
        } catch (e: IOException) {
            throw ApiException("网络连接失败：请检查服务器地址与网络")
        }
        if (response.code != 200) {
            throw ApiException(response.message ?: "请求失败(${response.code})")
        }
    }

    /** 非信封响应（日志内容、系统日志流） */
    private suspend fun <T> rawCall(block: suspend (QinglongApi) -> T): T {
        tokenManager.validToken()
        return try {
            block(api)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw e.toApiException()
        } catch (e: IOException) {
            throw ApiException("网络连接失败：请检查服务器地址与网络")
        }
    }

    private suspend fun HttpException.toApiException(): ApiException {
        if (code() == 401) {
            // 青龙 /open/ 中间件对「应用 scopes 未覆盖该路径」与「token 失效」都返回
            // HTTP 401，只能靠 errorBody 的 message 区分（express.ts 错误处理器）：
            //   暂无权限 / Access denied → 应用权限不足：换 token、重登都无用，
            //       绝不能上报会话失效把用户踢回向导（OPEN 模式误踢根因即在此）
            //   Token 已失效 / Token expired → 真正的会话过期：保持原行为
            // 账密模式走 /api/ JWT 校验、不涉及 scopes，过期提示保持不变。
            val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
            if (isScopePermissionError(body)) {
                Log.w(TAG, "open scope denied 401: $body")
                return ApiException(
                    "应用权限不足：该 OpenAPI 应用没有此功能所需权限。" +
                        "请到面板 → 应用设置中勾选所需权限后重试",
                )
            }
            tokenManager.reportSessionExpired()
            return ApiException("登录已过期，请重新登录")
        }
        // 其余 4xx/5xx：优先透传面板 errorBody 里的 message（如 envs 的 Joi 校验
        // 「"name" with value "测试" fails to match the required pattern」），
        // 让用户看到服务端的真实拒绝原因，而不是一句「服务器错误 400」。
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val serverMessage = parseServerMessage(body)
        return ApiException(serverMessage ?: "服务器错误 ${code()}")
    }

    /** 从青龙错误响应体提取 message：{"code":400,"message":"..."} 或 {"message":"..."} */
    private fun parseServerMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val json = com.google.gson.Gson().fromJson(body, com.google.gson.JsonObject::class.java)
            json?.get("message")?.asJsonPrimitive?.asString?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 401 报文是否属于「应用 scopes 权限不足」（青龙中英文面板） */
    private fun isScopePermissionError(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        return lower.contains("暂无权限") ||
            lower.contains("access denied") ||
            lower.contains("no permission") ||
            lower.contains("permission denied") ||
            lower.contains("insufficient permission")
    }

    // ------------------------------------------------------------ 配置与认证

    suspend fun serverConfig(): ServerConfig = settingsStore.current()

    suspend fun hasValidConfig(): Boolean = serverConfig().configured

    /** 应用授权模式激活：保存配置 → 重建客户端 → 换取 token → 校验数据端点可达 */
    suspend fun setupOpen(serverUrl: String, clientId: String, clientSecret: String): Result<Unit> =
        connectGuarded {
            val url = SettingsStore.normalizeUrl(serverUrl)
            if (url.isBlank()) error("服务器地址不能为空")
            if (clientId.isBlank()) error("client_id 不能为空")
            if (clientSecret.isBlank()) error("client_secret 不能为空")
            settingsStore.saveServer(url, AuthMode.OPEN, clientId.trim(), "")
                .also { secureStore.activeProfileId = it }
            secureStore.clientSecret = clientSecret.trim()
            secureStore.password = ""
            apiClient.configure(url, AuthMode.OPEN)
            tokenManager.activateOpen().getOrThrow()
            // fail-fast：换取 token 只证明 client_id/secret 正确；/open/ 中间件
            // 还会按应用 scopes 校验每个数据路径，权限不足即返回 401「暂无权限」。
            // 不在连接时暴露，用户就会「连上即被踢回向导」（落地页首个数据请求 401
            // 被误判为登录过期）。这里立即用真实数据请求验证，不可用则连接失败。
            verifyOpenDataAccess()
        }

    /**
     * OPEN 模式连接后校验 token 对数据端点真实可用：
     * 立即发起一个轻量真实请求（定时任务列表第 1 页 1 条，即 App 最核心数据）。
     *
     * ⚠️ 不要用 /open/dashboard/ 做探针：青龙 master 线面板的「应用设置」里
     * **没有 dashboard 权限项**（AppScope 仅 crons/envs/subscriptions/configs/
     * scripts/logs/dependencies/system 八种，develop 线才有 dashboard），
     * 导致 /open/dashboard/ 对任何应用都必然返回 401「暂无权限」——
     * 用户即使勾选全部权限也会被误判为权限不足而连不上（v1.0.2 实际事故）。
     * crons 是所有面板版本都可勾选、且响应可用 page/size 压到最小的探针。
     *
     * 权限不足 → 连接失败并给出中文指引，用户留在向导页修正权限，
     * 绝不会「连上又被踢下线」。仍在 [connectGuarded] 的 45s 护栏内。
     */
    private suspend fun verifyOpenDataAccess() {
        val response = try {
            api.crons(searchValue = null, page = 1, size = 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // 401「暂无权限」→ 权限不足（不上报会话失效）；「Token 已失效」→ 上报
            throw e.toApiException()
        } catch (e: IOException) {
            throw ApiException("网络连接失败：请检查服务器地址与网络")
        }
        if (response.code != 200) {
            throw ApiException(response.message ?: "连接校验失败(${response.code})")
        }
    }

    /** 账号密码模式激活（可能触发 2FA，见 [TwoFactorState]） */
    suspend fun setupAccount(serverUrl: String, username: String, password: String): Result<Unit> =
        connectGuarded {
            val url = SettingsStore.normalizeUrl(serverUrl)
            if (url.isBlank()) error("服务器地址不能为空")
            if (username.isBlank()) error("用户名不能为空")
            if (password.isBlank()) error("密码不能为空")
            settingsStore.saveServer(url, AuthMode.ACCOUNT, "", username.trim())
                .also { secureStore.activeProfileId = it }
            secureStore.password = password
            secureStore.clientSecret = ""
            apiClient.configure(url, AuthMode.ACCOUNT)
            tokenManager.login(username.trim(), password).getOrThrow()
            Unit
        }

    /**
     * 连接类操作（配置向导）统一加总超时护栏：
     * 无论慢网络、不可达地址还是任何异常挂起路径，都在有界时间内返回失败，
     * 保证 UI 的“连接中”状态必然解除——绝不无限转圈。
     */
    private suspend fun connectGuarded(block: suspend () -> Unit): Result<Unit> = try {
        Result.success(withTimeout(CONNECT_TIMEOUT_MS) { block() })
    } catch (e: TimeoutCancellationException) {
        Result.failure(
            ApiException(
                "连接超时（${CONNECT_TIMEOUT_MS / 1000} 秒）：地址或端口不可达。" +
                    "公网面板请确认 5700 端口已开放且手机网络可达；内网面板请确认手机与面板在同一网络",
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** 提交两步验证码 */
    suspend fun submitTwoFactor(code: String): Result<Unit> = connectGuarded {
        if (code.isBlank()) error("请输入验证码")
        tokenManager.loginWithCode(code.trim()).getOrThrow()
        Unit
    }

    /**
     * 用已持久化的配置与凭据重新连接（启动恢复失败后的手动兜底）：
     * 用户无需重新输入 client_secret / 密码，一键重试。
     */
    suspend fun reconnectSaved(): Result<Unit> = connectGuarded {
        val config = settingsStore.current()
        if (!config.configured) error("没有已保存的服务器配置")
        // 防御性同步：resetAll 后激活面板可能已重建，确保凭据读写落在正确前缀
        secureStore.activeProfileId = settingsStore.activeProfileId() ?: SecureStore.DEFAULT_PROFILE_ID
        apiClient.configure(config.serverUrl, config.authMode)
        when (config.authMode) {
            AuthMode.OPEN -> tokenManager.activateOpen().getOrThrow()
            AuthMode.ACCOUNT -> tokenManager.login(config.username, secureStore.password).getOrThrow()
        }
        Unit
    }

    /** 退出登录：清理凭据与配置，回到向导 */
    suspend fun logout(): Result<Unit> = runCatching {
        tokenManager.logout()
        settingsStore.clear()
        secureStore.clear()
        Unit
    }

    // ------------------------------------------------------------ 多面板管理（v1.1.0）

    suspend fun panels(): List<PanelProfile> = settingsStore.profiles()

    fun activePanelFlow(): Flow<PanelProfile?> = settingsStore.activeProfile

    suspend fun activePanel(): PanelProfile? = settingsStore.activeProfile()

    /**
     * 切换面板：各自保留登录态（token / 凭据按面板隔离），切换不重新登录；
     * 仅当目标面板会话已失效（token 过期且凭据重换失败）时才需重新连接。
     *
     * @return true = 会话就绪，UI 直达首页；false = 已上报会话失效事件，UI 回向导
     */
    suspend fun switchPanel(id: String): Result<Boolean> = runCatching {
        val target = settingsStore.profiles().firstOrNull { it.id == id }
            ?: error("面板不存在")
        settingsStore.setActiveProfile(id)
        secureStore.activeProfileId = id
        tokenManager.onActiveProfileChanged()
        apiClient.configure(target.serverUrl, target.authMode)
        val ready = tokenManager.restoreSessionToken() != null
        if (!ready) tokenManager.reportSessionExpired()
        ready
    }

    /**
     * 添加面板并连接（配置向导"添加面板"模式）。
     * 连接失败时自动清理新配置并完整回滚到原激活面板，不影响当前使用。
     */
    suspend fun addPanelAndConnect(
        name: String,
        serverUrl: String,
        authMode: AuthMode,
        clientId: String,
        clientSecret: String,
        username: String,
        password: String,
    ): Result<Unit> = connectGuarded {
        val url = SettingsStore.normalizeUrl(serverUrl)
        if (url.isBlank()) error("服务器地址不能为空")
        val previousId = settingsStore.activeProfileId()
        val previous = settingsStore.activeProfile()
        val id = settingsStore.addProfile(
            name = name.trim(),
            serverUrl = url,
            authMode = authMode,
            clientId = clientId.trim(),
            username = username.trim(),
        )
        // 凭据存储临时指向新面板：连接过程中的 token 落在新面板前缀下
        secureStore.activeProfileId = id
        try {
            when (authMode) {
                AuthMode.OPEN -> {
                    if (clientId.isBlank()) error("client_id 不能为空")
                    if (clientSecret.isBlank()) error("client_secret 不能为空")
                    secureStore.clientSecret = clientSecret.trim()
                    secureStore.password = ""
                    apiClient.configure(url, AuthMode.OPEN)
                    tokenManager.activateOpen().getOrThrow()
                    verifyOpenDataAccess()
                }
                AuthMode.ACCOUNT -> {
                    if (username.isBlank()) error("用户名不能为空")
                    if (password.isBlank()) error("密码不能为空")
                    secureStore.password = password
                    secureStore.clientSecret = ""
                    apiClient.configure(url, AuthMode.ACCOUNT)
                    tokenManager.login(username.trim(), password).getOrThrow()
                }
            }
            // 连接成功：激活新面板
            settingsStore.setActiveProfile(id)
            tokenManager.onActiveProfileChanged()
        } catch (t: Throwable) {
            // 回滚：删除新面板配置与凭据，恢复原激活面板的客户端与 token 缓存。
            // 协程已取消（超时 / 取消）时挂起函数会立刻再次抛 CancellationException，
            // 必须包在 NonCancellable 里保证回滚完整执行。
            withContext(NonCancellable) {
                settingsStore.removeProfile(id)
                secureStore.clearProfile(id)
                secureStore.activeProfileId = previousId ?: SecureStore.DEFAULT_PROFILE_ID
                if (previous != null) {
                    apiClient.configure(previous.serverUrl, previous.authMode)
                } else {
                    apiClient.configure("http://127.0.0.1:5700/", AuthMode.OPEN)
                }
                tokenManager.onActiveProfileChanged()
            }
            throw t
        }
        Unit
    }

    /** 删除面板配置与其凭据；当前激活面板不可删除（需先切换） */
    suspend fun removePanel(id: String): Result<Unit> = runCatching {
        if (settingsStore.activeProfileId() == id) {
            error("当前面板正在使用，请先切换到其他面板")
        }
        settingsStore.removeProfile(id)
        secureStore.clearProfile(id)
        Unit
    }

    // ------------------------------------------------------------ 仪表盘

    suspend fun overview(): Result<DashboardOverview> =
        runCatching { dataCall { it.overview() } }

    suspend fun runtime(): Result<DashboardRuntime> =
        runCatching { dataCall { it.runtime() } }

    suspend fun dashboardSystem(): Result<DashboardSystem> =
        runCatching { dataCall { it.dashboardSystem() } }

    suspend fun trend(days: Int = 7): Result<List<TrendPoint>> =
        runCatching { dataCall { it.trend(days) } ?: emptyList() }

    // ------------------------------------------------------------ 定时任务

    suspend fun crons(searchValue: String?, page: Int, size: Int): Result<PagedData<Cron>> =
        runCatching { dataCall { it.crons(searchValue, page, size) } }

    suspend fun createCron(payload: CronPayload): Result<Unit> =
        runCatching { unitCall { it.createCron(payload) } }

    suspend fun updateCron(payload: CronPayload): Result<Unit> =
        runCatching { unitCall { it.updateCron(payload) } }

    suspend fun deleteCrons(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.deleteCrons(ids) } }

    suspend fun runCrons(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.runCrons(ids) } }

    suspend fun stopCrons(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.stopCrons(ids) } }

    suspend fun enableCrons(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.enableCrons(ids) } }

    suspend fun disableCrons(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.disableCrons(ids) } }

    /** 任务日志；data 为空时返回空串而非报错 */
    suspend fun cronLog(id: Long, tail: Boolean = true, limit: Int = 262144): Result<LogChunk> =
        runCatching {
            rawCall { it.cronLog(id, tail, limit) }
        }

    // ------------------------------------------------------------ 环境变量

    suspend fun envs(searchValue: String? = null): Result<List<Env>> =
        runCatching { dataCall { it.envs(searchValue) } ?: emptyList() }

    suspend fun createEnv(name: String, value: String, remarks: String?): Result<Unit> =
        runCatching {
            unitCall { it.createEnvs(listOf(EnvPayload(name = name.trim(), value = value.trim(), remarks = remarks?.trim()?.ifBlank { null }))) }
        }

    suspend fun updateEnv(id: Long, name: String, value: String, remarks: String?): Result<Unit> =
        runCatching {
            unitCall { it.updateEnv(EnvPayload(id = id, name = name.trim(), value = value.trim(), remarks = remarks?.trim()?.ifBlank { null })) }
        }

    suspend fun deleteEnvs(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.deleteEnvs(ids) } }

    suspend fun enableEnvs(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.enableEnvs(ids) } }

    suspend fun disableEnvs(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.disableEnvs(ids) } }

    suspend fun pinEnvs(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.pinEnvs(ids) } }

    suspend fun unpinEnvs(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.unpinEnvs(ids) } }

    suspend fun moveEnv(id: Long, fromIndex: Int, toIndex: Int): Result<Unit> =
        runCatching { unitCall { it.moveEnv(id, MovePayload(fromIndex, toIndex)) } }

    /** 上传环境变量 JSON 文件 */
    suspend fun uploadEnvFile(fileName: String, bytes: ByteArray): Result<Unit> = runCatching {
        tokenManager.validToken()
        val part = MultipartBody.Part.createFormData(
            "env", fileName,
            bytes.toRequestBody("application/json".toMediaType()),
        )
        val response = try {
            api.uploadEnvFile(part)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw e.toApiException()
        } catch (e: IOException) {
            throw ApiException("网络连接失败：请检查服务器地址与网络")
        }
        if (response.code != 200) throw ApiException(response.message ?: "上传失败(${response.code})")
        Unit
    }

    // ------------------------------------------------------------ 脚本管理

    suspend fun scripts(path: String? = null): Result<List<ScriptFile>> =
        runCatching { dataCall { it.scripts(path) } ?: emptyList() }

    suspend fun scriptDetail(path: String?, file: String): Result<String> =
        runCatching { dataCall { it.scriptDetail(path, file) } }

    /** 纯内容上传（无文件流） */
    suspend fun uploadScriptContent(fileName: String, path: String, content: String, directory: String?): Result<Unit> =
        runCatching {
            tokenManager.validToken()
            val response = try {
                api.uploadScript(
                    file = null,
                    filename = fileName.toRequestBody(PLAIN),
                    path = path.toRequestBody(PLAIN),
                    content = content.toRequestBody(PLAIN),
                    directory = directory?.toRequestBody(PLAIN),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                throw e.toApiException()
            } catch (e: IOException) {
                throw ApiException("网络连接失败：请检查服务器地址与网络")
            }
            if (response.code != 200) throw ApiException(response.message ?: "上传失败(${response.code})")
            Unit
        }

    /** 文件流上传（本地文件路径场景） */
    suspend fun uploadScriptFile(fileName: String, path: String, bytes: ByteArray, directory: String?): Result<Unit> =
        runCatching {
            tokenManager.validToken()
            val filePart = MultipartBody.Part.createFormData(
                "file", fileName,
                bytes.toRequestBody("application/octet-stream".toMediaType()),
            )
            val response = try {
                api.uploadScript(
                    file = filePart,
                    filename = fileName.toRequestBody(PLAIN),
                    path = path.toRequestBody(PLAIN),
                    content = null,
                    directory = directory?.toRequestBody(PLAIN),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                throw e.toApiException()
            } catch (e: IOException) {
                throw ApiException("网络连接失败：请检查服务器地址与网络")
            }
            if (response.code != 200) throw ApiException(response.message ?: "上传失败(${response.code})")
            Unit
        }

    suspend fun saveScript(filename: String, path: String?, content: String): Result<Unit> =
        runCatching { unitCall { it.saveScript(ScriptSavePayload(filename, path, content)) } }

    suspend fun deleteScript(filename: String, path: String?, type: String?): Result<Unit> =
        runCatching { unitCall { it.deleteScript(ScriptDeletePayload(filename, path, type)) } }

    suspend fun runScript(filename: String, path: String?): Result<Unit> =
        runCatching { unitCall { it.runScript(ScriptRunPayload(filename, path)) } }

    suspend fun stopScript(filename: String, path: String?, pid: Long?): Result<Unit> =
        runCatching { unitCall { it.stopScript(ScriptStopPayload(filename, path, pid)) } }

    suspend fun renameScript(filename: String, path: String?, newFilename: String): Result<Unit> =
        runCatching { unitCall { it.renameScript(ScriptRenamePayload(filename, path, newFilename)) } }

    // ------------------------------------------------------------ 订阅管理

    suspend fun subscriptions(searchValue: String? = null): Result<List<Subscription>> =
        runCatching { dataCall { it.subscriptions(searchValue) } ?: emptyList() }

    suspend fun createSubscription(payload: SubscriptionPayload): Result<Unit> =
        runCatching { unitCall { it.createSubscription(payload) } }

    suspend fun updateSubscription(payload: SubscriptionPayload): Result<Unit> =
        runCatching { unitCall { it.updateSubscription(payload) } }

    suspend fun deleteSubscriptions(ids: List<Long>, force: Boolean = false): Result<Unit> =
        runCatching { unitCall { it.deleteSubscriptions(ids, force) } }

    suspend fun runSubscriptions(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.runSubscriptions(ids) } }

    suspend fun stopSubscriptions(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.stopSubscriptions(ids) } }

    suspend fun enableSubscriptions(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.enableSubscriptions(ids) } }

    suspend fun disableSubscriptions(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.disableSubscriptions(ids) } }

    suspend fun subscriptionLog(id: Long, tail: Boolean = true, limit: Int = 262144): Result<LogChunk> =
        runCatching { rawCall { it.subscriptionLog(id, tail, limit) } }

    suspend fun subscriptionLogFiles(id: Long): Result<List<SubLogFile>> =
        runCatching { dataCall { it.subscriptionLogFiles(id) } ?: emptyList() }

    // ------------------------------------------------------------ 依赖管理

    suspend fun dependencies(searchValue: String? = null): Result<List<Dependence>> =
        runCatching { dataCall { it.dependencies(searchValue, null) } ?: emptyList() }

    /** 安装依赖（同名多行按行拆分） */
    suspend fun installDependencies(names: String, type: Int): Result<Unit> = runCatching {
        val list = names.split("\n", ",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { DependencePayload(it, type) }
        if (list.isEmpty()) error("请输入依赖名称")
        unitCall { it.installDependencies(list) }
    }

    suspend fun deleteDependencies(ids: List<Long>, force: Boolean = false): Result<Unit> =
        runCatching {
            if (force) unitCall { it.forceDeleteDependencies(ids) }
            else unitCall { it.deleteDependencies(ids) }
        }

    suspend fun reinstallDependencies(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.reinstallDependencies(ids) } }

    suspend fun cancelDependencies(ids: List<Long>): Result<Unit> =
        runCatching { unitCall { it.cancelDependencies(ids) } }

    // ------------------------------------------------------------ 配置文件

    suspend fun configFiles(): Result<List<ConfigFile>> =
        runCatching { dataCall { it.configFiles() } ?: emptyList() }

    suspend fun configDetail(path: String): Result<String> =
        runCatching { dataCall { it.configDetail(path) } }

    suspend fun saveConfig(name: String, content: String): Result<Unit> =
        runCatching { unitCall { it.saveConfig(ConfigSavePayload(name, content)) } }

    // ------------------------------------------------------------ 日志管理

    suspend fun logs(): Result<List<ScriptFile>> =
        runCatching { dataCall { it.logs() } ?: emptyList() }

    suspend fun logDetail(path: String?, file: String): Result<LogChunk> =
        runCatching { rawCall { it.logDetail(path, file) } }

    suspend fun deleteLog(filename: String, path: String?, type: String?): Result<Unit> =
        runCatching { unitCall { it.deleteLog(ScriptDeletePayload(filename, path, type)) } }

    // ------------------------------------------------------------ 系统

    suspend fun systemInfo(): Result<SystemInfo> =
        runCatching { dataCall { it.systemInfo() } }

    suspend fun systemConfig(): Result<SystemConfig> =
        runCatching { dataCall { it.systemConfig() } }

    suspend fun updateLogRemoveFrequency(value: Long): Result<Unit> =
        runCatching { unitCall { it.updateLogRemoveFrequency(NumberPayload(value)) } }

    suspend fun updateCronConcurrency(value: Long): Result<Unit> =
        runCatching { unitCall { it.updateCronConcurrency(NumberPayload(value)) } }

    suspend fun updateDependenceProxy(value: String): Result<Unit> =
        runCatching { unitCall { it.updateDependenceProxy(TextPayload(value)) } }

    suspend fun updateNodeMirror(value: String): Result<Unit> =
        runCatching { unitCall { it.updateNodeMirror(TextPayload(value)) } }

    suspend fun updatePythonMirror(value: String): Result<Unit> =
        runCatching { unitCall { it.updatePythonMirror(TextPayload(value)) } }

    suspend fun updateLinuxMirror(value: String): Result<Unit> =
        runCatching { unitCall { it.updateLinuxMirror(TextPayload(value)) } }

    suspend fun updatePanelTitle(value: String): Result<Unit> =
        runCatching { unitCall { it.updatePanelTitle(TextPayload(value)) } }

    suspend fun notify(title: String, content: String): Result<Unit> =
        runCatching { unitCall { it.notify(NotifyPayload(title, content)) } }

    /** 系统日志为纯文本流，限制大小后一次性读取 */
    suspend fun systemLog(limit: Int = 524288): Result<String> = runCatching {
        rawCall { it.systemLog(limit) }.string()
    }

    companion object {
        private val PLAIN = "text/plain".toMediaType()
        private const val TAG = "QinglongRepository"

        /** 连接向导总超时：超过即向用户报错，绝不无限转圈 */
        private const val CONNECT_TIMEOUT_MS = 45_000L

        /** 统一提取 Result 失败消息 */
        fun errorMessage(t: Throwable): String {
            Log.w(TAG, "repo error", t)
            return when (t) {
                is ApiException -> t.message ?: "请求失败"
                is TwoFactorRequiredException -> "需要两步验证"
                is TimeoutCancellationException -> "连接超时：请检查地址端口与网络后重试"
                is SocketTimeoutException -> "连接超时：面板响应太慢或网络不通，请检查地址端口与网络"
                is UnknownHostException -> "域名解析失败：请检查服务器地址是否输入正确"
                is ConnectException -> "无法连接到服务器：请确认面板已启动、地址端口正确（公网面板需开放 5700 端口）"
                is SSLException -> "安全连接失败：自签名 https 证书需先在系统浏览器中信任后再连接"
                is HttpException -> when (t.code()) {
                    401 -> "认证被拒绝（401）：请检查 client_id / client_secret 是否正确"
                    403 -> "没有访问权限（403）：请在面板 OpenAPI 应用中勾选所需权限"
                    404 -> "接口不存在（404）：请检查面板地址是否多写或少写了路径"
                    else -> "服务器返回 HTTP ${t.code()}"
                }
                is IOException -> "网络错误：${t.message ?: "请检查网络与面板地址"}"
                else -> t.message ?: "未知错误"
            }
        }
    }
}
