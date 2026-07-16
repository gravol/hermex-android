package com.hermex.core.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Encrypted credential storage for Hermex server auth.
 *
 * Stores server URL + password (encrypted) so the 401 auto-relogin
 * interceptor can re-authenticate silently. Session cookies are
 * managed by OkHttp's CookieJar — this store is only for credentials
 * needed to obtain a new session cookie.
 */
object KeychainStore {
    private const val PREFS_NAME = "hermex_auth"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_PASSWORD = "password"

    private val prefsMap = mutableMapOf<Context, SharedPreferences>()

    @Synchronized
    private fun getPrefs(context: Context): SharedPreferences {
        return prefsMap.getOrPut(context.applicationContext) {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    /** Persist server URL + password after successful login. */
    fun savePassword(context: Context, serverUrl: String, password: String) {
        getPrefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getServerUrl(context: Context): String? =
        getPrefs(context).getString(KEY_SERVER_URL, null)

    fun getPassword(context: Context): String? =
        getPrefs(context).getString(KEY_PASSWORD, null)

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
