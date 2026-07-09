package com.example.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStoreException

object KeychainStore {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOKEN = "auth_token"
    
    // Master Key for encryption
    private val masterKey = MasterKey(
        AndroidKeyStoreKeyGenParameterSpec.Builder(
            "master_key",
            MasterKey.KEY_SIZE_256
        )
            .setBlockMode(MasterKey.BLOCK_MODE_GCM)
            .setEncryptionScheme(MasterKey.ENCRYPTION_SCHEME_AES256_GCM)
            .build()
    )

    fun createEncryptedPrefs(context: Context): EncryptedSharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES_SIV
        )
    }

    fun save(context: Context, serverUrl: String, token: String) {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES_SIV
        ).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getServerUrl(context: Context): String? {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES_SIV
        ).getString(KEY_SERVER_URL, null)
    }

    fun getToken(context: Context): String? {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES_SIV
        ).getString(KEY_TOKEN, null)
    }
    
    fun clear(context: Context) {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES_SIV
        ).edit().clear().apply()
    }
}