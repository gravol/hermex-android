package com.hermex.core.data.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Encrypted credential storage (stub — plain SharedPreferences for now,
 * upgrade to EncryptedSharedPreferences when security-crypto API stabilizes).
 */
object KeychainStore {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOKEN = "auth_token"

    private val prefsMap = mutableMapOf<Context, SharedPreferences>()

    private fun getPrefs(context: Context): SharedPreferences {
        return prefsMap.getOrPut(context.applicationContext) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(context: Context, serverUrl: String, token: String) {
        getPrefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getServerUrl(context: Context): String? =
        getPrefs(context).getString(KEY_SERVER_URL, null)

    fun getToken(context: Context): String? =
        getPrefs(context).getString(KEY_TOKEN, null)

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
