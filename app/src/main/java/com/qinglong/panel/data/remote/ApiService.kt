package com.qinglong.panel.data.remote

import com.qinglong.panel.data.model.ApiResponse
import com.qinglong.panel.data.model.ConfigFile
import com.qinglong.panel.data.model.ConfigInfo
import com.qinglong.panel.data.model.ConfigSavePayload
import com.qinglong.panel.data.model.Cron
import com.qinglong.panel.data.model.CronPayload
import com.qinglong.panel.data.model.DashboardOverview
import com.qinglong.panel.data.model.DashboardRuntime
import com.qinglong.panel.data.model.DashboardSystem
import com.qinglong.panel.data.model.Dependence
import com.qinglong.panel.data.model.DependencePayload
import com.qinglong.panel.data.model.Env
import com.qinglong.panel.data.model.EnvPayload
import com.qinglong.panel.data.model.LabelPayload
import com.qinglong.panel.data.model.LogChunk
import com.qinglong.panel.data.model.LoginData
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
import com.qinglong.panel.data.model.TokenData
import com.qinglong.panel.data.model.TrendPoint
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 青龙面板模块接口。
 *
 * 路径为相对路径（如 "crons"），由 [PrefixRewriteInterceptor] 根据认证模式
 * 自动前缀为 /open（应用授权）或 /api（账号登录），两者服务端路由完全等价。
 */
interface QinglongApi {

    // ------------------------------------------------------------ 仪表盘
    @GET("dashboard/overview")
    suspend fun overview(): ApiResponse<DashboardOverview>

    @GET("dashboard/runtime")
    suspend fun runtime(): ApiResponse<DashboardRuntime>

    @GET("dashboard/system")
    suspend fun dashboardSystem(): ApiResponse<DashboardSystem>

    @GET("dashboard/trend")
    suspend fun trend(@Query("days") days: Int = 7): ApiResponse<List<TrendPoint>>

    // ------------------------------------------------------------ 定时任务
    @GET("crons")
    suspend fun crons(
        @Query("searchValue") searchValue: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResponse<PagedData<Cron>>

    @POST("crons")
    suspend fun createCron(@Body body: CronPayload): ApiResponse<Cron>

    @PUT("crons")
    suspend fun updateCron(@Body body: CronPayload): ApiResponse<Cron>

    @HTTP(method = "DELETE", path = "crons", hasBody = true)
    suspend fun deleteCrons(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("crons/run")
    suspend fun runCrons(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("crons/stop")
    suspend fun stopCrons(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("crons/enable")
    suspend fun enableCrons(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("crons/disable")
    suspend fun disableCrons(@Body ids: List<Long>): ApiResponse<Unit>

    @GET("crons/{id}/log")
    suspend fun cronLog(
        @Path("id") id: Long,
        @Query("tail") tail: Boolean = true,
        @Query("limit") limit: Int = 262144,
    ): LogChunk

    @POST("crons/labels")
    suspend fun addCronLabels(@Body body: LabelPayload): ApiResponse<Unit>

    // ------------------------------------------------------------ 环境变量
    @GET("envs")
    suspend fun envs(@Query("searchValue") searchValue: String?): ApiResponse<List<Env>>

    @POST("envs")
    suspend fun createEnvs(@Body body: List<EnvPayload>): ApiResponse<List<Env>>

    @PUT("envs")
    suspend fun updateEnv(@Body body: EnvPayload): ApiResponse<Env>

    @HTTP(method = "DELETE", path = "envs", hasBody = true)
    suspend fun deleteEnvs(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("envs/enable")
    suspend fun enableEnvs(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("envs/disable")
    suspend fun disableEnvs(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("envs/pin")
    suspend fun pinEnvs(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("envs/unpin")
    suspend fun unpinEnvs(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("envs/{id}/move")
    suspend fun moveEnv(@Path("id") id: Long, @Body body: MovePayload): ApiResponse<Env>

    /** 上传环境变量文件（JSON，字段名 env） */
    @Multipart
    @POST("envs/upload")
    suspend fun uploadEnvFile(
        @Part file: MultipartBody.Part,
    ): ApiResponse<List<Env>>

    // ------------------------------------------------------------ 脚本管理
    @GET("scripts")
    suspend fun scripts(@Query("path") path: String?): ApiResponse<List<ScriptFile>>

    @GET("scripts/detail")
    suspend fun scriptDetail(
        @Query("path") path: String?,
        @Query("file") file: String,
    ): ApiResponse<String>

    @Multipart
    @POST("scripts")
    suspend fun uploadScript(
        @Part file: MultipartBody.Part?,
        @Part("filename") filename: RequestBody,
        @Part("path") path: RequestBody,
        @Part("content") content: RequestBody?,
        @Part("directory") directory: RequestBody?,
    ): ApiResponse<Unit>

    @PUT("scripts")
    suspend fun saveScript(@Body body: ScriptSavePayload): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "scripts", hasBody = true)
    suspend fun deleteScript(@Body body: ScriptDeletePayload): ApiResponse<Unit>

    @PUT("scripts/run")
    suspend fun runScript(@Body body: ScriptRunPayload): ApiResponse<Unit>

    @PUT("scripts/stop")
    suspend fun stopScript(@Body body: ScriptStopPayload): ApiResponse<Unit>

    @PUT("scripts/rename")
    suspend fun renameScript(@Body body: ScriptRenamePayload): ApiResponse<Unit>

    // ------------------------------------------------------------ 订阅管理
    @GET("subscriptions")
    suspend fun subscriptions(@Query("searchValue") searchValue: String?): ApiResponse<List<Subscription>>

    @POST("subscriptions")
    suspend fun createSubscription(@Body body: SubscriptionPayload): ApiResponse<Subscription>

    @PUT("subscriptions")
    suspend fun updateSubscription(@Body body: SubscriptionPayload): ApiResponse<Subscription>

    @HTTP(method = "DELETE", path = "subscriptions", hasBody = true)
    suspend fun deleteSubscriptions(
        @Body ids: List<Long>,
        @Query("force") force: Boolean? = null,
    ): ApiResponse<Unit>

    @PUT("subscriptions/run")
    suspend fun runSubscriptions(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("subscriptions/stop")
    suspend fun stopSubscriptions(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("subscriptions/enable")
    suspend fun enableSubscriptions(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("subscriptions/disable")
    suspend fun disableSubscriptions(@Body ids: List<Long>): ApiResponse<Unit>

    @GET("subscriptions/{id}/log")
    suspend fun subscriptionLog(
        @Path("id") id: Long,
        @Query("tail") tail: Boolean = true,
        @Query("limit") limit: Int = 262144,
    ): LogChunk

    @GET("subscriptions/{id}/logs")
    suspend fun subscriptionLogFiles(@Path("id") id: Long): ApiResponse<List<SubLogFile>>

    // ------------------------------------------------------------ 依赖管理
    @GET("dependencies")
    suspend fun dependencies(
        @Query("searchValue") searchValue: String?,
        @Query("type") type: String?,
    ): ApiResponse<List<Dependence>>

    @POST("dependencies")
    suspend fun installDependencies(@Body body: List<DependencePayload>): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "dependencies", hasBody = true)
    suspend fun deleteDependencies(@Body ids: List<Long>): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "dependencies/force", hasBody = true)
    suspend fun forceDeleteDependencies(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("dependencies/reinstall")
    suspend fun reinstallDependencies(@Body ids: List<Long>): ApiResponse<Unit>

    @PUT("dependencies/cancel")
    suspend fun cancelDependencies(@Body ids: List<Long>): ApiResponse<Unit>

    // ------------------------------------------------------------ 配置文件
    @GET("configs/files")
    suspend fun configFiles(): ApiResponse<List<ConfigFile>>

    @GET("configs/detail")
    suspend fun configDetail(@Query("path") path: String): ApiResponse<String>

    @POST("configs/save")
    suspend fun saveConfig(@Body body: ConfigSavePayload): ApiResponse<Unit>

    // ------------------------------------------------------------ 日志管理
    @GET("logs")
    suspend fun logs(): ApiResponse<List<ScriptFile>>

    @GET("logs/detail")
    suspend fun logDetail(
        @Query("path") path: String?,
        @Query("file") file: String,
        @Query("tail") tail: Boolean = true,
        @Query("limit") limit: Int = 262144,
    ): LogChunk

    @HTTP(method = "DELETE", path = "logs", hasBody = true)
    suspend fun deleteLog(@Body body: ScriptDeletePayload): ApiResponse<Unit>

    // ------------------------------------------------------------ 系统
    @GET("system")
    suspend fun systemInfo(): ApiResponse<SystemInfo>

    @GET("system/config")
    suspend fun systemConfig(): ApiResponse<SystemConfig>

    @PUT("system/config/log-remove-frequency")
    suspend fun updateLogRemoveFrequency(@Body body: NumberPayload): ApiResponse<Unit>

    @PUT("system/config/cron-concurrency")
    suspend fun updateCronConcurrency(@Body body: NumberPayload): ApiResponse<Unit>

    @PUT("system/config/dependence-proxy")
    suspend fun updateDependenceProxy(@Body body: TextPayload): ApiResponse<Unit>

    @PUT("system/config/node-mirror")
    suspend fun updateNodeMirror(@Body body: TextPayload): ApiResponse<Unit>

    @PUT("system/config/python-mirror")
    suspend fun updatePythonMirror(@Body body: TextPayload): ApiResponse<Unit>

    @PUT("system/config/linux-mirror")
    suspend fun updateLinuxMirror(@Body body: TextPayload): ApiResponse<Unit>

    @PUT("system/config/panel-title")
    suspend fun updatePanelTitle(@Body body: TextPayload): ApiResponse<Unit>

    @PUT("system/notify")
    suspend fun notify(@Body body: NotifyPayload): ApiResponse<Unit>

    /** 系统日志为纯文本流响应 */
    @GET("system/log")
    suspend fun systemLog(@Query("limit") limit: Int = 524288): ResponseBody
}

/** 认证接口（不携带业务 token） */
interface AuthApi {

    @GET("open/auth/token")
    suspend fun openToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
    ): ApiResponse<TokenData>

    @POST("api/user/login")
    suspend fun login(
        @Body body: Map<String, String>,
    ): ApiResponse<LoginData>

    @PUT("api/user/two-factor/login")
    suspend fun twoFactorLogin(
        @Body body: Map<String, String>,
    ): ApiResponse<LoginData>
}
