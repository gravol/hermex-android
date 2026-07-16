package com.hermex.core.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.CertificatePinner
import okhttp3.internal.tls.OkHostnameVerifier
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
    private lateinit var client: OkHttpClient

    val isConfigured: Boolean get() = baseUrl.isNotEmpty()

    /**
     * Initialize the shared OkHttpClient with a persistent CookieJar and
     * optional 401 auto-relogin Authenticator. Call once during app startup.
     */
    fun init(context: Context, authenticator: Authenticator? = null) {
        val cookieJar = NetworkCookieJar(context)
        client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .apply { if (authenticator != null) authenticator(authenticator) }
            .apply {
                // Certificate pinning for self-hosted Hermes server.
                // Pin is tied to the server's self-signed cert — if the cert
                // is regenerated or the server IP changes, the pin must be updated.
                // See DEVELOPMENT.md.
                certificatePinner(CertificatePinner.Builder()
                    .add("100.80.204.66", "sha256/jOIJfSaEOx0W1RLGrwSG/gIH4c2I5Nz2y193EoWi2+Q=")
                    .build()
                )
                // Scoped hostname verifier: the self-signed cert's SANs don't include
                // the Tailscale IP (they're container-internal addresses).  Allow the
                // mismatch ONLY for 100.80.204.66 and ONLY because CertificatePinner
                // already cryptographically verifies the server's public key.
                hostnameVerifier { hostname, session ->
                    hostname == "100.80.204.66" || OkHostnameVerifier.verify(hostname, session)
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Log.d("Hermex", "ApiClient.init: OkHttpClient ready (baseUrl empty until login)")
    }

    /** Set the base URL after successful login. */
    fun setBaseUrl(url: String) {
        this.baseUrl = url.trimEnd('/')
    }

    // ── helpers ──

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

    private fun sseUrl(path: String) = "$baseUrl$path"

    // ── Auth ──

    /** Health-check a server URL. Uses the shared client so cookies are captured. */
    suspend fun health(serverUrl: String): NetworkResult<HealthResponse> =
        withContext(Dispatchers.IO) {
            val url = serverUrl.trimEnd('/') + "/api/health"
            Log.d("Hermex", "ApiClient.health() → $url")
            client.newCall(Request.Builder().url(url).get().build())
                .execute()
                .also { Log.d("Hermex", "ApiClient.health() ← ${it.code}") }
                .handleResult(json, HealthResponse.serializer())
        }

    /** Login to a server. The session cookie is captured automatically by the
     *  shared client's CookieJar. Call [setBaseUrl] after success. */
    suspend fun login(serverUrl: String, username: String, password: String): NetworkResult<LoginResponse> =
        withContext(Dispatchers.IO) {
            val url = serverUrl.trimEnd('/') + "/api/auth/login"
            Log.d("Hermex", "ApiClient.login() → $url")
            val bodyStr = json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password))
            client.newCall(
                Request.Builder().url(url)
                    .post(bodyStr.toRequestBody(mediaTypeJson))
                    .build()
            ).execute()
                .also { Log.d("Hermex", "ApiClient.login() ← ${it.code}") }
                .handleResult(json, LoginResponse.serializer())
        }
    // ── Chat ──

    suspend fun startChat(req: ChatStartRequest) =
        post("/chat/start", req, ChatStartRequest.serializer(), ChatStartResponse.serializer())

    suspend fun chatStreamStatus(streamId: String) =
        get("/chat/$streamId/status", ChatStreamStatusResponse.serializer())

    suspend fun cancelChat(streamId: String) =
        get("/chat/$streamId/cancel", ChatCancelResponse.serializer())

    // ── Approval ──

    suspend fun approvalPending(sessionId: String) =
        get("/approval/$sessionId/pending", ApprovalPendingResponse.serializer())

    suspend fun respondApproval(req: ApprovalRespondRequest) =
        post("/approval/respond", req, ApprovalRespondRequest.serializer(), ApprovalRespondResponse.serializer())

    // ── Clarification ──

    suspend fun clarifyPending(sessionId: String) =
        get("/clarify/$sessionId/pending", ClarificationPendingResponse.serializer())

    suspend fun respondClarification(req: ClarificationRespondRequest) =
        post("/clarify/respond", req, ClarificationRespondRequest.serializer(), ClarificationRespondResponse.serializer())

    // ── Steer / Goal / BTW / Background ──

    suspend fun steerChat(req: ChatSteerRequest) =
        post("/chat/steer", req, ChatSteerRequest.serializer(), ChatSteerResponse.serializer())

    suspend fun submitGoal(req: GoalSubmissionRequest) =
        post("/goal/submit", req, GoalSubmissionRequest.serializer(), GoalSubmissionResponse.serializer())

    suspend fun startBtw(req: BtwRequest) =
        post("/btw/start", req, BtwRequest.serializer(), BtwStartResponse.serializer())

    suspend fun startBackground(req: BackgroundRequest) =
        post("/background/start", req, BackgroundRequest.serializer(), BackgroundStartResponse.serializer())

    suspend fun backgroundStatus(sessionId: String) =
        get("/background/$sessionId/status", BackgroundStatusResponse.serializer())

    // ── Sessions ──

    suspend fun sessions() =
        get("/sessions", SessionsResponse.serializer())

    suspend fun searchSessions(query: String, content: Boolean = true, depth: Int = 5): NetworkResult<SessionSearchResponse> {
        val q = "query=$query&content=$content&depth=$depth"
        return get("/sessions/search?$q", SessionSearchResponse.serializer())
    }

    suspend fun session(
        id: String, includeMessages: Boolean = true, messageLimit: Int? = 50,
        messageBefore: Int? = null, expandRenderable: Boolean = false,
    ): NetworkResult<SessionResponse> {
        val p = mutableListOf("includeMessages=$includeMessages")
        if (messageLimit != null) p += "messageLimit=$messageLimit"
        if (messageBefore != null) p += "messageBefore=$messageBefore"
        if (expandRenderable) p += "expandRenderable=true"
        return get("/session/$id?${p.joinToString("&")}", SessionResponse.serializer())
    }

    suspend fun sessionStatus(id: String) =
        get("/session/$id/status", SessionStatusResponse.serializer())

    suspend fun createSession(req: NewSessionRequest) =
        post("/session/new", req, NewSessionRequest.serializer(), SessionResponse.serializer())

    suspend fun renameSession(req: RenameSessionRequest) =
        post("/session/rename", req, RenameSessionRequest.serializer(), SessionMutationResponse.serializer())

    suspend fun deleteSession(req: SessionIDRequest) =
        post("/session/delete", req, SessionIDRequest.serializer(), SessionMutationResponse.serializer())

    suspend fun pinSession(req: PinSessionRequest) =
        post("/session/pin", req, PinSessionRequest.serializer(), SessionMutationResponse.serializer())

    suspend fun archiveSession(req: ArchiveSessionRequest) =
        post("/session/archive", req, ArchiveSessionRequest.serializer(), SessionMutationResponse.serializer())

    suspend fun branchSession(req: BranchSessionRequest) =
        post("/session/branch", req, BranchSessionRequest.serializer(), SessionBranchResponse.serializer())

    suspend fun compressSession(req: CompressSessionRequest) =
        post("/session/compress", req, CompressSessionRequest.serializer(), SessionCompressResponse.serializer())

    suspend fun undoSession(req: SessionIDRequest) =
        post("/session/undo", req, SessionIDRequest.serializer(), SessionUndoResponse.serializer())

    suspend fun retrySession(req: SessionIDRequest) =
        post("/session/retry", req, SessionIDRequest.serializer(), SessionRetryResponse.serializer())

    suspend fun truncateSession(req: TruncateSessionRequest) =
        post("/session/truncate", req, TruncateSessionRequest.serializer(), SessionResponse.serializer())

    suspend fun updateSession(req: UpdateSessionRequest) =
        post("/session/update", req, UpdateSessionRequest.serializer(), SessionResponse.serializer())

    suspend fun moveSession(req: MoveSessionRequest) =
        post("/session/move", req, MoveSessionRequest.serializer(), SessionMutationResponse.serializer())

    suspend fun sessionYolo(sessionId: String) =
        get("/session/$sessionId/yolo", SessionYoloResponse.serializer())

    suspend fun setSessionYolo(req: SessionYoloRequest) =
        post("/session/yolo", req, SessionYoloRequest.serializer(), SessionYoloResponse.serializer())

    // ── Cron ──

    suspend fun crons() =
        get("/crons", CronJobsResponse.serializer())

    suspend fun createCron(req: CronCreateRequest) =
        post("/cron/create", req, CronCreateRequest.serializer(), CronMutationResponse.serializer())

    suspend fun updateCron(req: CronUpdateRequest) =
        post("/cron/update", req, CronUpdateRequest.serializer(), CronMutationResponse.serializer())

    suspend fun deleteCron(req: CronJobIDRequest) =
        post("/cron/delete", req, CronJobIDRequest.serializer(), CronMutationResponse.serializer())

    suspend fun runCron(req: CronJobIDRequest) =
        post("/cron/run", req, CronJobIDRequest.serializer(), CronMutationResponse.serializer())

    suspend fun pauseCron(req: CronJobIDRequest) =
        post("/cron/pause", req, CronJobIDRequest.serializer(), CronMutationResponse.serializer())

    suspend fun resumeCron(req: CronJobIDRequest) =
        post("/cron/resume", req, CronJobIDRequest.serializer(), CronMutationResponse.serializer())

    // ── SSE ──

    fun chatStreamUrl(streamId: String, replay: Int? = null, afterSeq: Int? = null): String {
        val p = mutableListOf<String>()
        if (replay != null) p += "replay=$replay"
        if (afterSeq != null) p += "after_seq=${maxOf(0, afterSeq)}"
        val qs = if (p.isNotEmpty()) "?${p.joinToString("&")}" else ""
        return sseUrl("/chat/$streamId/stream$qs")
    }

    fun approvalStreamUrl(sessionId: String) = sseUrl("/approval/$sessionId/stream")
    fun clarifyStreamUrl(sessionId: String) = sseUrl("/clarify/$sessionId/stream")

    fun newEventSource(url: String, listener: EventSourceListener): EventSource =
        EventSources.createFactory(client).newEventSource(
            Request.Builder().url(url).build(), listener
        )
}
