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
 * REST client for the Hermes Dashboard (port 9119 REST + WebSocket).
 *
 * Auth chain: password-login → session cookies → ws-ticket → WebSocket.
 * Separate from [ApiClient] (port 8650 Bearer-auth REST+SSE).
 * Both coexist in the same codebase.
 *
 * REST calls use [restUrl] (port 9119 HTTP). WebSocket upgrades use [wsUrl]
 * (port 9119 WS), derived automatically from [restUrl] in [setDashboardUrl].
 */
object DashboardApiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val mediaTypeJson = "application/json".toMediaType()

    private var restUrl: String = ""            // e.g. "http://100.80.204.66:9119"
    private var wsUrl: String = ""              // e.g. "ws://100.80.204.66:9119"  (derived)
    private var dashboardPassword: String = ""
    private lateinit var httpClient: OkHttpClient

    val isConfigured: Boolean get() = restUrl.isNotEmpty() && dashboardPassword.isNotEmpty()

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
            .build()

        Log.d("Hermex", "DashboardApiClient.init: OkHttpClient ready")
        DebugLog.log("INFO", "DashboardApiClient", "OkHttpClient initialized (dashboard)")
    }

    fun setDashboardUrl(url: String) {
        restUrl = url.trimEnd('/')
        // Derive the WebSocket URL: same host and port, ws:///wss:// scheme only
        wsUrl = restUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
    }

    fun setPassword(password: String) {
        dashboardPassword = password
    }

    /** Expose the HTTP client for ticket-fetch + WS creation. */
    fun httpClient(): OkHttpClient = httpClient

    /** REST base URL (port 9119 HTTP). Used for login, ws-ticket, status. */
    fun baseUrl(): String = restUrl

    /** WebSocket base URL (port 9119 WS/WSS). Used for WS upgrade. */
    fun wsBaseUrl(): String = wsUrl

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

    @Serializable
    data class TranscribeRequest(
        val data_url: String,
        val mime_type: String? = null,
    )

    @Serializable
    data class TranscribeResponse(
        val ok: Boolean = false,
        val transcript: String? = null,
        val provider: String? = null,
    )

    /**
     * Log in with password. Session cookies are stored by the CookieJar.
     */
    suspend fun login(username: String, password: String): NetworkResult<LoginResponse> =
        withContext(Dispatchers.IO) {
            val body = LoginRequest(provider = "basic", username = username, password = password)
            val bodyStr = json.encodeToString(LoginRequest.serializer(), body)
            Log.d("Hermex", "DashboardApiClient.login() → $restUrl/auth/password-login")
            DebugLog.log("REQ", "Dashboard", "POST /auth/password-login (user=$username)")
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$restUrl/auth/password-login")
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
            Log.d("Hermex", "DashboardApiClient.fetchWsTicket() → $restUrl/api/auth/ws-ticket")
            DebugLog.log("REQ", "Dashboard", "POST /api/auth/ws-ticket")
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$restUrl/api/auth/ws-ticket")
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

    /**
     * Transcribe a voice recording (dashboard Whisper-backed endpoint).
     * Body: {data_url: "data:audio/webm;base64,...", mime_type?} → {ok, transcript}.
     * Cookie-authenticated — uses the same session cookies as the WS ticket.
     */
    suspend fun transcribeAudio(dataUrl: String, mimeType: String? = null): NetworkResult<TranscribeResponse> =
        withContext(Dispatchers.IO) {
            Log.d("Hermex", "DashboardApiClient.transcribeAudio() → $restUrl/api/audio/transcribe")
            DebugLog.log("REQ", "Dashboard", "POST /api/audio/transcribe (${dataUrl.length} chars)")
            try {
                val body = TranscribeRequest(data_url = dataUrl, mime_type = mimeType)
                val bodyStr = json.encodeToString(TranscribeRequest.serializer(), body)
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$restUrl/api/audio/transcribe")
                        .post(bodyStr.toRequestBody(mediaTypeJson))
                        .build()
                ).execute()
                val result = response.handleResult(json, TranscribeResponse.serializer())
                DebugLog.log("RESP", "Dashboard", "transcribe → ${response.code} ok=${result is NetworkResult.Success}")
                result
            } catch (e: Exception) {
                Log.e("Hermex", "Dashboard transcribe failed", e)
                NetworkResult.Error(e)
            }
        }

    // ── Authenticator: auto-relogin on 401 ──

    private class DashboardAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath == "/auth/password-login") {
                return null
            }

            synchronized(this) {
                val currentPassword = dashboardPassword
                if (currentPassword.isEmpty()) return null

                DebugLog.log("AUTH", "Dashboard", "401 received — attempting re-login")
                Log.d("Hermex", "DashboardAuthenticator: 401 on ${response.request.url}, re-logging in")

                try {
                    val loginBody = LoginRequest(provider = "basic", username = "jeff", password = currentPassword)
                    val bodyStr = json.encodeToString(LoginRequest.serializer(), loginBody)

                    val loginCall = httpClient.newCall(
                        Request.Builder()
                            .url("$restUrl/auth/password-login")
                            .post(bodyStr.toRequestBody(mediaTypeJson))
                            .build()
                    )
                    val loginResponse = loginCall.execute()

                    if (loginResponse.isSuccessful) {
                        DebugLog.log("AUTH", "Dashboard", "re-login success — retrying original request")
                        Log.d("Hermex", "DashboardAuthenticator: re-login OK")
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
