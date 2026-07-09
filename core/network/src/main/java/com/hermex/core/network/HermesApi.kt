package com.example.hermes.networking

import okhttp3.ResponseBody
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.Body
import retrofit2.http.Query

interface HermesApiService {

    // --- Chat ---
    @POST("chat/start")
    suspend fun startChat(@Body request: ChatStartRequest): Response<ChatStartResponse>

    @GET("chat/{streamId}/status")
    suspend fun chatStreamStatus(@Path("streamId") streamId: String): Response<ChatStreamStatusResponse>

    @GET("chat/{streamId}/cancel")
    suspend fun cancelChat(@Path("streamId") streamId: String): Response<ChatCancelResponse>

    @GET("approval/{sessionId}/pending")
    suspend fun approvalPending(@Path("sessionId") sessionId: String): Response<ApprovalPendingResponse>

    @POST("approval/respond")
    suspend fun respondApproval(@Body request: ApprovalRespondRequest): Response<ApprovalRespondResponse>

    @GET("clarify/{sessionId}/pending")
    suspend fun clarifyPending(@Path("sessionId") sessionId: String): Response<ClarificationPendingResponse>

    @POST("clarify/respond")
    suspend fun respondClarification(@Body request: ClarificationRespondRequest): Response<ClarificationRespondResponse>

    @POST("chat/steer")
    suspend fun steerChat(@Body request: ChatSteerRequest): Response<ChatSteerResponse>

    @POST("goal/submit")
    suspend fun submitGoal(@Body request: GoalSubmissionRequest): Response<GoalSubmissionResponse>

    @POST("btw/start")
    suspend fun startBtw(@Body request: BtwRequest): Response<BtwStartResponse>

    @POST("background/start")
    suspend fun startBackground(@Body request: BackgroundRequest): Response<BackgroundStartResponse>

    @GET("background/{sessionId}/status")
    suspend fun backgroundStatus(@Path("sessionId") sessionId: String): Response<BackgroundStatusResponse>

    // --- Sessions ---
    @GET("sessions")
    suspend fun sessions(): Response<SessionsResponse>

    @GET("sessions/search")
    suspend fun searchSessions(
        @Query("query") query: String,
        @Query("content") content: Boolean = true,
        @Query("depth") depth: Int = 5
    ): Response<SessionSearchResponse>

    @GET("session/{id}")
    suspend fun session(
        @Path("id") id: String,
        @Query("includeMessages") includeMessages: Boolean = true,
        @Query("messageLimit") messageLimit: Int? = 50,
        @Query("messageBefore") messageBefore: Int? = null,
        @Query("expandRenderable") expandRenderable: Boolean = false
    ): Response<SessionResponse>

    @GET("session/{id}/status")
    suspend fun sessionStatus(@Path("id") id: String): Response<SessionStatusResponse>

    @POST("session/new")
    suspend fun createSession(@Body request: NewSessionRequest): Response<SessionResponse>

    @POST("session/rename")
    suspend fun renameSession(@Body request: RenameSessionRequest): Response<SessionMutationResponse>

    @POST("session/delete")
    suspend fun deleteSession(@Body request: SessionIDRequest): Response<SessionMutationResponse>

    @POST("session/pin")
    suspend fun pinSession(@Body request: PinSessionRequest): Response<SessionMutationResponse>

    @POST("session/archive")
    suspend fun archiveSession(@Body request: ArchiveSessionRequest): Response<SessionMutationResponse>

    @POST("session/branch")
    suspend fun branchSession(@Body request: BranchSessionRequest): Response<SessionBranchResponse>

    @POST("session/compress")
    suspend fun compressSession(@Body request: CompressSessionRequest): Response<SessionCompressResponse>

    @POST("session/undo")
    suspend fun undoSession(@Body request: SessionIDRequest): Response<SessionUndoResponse>

    @POST("session/retry")
    suspend fun retrySession(@Body request: SessionIDRequest): Response<SessionRetryResponse>

    @POST("session/truncate")
    suspend fun truncateSession(@Body request: TruncateSessionRequest): Response<SessionResponse>

    @POST("session/update")
    suspend fun updateSession(@Body request: UpdateSessionRequest): Response<SessionResponse>

    @POST("session/move")
    suspend fun moveSession(@Body request: MoveSessionRequest): Response<SessionMutationResponse>

    @GET("session/{sessionId}/yolo")
    suspend fun sessionYolo(@Path("sessionId") sessionID: String): Response<SessionYoloResponse>

    @POST("session/yolo")
    suspend fun setSessionYolo(@Body request: SessionYoloRequest): Response<SessionYoloResponse>

    // --- Cron ---
    @GET("crons")
    suspend fun crons(): Response<CronJobsResponse>

    @POST("cron/create")
    suspend fun createCron(@Body request: CronCreateRequest): Response<CronMutationResponse>

    @POST("cron/update")
    suspend fun updateCron(@Body request: CronUpdateRequest): Response<CronMutationResponse>

    @POST("cron/delete")
    suspend fun deleteCron(@Body request: CronJobIDRequest): Response<CronMutationResponse>

    @POST("cron/run")
    suspend fun runCron(@Body request: CronJobIDRequest): Response<CronMutationResponse>

    @POST("cron/pause")
    suspend fun pauseCron(@Body request: CronJobIDRequest): Response<CronMutationResponse>

    @POST("cron/resume")
    suspend fun resumeCron(@Body request: CronJobIDRequest): Response<CronMutationResponse>

    // SSE Endpoints (Return raw ResponseBody for EventSource parsing)
    @GET("chat/{streamId}/stream")
    suspend fun chatStreamURL(@Path("streamId") streamId: String, @Query("replay") replay: Int?, @Query("after_seq") afterSeq: Int?): Response<ResponseBody>

    @GET("approval/{sessionId}/stream")
    suspend fun approvalStreamURL(@Path("sessionId") sessionId: String): Response<ResponseBody>

    @GET("clarify/{sessionId}/stream")
    suspend fun clarifyStreamURL(@Path("sessionId") sessionId: String): Response<ResponseBody>
}