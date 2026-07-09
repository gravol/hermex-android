package com.example.hermes.networking

import okhttp3.OkHttpClient
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.KotlinxSerializationConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://your-api-domain.com/" // Replace with actual URL

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Add Auth Interceptor here if needed
        // .addInterceptor(AuthInterceptor())
        // Add Cookie Jar here
        // .cookieJar(CookieJar())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(KotlinxSerializationConverterFactory.create(json))
        .build()

    val apiService: HermesApiService = retrofit.create(HermesApiService::class.java)

    // --- Helper for SSE ---
    fun startSSE(
        url: String,
        listener: EventSourceListener
    ) {
        val request = Request.Builder().url(url).build()
        okHttpClient.eventListenerFactory {
            EventSource.Factory(okHttpClient)
        }.newEventSource(request, listener)
    }

    fun cancelSSE(eventSource: EventSource) {
        eventSource.cancel()
    }

    // --- Chat Methods ---
    suspend fun startChat(
        sessionID: String,
        message: String,
        workspace: String?,
        model: String?,
        modelProvider: String?,
        profile: String?,
        explicitModelPick: Boolean,
        attachments: List<JsonElement>?
    ): NetworkResult<ChatStartResponse> {
        val request = ChatStartRequest(
            sessionId = sessionID,
            message = message,
            workspace = workspace,
            model = model,
            modelProvider = modelProvider,
            profile = profile,
            explicitModelPick = if (explicitModelPick) true else null,
            attachments = attachments
        )
        return apiService.startChat(request).handleResult()
    }

    suspend fun cancelChat(streamID: String): NetworkResult<ChatCancelResponse> =
        apiService.cancelChat(streamID).handleResult()

    suspend fun chatStreamStatus(streamID: String): NetworkResult<ChatStreamStatusResponse> =
        apiService.chatStreamStatus(streamID).handleResult()

    suspend fun approvalPending(sessionID: String): NetworkResult<ApprovalPendingResponse> =
        apiService.approvalPending(sessionID).handleResult()

    suspend fun respondApproval(
        sessionID: String,
        choice: String,
        approvalID: String?
    ): NetworkResult<ApprovalRespondResponse> {
        val req = ApprovalRespondRequest(sessionId = sessionID, choice = choice, approvalId = approvalID)
        return apiService.respondApproval(req).handleResult()
    }

    suspend fun clarifyPending(sessionID: String): NetworkResult<ClarificationPendingResponse> =
        apiService.clarifyPending(sessionID).handleResult()

    suspend fun respondClarification(
        sessionID: String,
        response: String,
        clarifyID: String?
    ): NetworkResult<ClarificationRespondResponse> {
        val req = ClarificationRespondRequest(sessionId = sessionID, response = response, clarifyId = clarifyID)
        return apiService.respondClarification(req).handleResult()
    }

    suspend fun steerChat(sessionID: String, text: String): NetworkResult<ChatSteerResponse> {
        return apiService.steerChat(ChatSteerRequest(sessionID, text)).handleResult()
    }

    suspend fun submitGoal(
        sessionID: String,
        args: String,
        workspace: String?,
        model: String?,
        modelProvider: String?,
        profile: String?
    ): NetworkResult<GoalSubmissionResponse> {
        val req = GoalSubmissionRequest(sessionID, args, workspace, model, modelProvider, profile)
        return apiService.submitGoal(req).handleResult()
    }

    suspend fun startBtw(sessionID: String, question: String): NetworkResult<BtwStartResponse> =
        apiService.startBtw(BtwRequest(sessionID, question)).handleResult()

    suspend fun startBackground(sessionID: String, prompt: String): NetworkResult<BackgroundStartResponse> =
        apiService.startBackground(BackgroundRequest(sessionID, prompt)).handleResult()

    suspend fun backgroundStatus(sessionID: String): NetworkResult<BackgroundStatusResponse> =
        apiService.backgroundStatus(sessionID).handleResult()

    // --- Session Methods ---
    suspend fun sessions(): NetworkResult<SessionsResponse> = apiService.sessions().handleResult()

    suspend fun searchSessions(
        query: String,
        content: Boolean,
        depth: Int
    ): NetworkResult<SessionSearchResponse> =
        apiService.searchSessions(query, content, depth).handleResult()

    suspend fun session(
        id: String,
        includeMessages: Boolean,
        messageLimit: Int?,
        messageBefore: Int?,
        expandRenderable: Boolean
    ): NetworkResult<SessionResponse> =
        apiService.session(id, includeMessages, messageLimit, messageBefore, expandRenderable).handleResult()

    suspend fun sessionStatus(id: String): NetworkResult<SessionStatusResponse> =
        apiService.sessionStatus(id).handleResult()

    suspend fun createSession(
        workspace: String?,
        model: String?,
        modelProvider: String?,
        profile: String?
    ): NetworkResult<SessionResponse> {
        val req = NewSessionRequest(workspace, model, modelProvider, profile)
        return apiService.createSession(req).handleResult()
    }

    suspend fun renameSession(id: String, title: String): NetworkResult<SessionMutationResponse> =
        apiService.renameSession(RenameSessionRequest(id, title)).handleResult()

    suspend fun deleteSession(id: String): NetworkResult<SessionMutationResponse> =
        apiService.deleteSession(SessionIDRequest(id)).handleResult()

    suspend fun pinSession(id: String, pinned: Boolean): NetworkResult<SessionMutationResponse> =
        apiService.pinSession(PinSessionRequest(id, pinned)).handleResult()

    suspend fun archiveSession(id: String, archived: Boolean): NetworkResult<SessionMutationResponse> =
        apiService.archiveSession(ArchiveSessionRequest(id, archived)).handleResult()

    suspend fun branchSession(
        id: String,
        keepCount: Int?,
        title: String?
    ): NetworkResult<SessionBranchResponse> =
        apiService.branchSession(BranchSessionRequest(id, keepCount, title)).handleResult()

    suspend fun compressSession(
        id: String,
        focusTopic: String?
    ): NetworkResult<SessionCompressResponse> =
        apiService.compressSession(CompressSessionRequest(id, focusTopic)).handleResult()

    suspend fun undoSession(id: String): NetworkResult<SessionUndoResponse> =
        apiService.undoSession(SessionIDRequest(id)).handleResult()

    suspend fun retrySession(id: String): NetworkResult<SessionRetryResponse> =
        apiService.retrySession(SessionIDRequest(id)).handleResult()

    suspend fun truncateSession(id: String, keepCount: Int): NetworkResult<SessionResponse> =
        apiService.truncateSession(TruncateSessionRequest(id, keepCount)).handleResult()

    suspend fun updateSession(
        id: String,
        workspace: String?,
        model: String?,
        modelProvider: String?
    ): NetworkResult<SessionResponse> {
        val req = UpdateSessionRequest(id, workspace, model, modelProvider)
        return apiService.updateSession(req).handleResult()
    }

    suspend fun moveSession(id: String, projectID: String?): NetworkResult<SessionMutationResponse> =
        apiService.moveSession(MoveSessionRequest(id, projectID)).handleResult()

    suspend fun sessionYolo(sessionID: String): NetworkResult<SessionYoloResponse> =
        apiService.sessionYolo(sessionID).handleResult()

    suspend fun setSessionYolo(sessionID: String, enabled: Boolean): NetworkResult<SessionYoloResponse> =
        apiService.setSessionYolo(SessionYoloRequest(sessionID, enabled)).handleResult()

    // --- Cron Methods ---
    suspend fun crons(): NetworkResult<CronJobsResponse> = apiService.crons().handleResult()

    suspend fun createCron(
        prompt: String,
        schedule: String,
        name: String?,
        deliver: String?,
        skills: List<String>,
        model: String?,
        profile: String?,
        toastNotifications: Boolean
    ): NetworkResult<CronMutationResponse> {
        val req = CronCreateRequest(prompt, schedule, name, deliver, skills, model, profile, toastNotifications)
        return apiService.createCron(req).handleResult()
    }

    suspend fun updateCron(
        jobID: String,
        prompt: String?,
        schedule: String?,
        name: String?,
        deliver: String?,
        skills: List<String>?,
        model: String?,
        profile: String?,
        toastNotifications: Boolean?
    ): NetworkResult<CronMutationResponse> {
        val req = CronUpdateRequest(jobID, prompt, schedule, name, deliver, skills, model, profile, toastNotifications)
        return apiService.updateCron(req).handleResult()
    }

    suspend fun deleteCron(jobID: String): NetworkResult<CronMutationResponse> =
        apiService.deleteCron(CronJobIDRequest(jobID, null)).handleResult()

    suspend fun runCron(jobID: String): NetworkResult<CronMutationResponse> =
        apiService.runCron(CronJobIDRequest(jobID, null)).handleResult()

    suspend fun pauseCron(jobID: String, reason: String?): NetworkResult<CronMutationResponse> =
        apiService.pauseCron(CronJobIDRequest(jobID, reason)).handleResult()

    suspend fun resumeCron(jobID: String): NetworkResult<CronMutationResponse> =
        apiService.resumeCron(CronJobIDRequest(jobID, null)).handleResult()

    // --- SSE URL Generators (Returns URL string for EventSource) ---
    fun getChatStreamURL(streamID: String, replayAfterSeq: Int?): String {
        val url = "${BASE_URL}chat/$streamID/stream"
        return if (replayAfterSeq != null) {
            val seq = maxOf(0, replayAfterSeq)
            "$url?replay=1&after_seq=$seq"
        } else {
            url
        }
    }

    fun getApprovalStreamURL(sessionID: String): String =
        "${BASE_URL}approval/$sessionID/stream"

    fun getClarifyStreamURL(sessionID: String): String =
        "${BASE_URL}clarify/$sessionID/stream"
}