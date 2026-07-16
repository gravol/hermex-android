package com.hermex.core.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Encrypted credential storage for auth tokens and server URLs.
 * Backed by AndroidX EncryptedSharedPreferences (AES-256 GCM).
 */
object KeychainStore {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOKEN = "auth_token"

    private val prefsMap = mutableMapOf<Context, SharedPreferences>()

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
