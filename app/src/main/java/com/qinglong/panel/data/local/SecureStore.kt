package com.qinglong.panel.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感凭据存储：client_secret / 密码 / token。
 * 使用 EncryptedSharedPreferences（AES256-GCM），不可用时降级为普通 SharedPreferences。
 *
 * 多面板（v1.1.0）：密钥按面板 ID 加前缀隔离（p_{profileId}_xxx），
 * 每个面板独立保留登录态，切换面板无需重新登录。
 * [activeProfileId] 由 AppContainer 在启动与切换面板时维护。
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, fallback to plain prefs", e)
        context.getSharedPreferences(FILE_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    /** 当前激活面板 ID：所有读写都落在 p_{id}_ 前缀的键上 */
    @Volatile
    var activeProfileId: String = DEFAULT_PROFILE_ID

    private fun key(base: String): String = "p_${activeProfileId}_$base"

    var clientSecret: String
        get() = prefs.getString(key(KEY_CLIENT_SECRET), "").orEmpty()
        set(value) = prefs.edit().putString(key(KEY_CLIENT_SECRET), value).apply()

    var password: String
        get() = prefs.getString(key(KEY_PASSWORD), "").orEmpty()
        set(value) = prefs.edit().putString(key(KEY_PASSWORD), value).apply()

    var token: String?
        get() = prefs.getString(key(KEY_TOKEN), null)
        set(value) = prefs.edit().putString(key(KEY_TOKEN), value).apply()

    var tokenExpiresAt: Long
        get() = prefs.getLong(key(KEY_TOKEN_EXPIRES_AT), 0L)
        set(value) = prefs.edit().putLong(key(KEY_TOKEN_EXPIRES_AT), value).apply()

    /**
     * 旧版单配置密钥迁移到默认面板（幂等）：
     * 把无前缀的 client_secret / password / token / token_expires_at
     * 复制到 p_{profileId}_ 前缀下（已存在则不覆盖）。
     * 旧键保留不删，作为迁移期双保险。
     */
    fun migrateLegacyTo(profileId: String) {
        val editor = prefs.edit()
        copyStringIfAbsent(editor, KEY_CLIENT_SECRET, profileId)
        copyStringIfAbsent(editor, KEY_PASSWORD, profileId)
        copyStringIfAbsent(editor, KEY_TOKEN, profileId)
        val legacyExpiry = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        if (legacyExpiry != 0L && prefs.getLong("p_${profileId}_$KEY_TOKEN_EXPIRES_AT", 0L) == 0L) {
            editor.putLong("p_${profileId}_$KEY_TOKEN_EXPIRES_AT", legacyExpiry)
        }
        editor.apply()
        Log.i(TAG, "legacy secure keys migrated to profile $profileId")
    }

    private fun copyStringIfAbsent(editor: SharedPreferences.Editor, base: String, profileId: String) {
        val legacy = prefs.getString(base, null)?.takeIf { it.isNotEmpty() } ?: return
        if (prefs.getString("p_${profileId}_$base", null) == null) {
            editor.putString("p_${profileId}_$base", legacy)
        }
    }

    /** 删除某个面板的全部密钥（删除面板配置时调用） */
    fun clearProfile(profileId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("p_${profileId}_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SecureStore"
        private const val FILE_NAME = "ql_secure_prefs"
        private const val FILE_NAME_FALLBACK = "ql_secure_prefs_plain"
        private const val KEY_CLIENT_SECRET = "client_secret"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"

        /** 默认面板 ID（旧配置迁移目标） */
        const val DEFAULT_PROFILE_ID = SettingsStore.LEGACY_PROFILE_ID
    }
}
