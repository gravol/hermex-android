package com.hermex.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

/**
 * REST client for the Hermes Dashboard (port 9119/8443).
 *
 * Auth chain: password-login → session cookies → ws-ticket → WebSocket.
 * Separate from [ApiClient] (port 8650 Bearer-auth REST+SSE).
 * Both coexist in the same codebase.
 */
object DashboardApiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaTypeJson = "application/json".toMediaType()

    private var dashboardUrl: String = ""       // e.g. "https://100.80.204.66:8443"
    private var dashboardPassword: String = ""
    private lateinit var httpClient: OkHttpClient

    val isConfigured: Boolean get() = dashboardUrl.isNotEmpty() && dashboardPassword.isNotEmpty()

    // ── Init ──

    /**
     * Build the shared OkHttpClient for dashboard REST calls.
     * Call once during app startup.
     */
    fun init(context: android.content.Context) {
        val cookieJar = NetworkCookieJar(context)

        httpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(DebugLoggingInterceptor())
            .authenticator(DashboardAuthenticator())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Trust self-signed cert on Tailscale IP
            .hostnameVerifier { _, _ -> true }
            .apply {
                // Trust-all SSL for self-signed cert on development server
                val trustAllCerts = javax.net.ssl.X509TrustManager {
                    @Suppress("EmptyFunctionBlock")
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>, authType: String
                    ) {}
                    @Suppress("EmptyFunctionBlock")
                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>, authType: String
                    ) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(trustAllCerts), java.security.SecureRandom())
                sslSocketFactory(sslContext.socketFactory, trustAllCerts)
            }
            .build()

        Log.d("Hermex", "DashboardApiClient.init: OkHttpClient ready")
        DebugLog.log("INFO", "DashboardApiClient", "OkHttpClient initialized (dashboard)")
    }

    fun setDashboardUrl(url: String) {
        dashboardUrl = url.trimEnd('/')
    }

    fun setPassword(password: String) {
        dashboardPassword = password
    }

    /** Expose the HTTP client for ticket-fetch + WS creation. */
    fun httpClient(): OkHttpClient = httpClient

    fun baseUrl(): String = dashboardUrl

    // ── Auth endpoints ──

    @Serializable
    data class LoginRequest(
        val provider: String = "basic",
        val username: String,
        val password: String,
    )

    @Serializable
    data class LoginResponse(
        val success: Boolean = false,
        val message: String? = null,
        val user: UserInfo? = null,
        val requires_2fa: Boolean = false,
    )

    @Serializable
    data class UserInfo(
        val id: String? = null,
        val username: String? = null,
        val role: String? = null,
    )

    @Serializable
    data class WsTicketResponse(
        val ticket: String,
        val ttl_seconds: Int = 30,
    )

    @Serializable
    data class StatusResponse(
        val version: String? = null,
        val auth_required: Boolean = false,
        val auth_providers: List<String> = emptyList(),
        val gateway_running: Boolean = false,
        val active_agents: Int = 0,
        val active_sessions: Int = 0,
    )

    /**
     * Log in with password. Session cookies are stored by the CookieJar.
     */
    suspend fun login(username: String, password: String): NetworkResult<LoginResponse> =
        withContext(Dispatchers.IO) {
            val body = LoginRequest(username = username, password = password)
            val bodyStr = json.encodeToString(LoginRequest.serializer(), body)
            Log.d("Hermex", "DashboardApiClient.login() → $dashboardUrl/auth/password-login")
            DebugLog.log("REQ", "Dashboard", "POST /auth/password-login (user=$username)")
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$dashboardUrl/auth/password-login")
                        .post(bodyStr.toRequestBody(mediaTypeJson))
                        .build()
                ).execute()
                val result = response.handleResult(json, LoginResponse.serializer())
                DebugLog.log("RESP", "Dashboard", "login → ${response.code} ${if (result is NetworkResult.Success) "success" else "failed"}")
                result
            } catch (e: Exception) {
                Log.e("Hermex", "Dashboard login failed", e)
                NetworkResult.Error(e)
            }
        }

    /**
     * Fetch a single-use 30s-TTL WebSocket ticket.
     * Requires valid session cookies (from prior login + CookieJar).
     */
    suspend fun fetchWsTicket(): NetworkResult<WsTicketResponse> =
        withContext(Dispatchers.IO) {
            Log.d("Hermex", "DashboardApiClient.fetchWsTicket() → $dashboardUrl/api/auth/ws-ticket")
            DebugLog.log("REQ", "Dashboard", "POST /api/auth/ws-ticket")
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$dashboardUrl/api/auth/ws-ticket")
                        .post("{}".toRequestBody(mediaTypeJson))
                        .build()
                ).execute()
                val result = response.handleResult(json, WsTicketResponse.serializer())
                DebugLog.log("RESP", "Dashboard", "ws-ticket → ${response.code} ${if (result is NetworkResult.Success) "got ticket" else "failed"}")
                result
            } catch (e: Exception) {
                Log.e("Hermex", "Dashboard ws-ticket fetch failed", e)
                NetworkResult.Error(e)
            }
        }

    /**
     * Health-check / status endpoint.
     */
    suspend fun status(serverUrl: String): NetworkResult<StatusResponse> =
        withContext(Dispatchers.IO) {
            val url = serverUrl.trimEnd('/') + "/api/status"
            Log.d("Hermex", "DashboardApiClient.status() → $url")
            DebugLog.log("REQ", "Dashboard", "GET /api/status")
            try {
                val response = httpClient.newCall(
                    Request.Builder().url(url).get().build()
                ).execute()
                response.handleResult(json, StatusResponse.serializer())
            } catch (e: Exception) {
                NetworkResult.Error(e)
            }
        }

    // ── Authenticator: auto-relogin on 401 ──

    private inner class DashboardAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath == "/auth/password-login") {
                // Don't retry the login request itself
                return null
            }

            synchronized(this) {
                val currentPassword = dashboardPassword
                if (currentPassword.isEmpty()) return null

                DebugLog.log("AUTH", "Dashboard", "401 received — attempting re-login")
                Log.d("Hermex", "DashboardAuthenticator: 401 on ${response.request.url}, re-logging in")

                try {
                    val loginBody = LoginRequest(username = "jeff", password = currentPassword)
                    val bodyStr = json.encodeToString(LoginRequest.serializer(), loginBody)

                    val loginCall = httpClient.newCall(
                        Request.Builder()
                            .url("$dashboardUrl/auth/password-login")
                            .post(bodyStr.toRequestBody(mediaTypeJson))
                            .build()
                    )
                    val loginResponse = loginCall.execute()

                    if (loginResponse.isSuccessful) {
                        DebugLog.log("AUTH", "Dashboard", "re-login success — retrying original request")
                        Log.d("Hermex", "DashboardAuthenticator: re-login OK")
                        // Cookies are now set by CookieJar — retry the original request
                        return response.request
                    } else {
                        DebugLog.log("AUTH", "Dashboard", "re-login failed (${loginResponse.code}) — giving up")
                        Log.w("Hermex", "DashboardAuthenticator: re-login returned ${loginResponse.code}")
                        return null
                    }
                } catch (e: Exception) {
                    DebugLog.log("AUTH", "Dashboard", "re-login exception: ${e.message}")
                    Log.e("Hermex", "DashboardAuthenticator: re-login failed", e)
                    return null
                }
            }
        }
    }
}
