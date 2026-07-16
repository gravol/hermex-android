package com.hermex.core.network

import kotlinx.serialization.Serializable

// ── Empty body for POST with no fields ──

@Serializable
object EmptyBody

// ── Health ──

@Serializable
data class StatusResponse(
    val status: String = "",
    val platform: String? = null,
    val version: String? = null,
)

// ── Sessions (Hermes API Server v0.18.0) ──

/** GET /api/sessions */
@Serializable
data class SessionsResponse(
    val `object`: String = "list",
    val data: List<Session> = emptyList(),
)

@Serializable
data class Session(
    val id: String = "",
    val title: String? = null,
    val source: String? = null,
    val model: String? = null,
    val startedAt: Double? = null,
    val endedAt: Double? = null,
    val endReason: String? = null,
    val messageCount: Int = 0,
    val toolCallCount: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val lastActive: Double? = null,
    val preview: String? = null,
)

/** GET /api/sessions/{id} */
@Serializable
data class SessionResponse(
    val session: Session? = null,
)

/** DELETE /api/sessions/{id} */
@Serializable
data class SessionDeleteResponse(
    val success: Boolean = false,
)

/** POST /api/sessions — create */
@Serializable
data class NewSessionRequest(
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
)

/** PATCH /api/sessions/{id} — update metadata */
@Serializable
data class PatchSessionRequest(
    val title: String? = null,
)

// ── Messages ──

/** GET /api/sessions/{id}/messages */
@Serializable
data class SessionMessagesResponse(
    val messages: List<Message> = emptyList(),
)

@Serializable
data class Message(
    val id: Int? = null,
    val role: String = "",
    val content: String = "",
    val createdAt: String? = null,
)

// ── Chat ──

/** POST /api/sessions/{id}/chat */
@Serializable
data class ChatRequest(
    val message: String,
    val workspace: String? = null,
)

@Serializable
data class ChatStreamResponse(
    val streamId: String = "",
    val sessionId: String = "",
    val status: String = "",
)
