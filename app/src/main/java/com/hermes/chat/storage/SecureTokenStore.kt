package com.hermes.chat.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure, encrypted storage for the Hermes API token.
 *
 * Uses Android Keystore-backed AES-256 GCM encryption via EncryptedSharedPreferences.
 * The MasterKey is stored in the hardware-backed Keystore (or software fallback).
 *
 * Safe for Bearer tokens, API keys, and other secrets.
 */
class SecureTokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Load the stored token. Returns empty string if none set. */
    fun loadToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""

    /** Persist a new token (or empty to clear). */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /** Delete the stored token. */
    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "hermes_secure_prefs"
        private const val KEY_TOKEN = "hermes_api_token"
    }
}
