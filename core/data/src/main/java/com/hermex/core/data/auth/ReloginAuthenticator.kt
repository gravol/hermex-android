package com.hermex.core.data.auth

import android.content.Context
import com.hermex.core.network.ApiClient
import com.hermex.core.network.NetworkResult
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp Authenticator that silently re-logs in when a 401 is received.
 *
 * Uses the password stored in [KeychainStore] to call POST /api/auth/login,
 * which sets a new session cookie in the shared CookieJar. The original
 * request is then retried with the fresh cookie.
 */
class ReloginAuthenticator(private val context: Context) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if we just tried to log in (prevents infinite loop)
        if (response.request.url.encodedPath == "/api/auth/login") return null

        // Only retry once per 401 chain
        if (responseCount(response) > 1) return null

        val password = KeychainStore.getPassword(context) ?: return null

        return try {
            // Blocking call is acceptable here — Authenticator runs on OkHttp's
            // background thread, and the login is a single fast HTTP call.
            runBlocking {
                when (val result = ApiClient.login(
                    serverUrl = KeychainStore.getServerUrl(context) ?: return@runBlocking null,
                    password = password,
                )) {
                    is NetworkResult.Success -> {
                        if (result.data.ok) response.request
                        else null
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
