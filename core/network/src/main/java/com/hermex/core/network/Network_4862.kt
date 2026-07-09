package com.example.hermes.networking

import kotlinx.serialization.Serializable

// --- Request DTOs ---

@Serializable
data class ChatStartRequest(
    val sessionId: String,
    val message: String,
    val workspace: String?,
    val model: String?,
    val modelProvider: String?,
    val profile: String?,
    val explicitModelPick: Boolean?,
    val attachments: List<JsonElement>? // Map JSONValue to List<JsonElement>
)

@Serializable
data class ChatSteerRequest(
    val sessionId: String,
    val text: String
)

@Serializable
data class GoalSubmissionRequest(
    val sessionId: String,
    val args: String,
    val workspace: String?,
    val model: String?,
    val modelProvider: String?,
    val profile: String?
)

@Serializable
data class ApprovalRespondRequest(
    val sessionId: String,
    val choice: String, // Assuming ApprovalChoice is a String or enum serialized as String
    val approvalId: String?
)

@Serializable
data class ClarificationRespondRequest(
    val sessionId: String,
    val response: String,
    val clarifyId: String?
)

@Serializable
data class BtwRequest(
    val sessionId: String,
    val question: String
)

@Serializable
data class BackgroundRequest(
    val sessionId: String,
    val prompt: String
)

@Serializable
data class NewSessionRequest(
    val workspace: String?,
    val model: String?,
    val modelProvider: String?,
    val profile: String?
)

@Serializable
data class RenameSessionRequest(
    val sessionId: String,
    val title: String
)

@Serializable
data class SessionIDRequest(
    val sessionId: String
)

@Serializable
data class PinSessionRequest(
    val sessionId: String,
    val pinned: Boolean
)

@Serializable
data class ArchiveSessionRequest(
    val sessionId: String,
    val archived: Boolean
)

@Serializable
data class BranchSessionRequest(
    val sessionId: String,
    val keepCount: Int?,
    val title: String?
)

@Serializable
data class CompressSessionRequest(
    val sessionId: String,
    val focusTopic: String?
)

@Serializable
data class TruncateSessionRequest(
    val sessionId: String,
    val keepCount: Int
)

@Serializable
data class UpdateSessionRequest(
    val sessionId: String,
    val workspace: String?,
    val model: String?,
    val modelProvider: String?
)

@Serializable
data class MoveSessionRequest(
    val sessionId: String,
    val projectId: String?
)

@Serializable
data class SessionYoloRequest(
    val sessionId: String,
    val enabled: Boolean
)

@Serializable
data class CronCreateRequest(
    val prompt: String,
    val schedule: String,
    val name: String?,
    val deliver: String?,
    val skills: List<String>,
    val model: String?,
    val profile: String?,
    val toastNotifications: Boolean
)

@Serializable
data class CronUpdateRequest(
    val jobId: String,
    val prompt: String?,
    val schedule: String?,
    val name: String?,
    val deliver: String?,
    val skills: List<String>?,
    val model: String?,
    val profile: String?,
    val toastNotifications: Boolean?
)

@Serializable
data class CronJobIDRequest(
    val jobId: String,
    val reason: String?
)

// --- Response DTOs (Placeholders based on Swift names) ---

@Serializable
data class ChatStartResponse(
    val success: Boolean
)

@Serializable
data class ChatCancelResponse(val success: Boolean)
@Serializable
data class ChatStreamStatusResponse(val status: String)
@Serializable
data class ApprovalPendingResponse(val pending: Boolean)
@Serializable
data class ApprovalRespondResponse(val success: Boolean)
@Serializable
data class ClarificationPendingResponse(val pending: Boolean)
@Serializable
data class ClarificationRespondResponse(val success: Boolean)
@Serializable
data class ChatSteerResponse(val success: Boolean)
@Serializable
data class GoalSubmissionResponse(val success: Boolean)
@Serializable
data class BtwStartResponse(val success: Boolean)
@Serializable
data class BackgroundStartResponse(val success: Boolean)
@Serializable
data class BackgroundStatusResponse(val status: String)

@Serializable
data class SessionsResponse(val sessions: List<Session>)
@Serializable
data class SessionSearchResponse(val sessions: List<Session>)
@Serializable
data class SessionResponse(val session: Session)
@Serializable
data class SessionStatusResponse(val status: String)
@Serializable
data class SessionMutationResponse(val success: Boolean)
@Serializable
data class SessionBranchResponse(val newSessionId: String)
@Serializable
data class SessionCompressResponse(val success: Boolean)
@Serializable
data class SessionUndoResponse(val success: Boolean)
@Serializable
data class SessionRetryResponse(val success: Boolean)
@Serializable
data class SessionYoloResponse(val enabled: Boolean)

@Serializable
data class CronJobsResponse(val jobs: List<CronJob>)
@Serializable
data class CronMutationResponse(val success: Boolean)