package com.hermex.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Chat ──

@Serializable
data class ChatStartRequest(
    val sessionId: String,
    val message: String,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
    val explicitModelPick: Boolean? = null,
    val attachments: List<JsonElement>? = null,
)

@Serializable data class ChatStartResponse(val success: Boolean = false)
@Serializable data class ChatCancelResponse(val success: Boolean = false)
@Serializable data class ChatStreamStatusResponse(val status: String = "")
@Serializable data class ChatSteerResponse(val success: Boolean = false)

@Serializable
data class ChatSteerRequest(val sessionId: String, val text: String)

// ── Goal ──

@Serializable
data class GoalSubmissionRequest(
    val sessionId: String,
    val args: String,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
)

@Serializable data class GoalSubmissionResponse(val success: Boolean = false)

// ── Approval ──

@Serializable
data class ApprovalRespondRequest(
    val sessionId: String,
    val choice: String,
    val approvalId: String? = null,
)

@Serializable data class ApprovalPendingResponse(val pending: Boolean = false)
@Serializable data class ApprovalRespondResponse(val success: Boolean = false)

// ── Clarification ──

@Serializable
data class ClarificationRespondRequest(
    val sessionId: String,
    val response: String,
    val clarifyId: String? = null,
)

@Serializable data class ClarificationPendingResponse(val pending: Boolean = false)
@Serializable data class ClarificationRespondResponse(val success: Boolean = false)

// ── BTW / Background ──

@Serializable
data class BtwRequest(val sessionId: String, val question: String)

@Serializable data class BtwStartResponse(val success: Boolean = false)

@Serializable
data class BackgroundRequest(val sessionId: String, val prompt: String)

@Serializable data class BackgroundStartResponse(val success: Boolean = false)
@Serializable data class BackgroundStatusResponse(val status: String = "")

// ── Sessions ──

@Serializable
data class NewSessionRequest(
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
)

@Serializable
data class RenameSessionRequest(val sessionId: String, val title: String)

@Serializable
data class SessionIDRequest(val sessionId: String)

@Serializable
data class PinSessionRequest(val sessionId: String, val pinned: Boolean)

@Serializable
data class ArchiveSessionRequest(val sessionId: String, val archived: Boolean)

@Serializable
data class BranchSessionRequest(
    val sessionId: String,
    val keepCount: Int? = null,
    val title: String? = null,
)

@Serializable
data class CompressSessionRequest(
    val sessionId: String,
    val focusTopic: String? = null,
)

@Serializable
data class TruncateSessionRequest(val sessionId: String, val keepCount: Int)

@Serializable
data class UpdateSessionRequest(
    val sessionId: String,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
)

@Serializable
data class MoveSessionRequest(val sessionId: String, val projectId: String? = null)

@Serializable
data class SessionYoloRequest(val sessionId: String, val enabled: Boolean)

@Serializable data class SessionsResponse(val sessions: List<Session> = emptyList())
@Serializable data class SessionSearchResponse(val sessions: List<Session> = emptyList())
@Serializable data class SessionResponse(val session: Session? = null)
@Serializable data class SessionStatusResponse(val status: String = "")
@Serializable data class SessionMutationResponse(val success: Boolean = false)
@Serializable data class SessionBranchResponse(val newSessionId: String = "")
@Serializable data class SessionCompressResponse(val success: Boolean = false)
@Serializable data class SessionUndoResponse(val success: Boolean = false)
@Serializable data class SessionRetryResponse(val success: Boolean = false)
@Serializable data class SessionYoloResponse(val enabled: Boolean = false)

// ── Cron ──

@Serializable
data class CronCreateRequest(
    val prompt: String,
    val schedule: String,
    val name: String? = null,
    val deliver: String? = null,
    val skills: List<String> = emptyList(),
    val model: String? = null,
    val profile: String? = null,
    val toastNotifications: Boolean = false,
)

@Serializable
data class CronUpdateRequest(
    val jobId: String,
    val prompt: String? = null,
    val schedule: String? = null,
    val name: String? = null,
    val deliver: String? = null,
    val skills: List<String>? = null,
    val model: String? = null,
    val profile: String? = null,
    val toastNotifications: Boolean? = null,
)

@Serializable
data class CronJobIDRequest(val jobId: String, val reason: String? = null)

@Serializable data class CronJobsResponse(val jobs: List<CronJob> = emptyList())
@Serializable data class CronMutationResponse(val success: Boolean = false)

// ── Auth ──

@Serializable
data class HealthResponse(
    val status: String = "",
    val authEnabled: Boolean = false,
    val passwordAuthEnabled: Boolean? = null,
)

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val token: String? = null,
)

// ── Placeholder types (to be replaced with real models) ──

@Serializable
data class Session(
    val id: String = "",
    val title: String? = null,
    val createdAt: String? = null,
    val status: String? = null,
)

@Serializable
data class CronJob(
    val id: String = "",
    val prompt: String = "",
    val schedule: String = "",
    val name: String? = null,
)
