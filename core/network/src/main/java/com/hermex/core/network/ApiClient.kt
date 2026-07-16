package com.hermex.core.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaTypeJson = "application/json".toMediaType()

    private var baseUrl: String = ""
    private var apiKey: String = ""
    private lateinit var client: OkHttpClient

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && apiKey.isNotEmpty()

    /** Expose the OkHttpClient for SSE stream connections. */
    fun httpClient(): OkHttpClient = client

    /** Expose baseUrl for constructing SSE URLs. */
    fun baseUrl(): String = baseUrl

    /** Initialize the shared OkHttpClient. Call once during app startup. */
    fun init(context: Context) {
        val cookieJar = NetworkCookieJar(context)
        client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(BearerInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)  // no read timeout for SSE
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Log.d("Hermex", "ApiClient.init: OkHttpClient ready")
    }

    /** Set the base URL (e.g. http://100.80.204.66:8650). */
    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /** Set the API Server bearer key. */
    fun setApiKey(key: String) {
        apiKey = key
    }

    /** Interceptor that adds Authorization: Bearer *** to every request. */
    private class BearerInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val original = chain.request()
            if (apiKey.isEmpty()) return chain.proceed(original)
            val request = original.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
            return chain.proceed(request)
        }
    }

    // ── HTTP helpers ──

    private suspend fun <T> get(path: String, ser: KSerializer<T>): NetworkResult<T> =
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url("$baseUrl$path").get().build())
                .execute().handleResult(json, ser)
        }

    private suspend fun <T, B> post(
        path: String, body: B, bodySer: KSerializer<B>, respSer: KSerializer<T>,
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        val bodyStr = json.encodeToString(bodySer, body)
        client.newCall(
            Request.Builder().url("$baseUrl$path")
                .post(bodyStr.toRequestBody(mediaTypeJson)).build()
        ).execute().handleResult(json, respSer)
    }

    private suspend fun <T> delete(path: String, ser: KSerializer<T>): NetworkResult<T> =
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url("$baseUrl$path").delete().build())
                .execute().handleResult(json, ser)
        }

    private suspend fun <T, B> patch(
        path: String, body: B, bodySer: KSerializer<B>, respSer: KSerializer<T>,
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        val bodyStr = json.encodeToString(bodySer, body)
        client.newCall(
            Request.Builder().url("$baseUrl$path")
                .patch(bodyStr.toRequestBody(mediaTypeJson)).build()
        ).execute().handleResult(json, respSer)
    }

    // ── Health ──

    /** GET /health — connection + auth test. */
    suspend fun testConnection(): NetworkResult<StatusResponse> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/health"
            Log.d("Hermex", "ApiClient.testConnection() → $url")
            client.newCall(Request.Builder().url(url).get().build())
                .execute()
                .also { Log.d("Hermex", "ApiClient.testConnection() ← ${it.code}") }
                .handleResult(json, StatusResponse.serializer())
        }

    // ── Sessions ──

    /** GET /api/sessions — list sessions. */
    suspend fun sessions() =
        get("/api/sessions", SessionsResponse.serializer())

    /** GET /api/sessions/{id}?includeMessages= — get one session with optional messages. */
    suspend fun session(id: String, includeMessages: Boolean = true) =
        get("/api/sessions/$id?includeMessages=$includeMessages", SessionDetail.serializer())

    /** GET /api/sessions/{id}/messages — message history for a session. */
    suspend fun sessionMessages(id: String) =
        get("/api/sessions/$id/messages", MessagesResponse.serializer())

    /** POST /api/sessions — create a new session. Returns the session directly. */
    suspend fun createSession(req: NewSessionRequest) =
        post("/api/sessions", req, NewSessionRequest.serializer(), SessionDetail.serializer())

    /** POST /api/sessions/{id}/fork — branch a session. */
    suspend fun forkSession(sessionId: String) =
        post("/api/sessions/$sessionId/fork", EmptyBody, EmptyBody.serializer(), SessionDetail.serializer())

    /** PATCH /api/sessions/{id} — update session metadata. */
    suspend fun updateSession(sessionId: String, req: PatchSessionRequest) =
        patch("/api/sessions/$sessionId", req, PatchSessionRequest.serializer(), SessionDetail.serializer())

    /** DELETE /api/sessions/{id} — delete a session. */
    suspend fun deleteSession(sessionId: String) =
        delete("/api/sessions/$sessionId", SessionDeleteResponse.serializer())

    // ── SSE streaming ──

    /** Open an SSE stream for POST /api/sessions/{id}/chat/stream. Returns the EventSource. */
    fun openChatStream(
        sessionId: String,
        message: String,
        workspace: String? = null,
        model: String? = null,
        modelProvider: String? = null,
        listener: EventSourceListener,
    ): EventSource {
        val req = ChatRequest(message = message, workspace = workspace, model = model, modelProvider = modelProvider)
        val bodyStr = json.encodeToString(ChatRequest.serializer(), req)
        val request = Request.Builder()
            .url("$baseUrl/api/sessions/$sessionId/chat/stream")
            .post(bodyStr.toRequestBody(mediaTypeJson))
            .build()
        return EventSources.createFactory(client).newEventSource(request, listener)
    }
}
