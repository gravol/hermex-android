package com.hermex.android

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.hermex.core.data.auth.KeychainStore
import com.hermex.core.data.auth.ReloginAuthenticator
import com.hermex.core.network.ApiClient

class HermexApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Catch otherwise-unhandled crashes and log them before the process dies.
        // This gives us a logcat trace for crashes that happen off the main thread.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("Hermex", "FATAL: uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize the shared ApiClient with persistent CookieJar
        // and 401 auto-relogin Authenticator.
        try {
            ApiClient.init(
                context = this,
                authenticator = ReloginAuthenticator(this),
            )

            // If we have a saved server URL from a previous session,
            // set it so the client can make authenticated requests immediately.
            val savedUrl = KeychainStore.getServerUrl(this)
            if (savedUrl != null) {
                ApiClient.setBaseUrl(savedUrl)
                Log.d("Hermex", "HermexApplication: restored server URL: $savedUrl")
            }
        } catch (e: Exception) {
            Log.e("Hermex", "HermexApplication: ApiClient.init failed", e)
        }
    }
}
