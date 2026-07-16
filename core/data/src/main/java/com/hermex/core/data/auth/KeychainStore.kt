package com.hermex.core.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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

    private const val KEY_USERNAME = "username"

    private val prefsMap = mutableMapOf<Context, SharedPreferences>()

    @Synchronized
    private fun getPrefs(context: Context): SharedPreferences {
        return prefsMap.getOrPut(context.applicationContext) {
            try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context.applicationContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e("Hermex", "KeychainStore: EncryptedSharedPreferences failed, falling back to plain SP", e)
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    /** Persist server URL + credentials after successful login. */
    fun saveCredentials(context: Context, serverUrl: String, username: String, password: String) {
        getPrefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** @deprecated Use [saveCredentials] instead. */
    fun savePassword(context: Context, serverUrl: String, password: String) {
        getPrefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** @deprecated Use [saveCredentials] instead. */
    fun saveUsername(context: Context, serverUrl: String, username: String) {
        getPrefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun getServerUrl(context: Context): String? =
        getPrefs(context).getString(KEY_SERVER_URL, null)

    fun getUsername(context: Context): String? =
        getPrefs(context).getString(KEY_USERNAME, null)

    fun getPassword(context: Context): String? =
        getPrefs(context).getString(KEY_PASSWORD, null)

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
