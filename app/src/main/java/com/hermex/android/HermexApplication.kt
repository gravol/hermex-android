package com.hermex.android

import android.app.Application
import com.hermex.core.data.auth.KeychainStore
import com.hermex.core.data.auth.ReloginAuthenticator
import com.hermex.core.network.ApiClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HermexApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize the shared ApiClient with persistent CookieJar
        // and 401 auto-relogin Authenticator.
        ApiClient.init(
            context = this,
            authenticator = ReloginAuthenticator(this),
        )

        // If we have a saved server URL from a previous session,
        // set it so the client can make authenticated requests immediately.
        val savedUrl = KeychainStore.getServerUrl(this)
        if (savedUrl != null) {
            ApiClient.setBaseUrl(savedUrl)
        }
    }
}
