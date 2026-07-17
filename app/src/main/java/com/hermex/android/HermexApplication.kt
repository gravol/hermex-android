package com.hermex.android

import android.app.Application
import android.util.Log
import com.hermex.core.data.auth.KeychainStore
import com.hermex.core.network.ApiClient
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog

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
                DebugLog.log("INFO", "HermexApp", "restored LEGACY API credentials → ApiClient.isConfigured=true")
            } else {
                DebugLog.log("INFO", "HermexApp", "no legacy API credentials stored → ApiClient.isConfigured=false")
            }
        } catch (e: Exception) {
            Log.e("Hermex", "HermexApplication: ApiClient.init failed", e)
        }

        try {
            DashboardApiClient.init(this)

            val savedDashboardUrl = KeychainStore.getDashboardUrl(this)
            val savedDashboardPassword = KeychainStore.getDashboardPassword(this)
            if (savedDashboardUrl != null && savedDashboardPassword != null) {
                DashboardApiClient.setDashboardUrl(savedDashboardUrl)
                DashboardApiClient.setPassword(savedDashboardPassword)
                Log.d("Hermex", "HermexApplication: restored dashboard URL + password")
                DebugLog.log("INFO", "HermexApp", "restored dashboard credentials → isConfigured=true")
            } else {
                DebugLog.log("INFO", "HermexApp", "no dashboard credentials stored → isConfigured=false")
            }
        } catch (e: Exception) {
            Log.e("Hermex", "HermexApplication: DashboardApiClient.init failed", e)
        }
    }
}
