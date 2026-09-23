package com.qinglong.panel.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 认证方式 */
enum class AuthMode(val key: String) {
    /** OpenAPI 应用授权（client_id / client_secret） */
    OPEN("open"),

    /** 面板账号密码登录，推荐 */
    ACCOUNT("account");

    companion object {
        fun from(key: String?): AuthMode = entries.firstOrNull { it.key == key } ?: OPEN
    }
}

/** 主题模式：默认浅色，可在设置页切换深色 */
enum class ThemeMode(val key: String) {
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

/** 非敏感配置（凭据类信息存放于 SecureStore） */
data class ServerConfig(
    val serverUrl: String = "",
    val authMode: AuthMode = AuthMode.OPEN,
    val clientId: String = "",
    val username: String = "",
) {
    val configured: Boolean
        get() = serverUrl.isNotBlank() &&
            if (authMode == AuthMode.OPEN) clientId.isNotBlank() else username.isNotBlank()
}

/**
 * 青龙面板配置（多面板，v1.1.0）。
 *
 * 非敏感字段（地址 / 认证方式 / client_id / 用户名）存 DataStore 的 profiles_json；
 * client_secret / 密码 / token 存 SecureStore，按面板 ID 加前缀隔离，
 * 因此每个面板独立保留登录态，切换面板无需重新登录。
 */
data class PanelProfile(
    val id: String,
    val name: String,
    val serverUrl: String = "",
    val authMode: AuthMode = AuthMode.ACCOUNT,
    val clientId: String = "",
    val username: String = "",
) {
    val config: ServerConfig
        get() = ServerConfig(serverUrl, authMode, clientId, username)

    val configured: Boolean
        get() = config.configured
}

private val Context.settingsDataStore by preferencesDataStore(name = "ql_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        // v1.1.0 多面板键
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        val THEME_MODE = stringPreferencesKey("theme_mode")

        // 旧版单配置键（ensureInitialized 迁移后移除，仅向前兼容）
        val SERVER_URL = stringPreferencesKey("server_url")
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val CLIENT_ID = stringPreferencesKey("client_id")
        val USERNAME = stringPreferencesKey("username")
    }

    // ------------------------------------------------------------ 面板列表与激活项

    /** 全部面板配置（非敏感字段） */
    val profiles: Flow<List<PanelProfile>> = context.settingsDataStore.data.map { prefs ->
        decodeProfiles(prefs[Keys.PROFILES_JSON])
    }

    /** 当前激活面板 ID */
    val activeProfileId: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PROFILE_ID]
    }

    /** 当前激活面板（active 失效时回落第一个，保证 UI 不出现空档） */
    val activeProfile: Flow<PanelProfile?> =
        combine(profiles, activeProfileId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }

    /** 当前激活面板的服务器配置（兼容旧调用点，单一数据源） */
    val config: Flow<ServerConfig> = activeProfile.map { it?.config ?: ServerConfig() }

    /** 主题模式 Flow（默认浅色） */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        ThemeMode.from(prefs[Keys.THEME_MODE])
    }

    // ------------------------------------------------------------ 一次性读取

    suspend fun profiles(): List<PanelProfile> {
        var result = emptyList<PanelProfile>()
        context.settingsDataStore.data.first { prefs ->
            result = decodeProfiles(prefs[Keys.PROFILES_JSON])
            true
        }
        return result
    }

    suspend fun activeProfileId(): String? {
        var result: String? = null
        context.settingsDataStore.data.first { prefs ->
            result = prefs[Keys.ACTIVE_PROFILE_ID]
            true
        }
        return result
    }

    suspend fun activeProfile(): PanelProfile? {
        val id = activeProfileId()
        return profiles().firstOrNull { it.id == id } ?: profiles().firstOrNull()
    }

    suspend fun current(): ServerConfig = activeProfile()?.config ?: ServerConfig()

    /** 读取主题模式；无记录时默认浅色 */
    suspend fun currentThemeMode(): ThemeMode {
        var result = ThemeMode.LIGHT
        context.settingsDataStore.data.first { prefs ->
            result = ThemeMode.from(prefs[Keys.THEME_MODE])
            true
        }
        return result
    }

    // ------------------------------------------------------------ 启动规范化与迁移

    /**
     * 启动规范化（幂等，每次冷启动调用）：
     * 1. 旧版单配置（server_url 等旧键）迁移为 id=[LEGACY_PROFILE_ID] 的默认面板；
     * 2. 确保 active_profile_id 指向存在的面板。
     *
     * @return 发生旧配置迁移时返回迁移出的面板 ID；调用方需把 SecureStore 旧密钥
     *   复制到该面板的隔离前缀下（见 SecureStore.migrateLegacyTo）
     */
    suspend fun ensureInitialized(): String? {
        var migratedId: String? = null
        context.settingsDataStore.edit { prefs ->
            var list = decodeProfiles(prefs[Keys.PROFILES_JSON])
            if (list.isEmpty()) {
                val legacyUrl = prefs[Keys.SERVER_URL]
                if (!legacyUrl.isNullOrBlank()) {
                    val profile = PanelProfile(
                        id = LEGACY_PROFILE_ID,
                        name = deriveName(legacyUrl),
                        serverUrl = legacyUrl,
                        authMode = AuthMode.from(prefs[Keys.AUTH_MODE]),
                        clientId = prefs[Keys.CLIENT_ID].orEmpty(),
                        username = prefs[Keys.USERNAME].orEmpty(),
                    )
                    list = listOf(profile)
                    prefs[Keys.PROFILES_JSON] = encodeProfiles(list)
                    prefs.remove(Keys.SERVER_URL)
                    prefs.remove(Keys.AUTH_MODE)
                    prefs.remove(Keys.CLIENT_ID)
                    prefs.remove(Keys.USERNAME)
                    migratedId = LEGACY_PROFILE_ID
                }
            }
            val activeId = prefs[Keys.ACTIVE_PROFILE_ID]
            if (list.isNotEmpty() && (activeId.isNullOrBlank() || list.none { it.id == activeId })) {
                prefs[Keys.ACTIVE_PROFILE_ID] = list.first().id
            }
        }
        return migratedId
    }

    // ------------------------------------------------------------ 面板增删改查

    /** 新增面板（不切换激活项）；返回新面板 ID */
    suspend fun addProfile(
        name: String,
        serverUrl: String,
        authMode: AuthMode,
        clientId: String,
        username: String,
    ): String {
        val id = UUID.randomUUID().toString().take(8)
        context.settingsDataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.PROFILES_JSON]).toMutableList()
            list.add(
                PanelProfile(
                    id = id,
                    name = name.trim().ifBlank { deriveName(serverUrl) },
                    serverUrl = normalizeUrl(serverUrl),
                    authMode = authMode,
                    clientId = clientId.trim(),
                    username = username.trim(),
                ),
            )
            prefs[Keys.PROFILES_JSON] = encodeProfiles(list)
        }
        return id
    }

    /** 更新面板字段（按 ID） */
    suspend fun updateProfile(profile: PanelProfile) {
        context.settingsDataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.PROFILES_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) {
                list[idx] = profile.copy(
                    name = profile.name.trim().ifBlank { deriveName(profile.serverUrl) },
                    serverUrl = normalizeUrl(profile.serverUrl),
                    clientId = profile.clientId.trim(),
                    username = profile.username.trim(),
                )
                prefs[Keys.PROFILES_JSON] = encodeProfiles(list)
            }
        }
    }

    /** 删除面板；若删除的是激活面板，激活项顺延到剩余第一个 */
    suspend fun removeProfile(id: String) {
        context.settingsDataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.PROFILES_JSON]).toMutableList()
            val removed = list.removeAll { it.id == id }
            if (removed) {
                prefs[Keys.PROFILES_JSON] = encodeProfiles(list)
                if (prefs[Keys.ACTIVE_PROFILE_ID] == id) {
                    if (list.isEmpty()) {
                        prefs.remove(Keys.ACTIVE_PROFILE_ID)
                    } else {
                        prefs[Keys.ACTIVE_PROFILE_ID] = list.first().id
                    }
                }
            }
        }
    }

    /** 切换激活面板 */
    suspend fun setActiveProfile(id: String) {
        context.settingsDataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.PROFILES_JSON])
            if (list.any { it.id == id }) {
                prefs[Keys.ACTIVE_PROFILE_ID] = id
            }
        }
    }

    /**
     * 保存当前激活面板的服务器配置（配置向导编辑模式）。
     * 无激活面板时（如重新配置后全清空）自动创建默认面板并激活。
     *
     * @return 保存后的激活面板 ID（调用方需同步 SecureStore.activeProfileId）
     */
    suspend fun saveServer(serverUrl: String, authMode: AuthMode, clientId: String, username: String): String {
        var activeId = ""
        context.settingsDataStore.edit { prefs ->
            val list = decodeProfiles(prefs[Keys.PROFILES_JSON]).toMutableList()
            val currentActive = prefs[Keys.ACTIVE_PROFILE_ID]
            val idx = list.indexOfFirst { it.id == currentActive }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    serverUrl = normalizeUrl(serverUrl),
                    authMode = authMode,
                    clientId = clientId.trim(),
                    username = username.trim(),
                )
                activeId = list[idx].id
            } else {
                val profile = PanelProfile(
                    id = UUID.randomUUID().toString().take(8),
                    name = deriveName(serverUrl),
                    serverUrl = normalizeUrl(serverUrl),
                    authMode = authMode,
                    clientId = clientId.trim(),
                    username = username.trim(),
                )
                list.add(profile)
                prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
                activeId = profile.id
            }
            prefs[Keys.PROFILES_JSON] = encodeProfiles(list)
        }
        return activeId
    }

    /** 持久化主题模式；MainActivity 监听同一 Flow 实现即时切换 */
    suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.key
        }
    }

    suspend fun clear() {
        context.settingsDataStore.edit { it.clear() }
    }

    companion object {
        /** 旧版单配置迁移出的默认面板 ID（同时作为 SecureStore 迁移目标前缀） */
        const val LEGACY_PROFILE_ID = "default"

        /** 去掉末尾斜杠，补全协议 */
        fun normalizeUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return url
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                url = "http://$url"
            }
            while (url.endsWith("/")) url = url.dropLast(1)
            return url
        }

        /** 从地址推导面板默认名：取主机部分 */
        fun deriveName(serverUrl: String): String {
            val url = normalizeUrl(serverUrl)
            if (url.isBlank()) return "青龙面板"
            val withoutScheme = url.substringAfter("://")
            val host = withoutScheme.substringBefore("/").substringBefore("?")
            return host.ifBlank { "青龙面板" }
        }

        // ------------------------------------------------------------ JSON 编解码

        fun encodeProfiles(list: List<PanelProfile>): String =
            JSONArray().apply {
                list.forEach { p ->
                    put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("serverUrl", p.serverUrl)
                            put("authMode", p.authMode.key)
                            put("clientId", p.clientId)
                            put("username", p.username)
                        },
                    )
                }
            }.toString()

        fun decodeProfiles(raw: String?): List<PanelProfile> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id")
                    if (id.isBlank()) return@mapNotNull null
                    PanelProfile(
                        id = id,
                        name = o.optString("name"),
                        serverUrl = o.optString("serverUrl"),
                        authMode = AuthMode.from(o.optString("authMode")),
                        clientId = o.optString("clientId"),
                        username = o.optString("username"),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
