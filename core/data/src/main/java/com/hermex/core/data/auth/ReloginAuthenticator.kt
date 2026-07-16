package com.hermex.core.data.auth

import android.content.Context
import android.util.Log
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
        Log.w("Hermex", "ReloginAuthenticator: 401 on ${response.request.url} (code=${response.code})")

        // Don't retry if we just tried to log in (prevents infinite loop)
        if (response.request.url.encodedPath == "/api/auth/login") {
            Log.w("Hermex", "ReloginAuthenticator: already on /login, not retrying")
            return null
        }

        // Only retry once per 401 chain
        if (responseCount(response) > 1) return null

        val password = KeychainStore.getPassword(context) ?: return null

        Log.i("Hermex", "ReloginAuthenticator: attempting auto-relogin...")
        return try {
            runBlocking {
                when (val result = ApiClient.login(
                    serverUrl = KeychainStore.getServerUrl(context) ?: return@runBlocking null,
                    password = password,
                )) {
                    is NetworkResult.Success -> {
                        if (result.data.ok) {
                            Log.i("Hermex", "ReloginAuthenticator: auto-relogin SUCCESS — retrying original request")
                            response.request
                        } else {
                            Log.e("Hermex", "ReloginAuthenticator: auto-relogin FAILED — login returned ok=false")
                            null
                        }
                    }
                    else -> {
                        Log.e("Hermex", "ReloginAuthenticator: auto-relogin FAILED — ${result::class.simpleName}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Hermex", "ReloginAuthenticator: auto-relogin exception: ${e.message}", e)
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
