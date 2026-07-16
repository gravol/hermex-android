package com.hermex.android

import android.app.Application
import android.util.Log
import com.hermex.core.data.auth.KeychainStore
import com.hermex.core.network.ApiClient

class HermexApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("Hermex", "FATAL: uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            ApiClient.init(this)

            val savedUrl = KeychainStore.getServerUrl(this)
            val savedKey = KeychainStore.getApiKey(this)
            if (savedUrl != null && savedKey != null) {
                ApiClient.setBaseUrl(savedUrl)
                ApiClient.setApiKey(savedKey)
                Log.d("Hermex", "HermexApplication: restored server URL + API key")
            }
        } catch (e: Exception) {
            Log.e("Hermex", "HermexApplication: ApiClient.init failed", e)
        }
    }
}
