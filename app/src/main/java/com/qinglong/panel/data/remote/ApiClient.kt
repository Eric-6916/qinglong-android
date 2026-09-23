package com.qinglong.panel.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * labels 字段在青龙后端可能以数组或 JSON 字符串两种形式返回，统一适配为 List<String>。
 */
private class LabelsAdapter : TypeAdapter<List<String>>() {
    override fun write(out: JsonWriter, value: List<String>?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginArray()
        value.forEach { out.value(it) }
        out.endArray()
    }

    override fun read(reader: JsonReader): List<String> {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                emptyList()
            }
            JsonToken.BEGIN_ARRAY -> {
                val list = mutableListOf<String>()
                reader.beginArray()
                while (reader.hasNext()) list += reader.nextString()
                reader.endArray()
                list
            }
            JsonToken.STRING -> {
                val raw = reader.nextString()
                runCatching {
                    GsonBuilder().create().fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
                }.getOrElse { emptyList() }
            }
            else -> {
                reader.skipValue()
                emptyList()
            }
        }
    }
}

fun provideGson() = GsonBuilder()
    .registerTypeAdapter(object : TypeToken<List<String>>() {}.type, LabelsAdapter())
    .setLenient()
    .create()

/** 根据认证模式把相对路径改写为 /open/... 或 /api/... */
class PrefixRewriteInterceptor(private val prefixProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val prefix = prefixProvider()
        if (prefix.isEmpty() || path == prefix || path.startsWith("$prefix/")) {
            return chain.proceed(request)
        }
        val newUrl = request.url.newBuilder().encodedPath("$prefix$path").build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

/**
 * 附加 Bearer Token。认证端点（换 token / 登录 / 2FA 登录）绝不附带：
 * 青龙 /open/ 中间件按路径段取 key 校验 scope，auth 不在任何应用的 scopes 中，
 * 带陈旧 token 请求 /open/auth/token 会被判 401（旧版由此触发 authenticator 死锁）。
 *
 * 实测证据（666nb.top:1700 真机面板，2026-09-20）：
 * - GET  /open/auth/token  无 Authorization        → HTTP 200 {code:400 凭据错误}
 * - GET  /open/auth/token  带无效 Bearer           → HTTP 401 {code:401 "Token 已失效"}
 * - POST /api/user/login   无 Authorization        → HTTP 200 {code:400 用户名密码错误}
 * - POST /api/user/login   带无效 Bearer           → HTTP 401 {code:401 "Token 已失效"}
 * 即：认证端点只要带了无效 Bearer，两种模式都会 401——与凭据是否正确无关。
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isAuthEndpoint = path.endsWith("/open/auth/token") ||
            path.endsWith("/user/login") ||
            path.endsWith("/two-factor/login")
        if (isAuthEndpoint) {
            // 硬保险：即便请求因任何原因已带 Authorization，也强制剥掉再放行
            return chain.proceed(request.newBuilder().removeHeader("Authorization").build())
        }
        val token = tokenProvider()
        val builder = request.newBuilder()
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return chain.proceed(builder.build())
    }
}

/** 401 时刷新 token 并重试；仅重试 GET（幂等）请求，写操作零自动重试 */
class TokenAuthenticator(private val tokenManager: TokenManager) : okhttp3.Authenticator {
    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        if (response.request.method != "GET") return null
        if (response.priorCount() >= 2) return null
        // 死锁防护：绝不能在此阻塞等待 TokenManager 的刷新锁——调用方协程可能
        // 正持锁等待本请求的响应（Mutex 不可重入），阻塞即死锁、UI 无限转圈。
        // refreshTokenForAuthenticator 内部为非阻塞抢锁，抢不到即放弃本次自动刷新，
        // 让请求以 401 正常失败，由上层转译为用户可读错误。
        val newToken = kotlinx.coroutines.runBlocking {
            runCatching { tokenManager.refreshTokenForAuthenticator() }.getOrNull()
        } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $newToken").build()
    }
}

private fun Response.priorCount(): Int {
    var count = 1
    var prior = priorResponse
    while (prior != null) {
        count++
        prior = prior.priorResponse
    }
    return count
}

/**
 * 网络客户端。服务器地址 / 认证模式变化时调用 [configure] 重建 Retrofit。
 */
class ApiClient(
    private val tokenManager: TokenManager,
    private val isDebug: Boolean,
) {
    @Volatile
    private var retrofit: Retrofit = buildRetrofit("http://127.0.0.1:5700/", "/open")

    @Volatile
    var api: QinglongApi = retrofit.create(QinglongApi::class.java)
        private set

    @Volatile
    var authApi: AuthApi = retrofit.create(AuthApi::class.java)
        private set

    @Volatile
    private var prefix: String = "/open"

    @Volatile
    private var configuredBase: String = ""

    fun configure(serverUrl: String, mode: com.qinglong.panel.data.local.AuthMode) {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val newPrefix = if (mode == com.qinglong.panel.data.local.AuthMode.OPEN) "/open" else "/api"
        if (base == configuredBase && newPrefix == prefix) return
        configuredBase = base
        prefix = newPrefix
        retrofit = buildRetrofit(base, prefix)
        api = retrofit.create(QinglongApi::class.java)
        authApi = retrofit.create(AuthApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String, prefix: String): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(PrefixRewriteInterceptor { prefix })
            .addInterceptor(AuthInterceptor { tokenManager.cachedToken() })
            .authenticator(TokenAuthenticator(tokenManager))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (isDebug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(provideGson()))
            .build()
    }
}
