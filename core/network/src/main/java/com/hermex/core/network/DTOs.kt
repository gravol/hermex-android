package com.hermex.core.network

import kotlinx.serialization.SerialName
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

// ── Session listing ──

/** GET /api/sessions */
@Serializable
data class SessionsResponse(
    val `object`: String = "list",
    val data: List<SessionSummary> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
)

@Serializable
data class SessionSummary(
    val id: String = "",
    val title: String? = null,
    val source: String? = null,
    val model: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("ended_at") val endedAt: Double? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("last_active") val lastActive: Double? = null,
    val preview: String? = null,
)

// ── Session detail (GET /api/sessions/{id}) ──

/** GET /api/sessions/{id}?includeMessages=true */
@Serializable
data class SessionDetail(
    val id: String = "",
    val title: String? = null,
    val source: String? = null,
    val model: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("ended_at") val endedAt: Double? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("last_active") val lastActive: Double? = null,
    val preview: String? = null,
    val messages: List<ChatMessage>? = null,
)

// ── Messages ──

/** GET /api/sessions/{id}/messages */
@Serializable
data class MessagesResponse(
    val `object`: String = "list",
    @SerialName("session_id") val sessionId: String = "",
    val data: List<ChatMessage> = emptyList(),
)

@Serializable
data class ChatMessage(
    val id: Int = 0,
    @SerialName("session_id") val sessionId: String? = null,
    val role: String = "",
    val content: String = "",
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallData>? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ToolCallData(
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunction? = null,
)

@Serializable
data class ToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)

// ── Session create / delete ──

/** POST /api/sessions → returns session object directly (NOT wrapped in {session: ...}) */
// Reuses SessionDetail — the wire returns the same shape.

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
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
)

/** PATCH /api/sessions/{id} — update metadata */
@Serializable
data class PatchSessionRequest(
    val title: String? = null,
)

// ── Chat request ──

/** POST /api/sessions/{id}/chat or /chat/stream */
@Serializable
data class ChatRequest(
    val message: String,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
)

// ── SSE Event types (polymorphic, keyed on event name) ──

/**
 * Sealed hierarchy of all SSE events emitted by the Hermes API Server
 * chat stream. Each event has a [type] matching the SSE `event:` field.
 * Unknown future event types decode to [SseEvent.Unknown] — never crash.
 */
@Serializable
sealed class SseEvent {

    /** SSE `event:` field — used for polymorphic dispatch. */
    abstract val eventType: String

    @Serializable
    @SerialName("run.started")
    data class RunStarted(
        override val eventType: String = "run.started",
        @SerialName("user_message") val userMessage: String? = null,
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("run_id") val runId: String? = null,
        val seq: Int? = null,
        val ts: Double? = null,
    ) : SseEvent()

    @Serializable
    @SerialName("message.started")
    data class MessageStarted(
        override val eventType: String = "message.started",
        val message: MessageStartedPayload? = null,
        @SerialName("run_id") val runId: String? = null,
    ) : SseEvent()

    @Serializable
    data class MessageStartedPayload(
        val id: String? = null,
        val role: String? = null,
    )

    @Serializable
    @SerialName("assistant.delta")
    data class AssistantDelta(
        override val eventType: String = "assistant.delta",
        @SerialName("message_id") val messageId: String? = null,
        val delta: String? = null,
        val seq: Int? = null,
        val ts: Double? = null,
    ) : SseEvent()

    @Serializable
    @SerialName("tool.progress")
    data class ToolProgress(
        override val eventType: String = "tool.progress",
        @SerialName("message_id") val messageId: String? = null,
        @SerialName("tool_name") val toolName: String? = null,
        val delta: String? = null,
    ) : SseEvent()

    @Serializable
    @SerialName("tool.started")
    data class ToolStarted(
        override val eventType: String = "tool.started",
        @SerialName("message_id") val messageId: String? = null,
        @SerialName("tool_name") val toolName: String? = null,
        val preview: String? = null,
        val args: kotlinx.serialization.json.JsonElement? = null,
    ) : SseEvent()

    @Serializable
    @SerialName("tool.completed")
    data class ToolCompleted(
        override val eventType: String = "tool.completed",
        @SerialName("message_id") val messageId: String? = null,
        @SerialName("tool_name") val toolName: String? = null,
    ) : SseEvent()

    @Serializable
    @SerialName("message.completed")
    data class MessageCompleted(
        override val eventType: String = "message.completed",
        val message: ChatMessage? = null,
        val usage: UsageData? = null,
    ) : SseEvent()

    @Serializable
    data class UsageData(
        @SerialName("prompt_tokens") val promptTokens: Int? = null,
        @SerialName("completion_tokens") val completionTokens: Int? = null,
        @SerialName("total_tokens") val totalTokens: Int? = null,
        @SerialName("estimated_cost_usd") val estimatedCostUsd: Double? = null,
    )

    @Serializable
    @SerialName("run.completed")
    data class RunCompleted(
        override val eventType: String = "run.completed",
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("run_id") val runId: String? = null,
        val ts: Double? = null,
    ) : SseEvent()

    /** Terminal — stream has ended cleanly. No further events. */
    @Serializable
    @SerialName("done")
    data class Done(
        override val eventType: String = "done",
    ) : SseEvent()

    /**
     * Catch-all for unknown future event types.
     * `rawData` holds the complete JSON payload so the UI can at least log it.
     */
    @Serializable
    data class Unknown(
        override val eventType: String = "",
        @SerialName("raw_data") val rawData: kotlinx.serialization.json.JsonElement? = null,
    ) : SseEvent()
}

/**
 * SSE line parser. Splits `event:` and `data:` lines into typed [SseEvent] instances.
 * Call [feed] for each line from the OkHttp SSE EventSource.
 */
object SseParser {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var currentEvent: String? = null
    private var dataBuffer = StringBuilder()

    /** Feed one SSE line. Returns a parsed [SseEvent] when a complete event is assembled, or null. */
    fun feed(line: String): SseEvent? {
        val trimmed = line.trimEnd('\r', '\n')
        when {
            trimmed.startsWith("event:") -> {
                currentEvent = trimmed.removePrefix("event:").trim()
            }
            trimmed.startsWith("data:") -> {
                dataBuffer.append(trimmed.removePrefix("data:").trim())
            }
            trimmed.isEmpty() && dataBuffer.isNotEmpty() -> {
                // Empty line = end of event
                val data = dataBuffer.toString()
                val eventName = currentEvent ?: "message"
                dataBuffer = StringBuilder()
                currentEvent = null
                return decodeEvent(eventName, data)
            }
            trimmed.startsWith(":") -> { /* heartbeat comment — ignore */ }
        }
        return null
    }

    private fun decodeEvent(name: String, data: String): SseEvent {
        return try {
            when (name) {
                "run.started"       -> json.decodeFromString(SseEvent.RunStarted.serializer(), data)
                "message.started"   -> json.decodeFromString(SseEvent.MessageStarted.serializer(), data)
                "assistant.delta"   -> json.decodeFromString(SseEvent.AssistantDelta.serializer(), data)
                "tool.progress"     -> json.decodeFromString(SseEvent.ToolProgress.serializer(), data)
                "tool.started"      -> json.decodeFromString(SseEvent.ToolStarted.serializer(), data)
                "tool.completed"    -> json.decodeFromString(SseEvent.ToolCompleted.serializer(), data)
                "message.completed" -> json.decodeFromString(SseEvent.MessageCompleted.serializer(), data)
                "run.completed"     -> json.decodeFromString(SseEvent.RunCompleted.serializer(), data)
                "done"              -> SseEvent.Done()
                else                -> SseEvent.Unknown(
                    eventType = name,
                    rawData = kotlinx.serialization.json.Json.parseToJsonElement(data),
                )
            }
        } catch (_: Exception) {
            SseEvent.Unknown(eventType = name, rawData = null)
        }
    }

    /** Reset parser state for a new stream. */
    fun reset() {
        currentEvent = null
        dataBuffer = StringBuilder()
    }
}
