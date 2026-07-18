package com.hermex.android.feature.chat

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.core.network.ApiClient
import com.hermex.core.network.ChatMessage
import com.hermex.core.network.DebugLog
import com.hermex.core.network.NetworkResult
import com.hermex.core.network.SseEvent
import com.hermex.core.network.SseParser
import com.hermex.core.network.ToolCallData
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

// ── UI models ──

data class UiMessage(
    val id: String,
    val role: String,      // "user" | "assistant"
    val content: String = "",
    val isStreaming: Boolean = false,
    val isWaitingForFirstEvent: Boolean = false,  // true from send until first SSE event
    val thinkingText: String? = null,
    val thinkingExpanded: Boolean = true,
    val thinkingHasContent: Boolean = false,  // true once first assistant.delta arrives
    val toolCalls: List<UiToolCall> = emptyList(),
    val usage: UiUsage? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class UiToolCall(
    val id: String,
    val toolName: String,
    val preview: String? = null,
    val args: String? = null,
    val completed: Boolean = false,
)

data class UiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double? = null,
)

data class ChatUiState(
    val sessionTitle: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val scrollGeneration: Long = 0L,  // bumped on every SSE-driven list mutation; triggers auto-scroll
    val pendingApproval: PendingApproval? = null,  // non-null when tool needs approval
)

/**
 * A tool call waiting for user approval.
 */
data class PendingApproval(
    val toolName: String,
    val toolArgs: String = "",
    val requestId: String = "",
)

// ── ViewModel ──

class ChatViewModel(application: Application) : ChatViewModelContract(application) {

    // Compose snapshot state — no StateFlow conflation, every write feeds the next frame
    override var uiState by mutableStateOf(ChatUiState())

    private var sessionId: String = ""
    private var sessionTitle: String = ""
    private var activeEventSource: EventSource? = null
    private val tempIdCounter = AtomicInteger(0)

    // ── Public API ──

    /** Initialize with a session. Call once from the composable. */
    override fun init(sessionId: String, title: String?) {
        if (this.sessionId == sessionId) return
        this.sessionId = sessionId
        this.sessionTitle = title ?: sessionId.take(16)
        uiState = ChatUiState(sessionTitle = this.sessionTitle)
        loadMessages()
    }

    override fun loadMessages() {
        if (sessionId.isEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = ApiClient.sessionMessages(sessionId)) {
                is NetworkResult.Success -> {
                    val messages = result.data.data.map { it.toUiMessage() }
                    uiState = uiState.copy(
                        isLoading = false,
                        messages = messages,
                        error = null,
                    )
                }
                is NetworkResult.HttpError -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "Server error (${result.code})",
                    )
                }
                is NetworkResult.Error -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = result.exception.message ?: "Failed to load messages",
                    )
                }
            }
        }
    }

    override fun sendMessage(text: String) {
        if (text.isBlank() || sessionId.isEmpty()) return

        // Cancel any previous stream before opening a new one
        activeEventSource?.cancel()
        activeEventSource = null

        val userMsgId = "user_${tempIdCounter.incrementAndGet()}"
        val assistantMsgId = "asst_${tempIdCounter.incrementAndGet()}"
        val now = System.currentTimeMillis()

        // Add user message immediately
        val userMsg = UiMessage(id = userMsgId, role = "user", content = text, timestamp = now)
        val current = uiState.messages.toMutableList()
        current.add(userMsg)

        // Add empty streaming assistant message placeholder
        val asstMsg = UiMessage(id = assistantMsgId, role = "assistant", isStreaming = true, isWaitingForFirstEvent = true, timestamp = now)
        current.add(asstMsg)

        uiState = uiState.copy(
            messages = current,
            isStreaming = true,
            error = null,
        )

        SseParser.reset()

        activeEventSource = ApiClient.openChatStream(
            sessionId = sessionId,
            message = text,
            listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (!type.isNullOrBlank()) {
                        SseParser.feed("event: $type")
                    }
                    SseParser.feed("data: $data")
                    val parsed = SseParser.feed("")
                    if (parsed != null) {
                        DebugLog.sse("Parser", "parsed event: ${parsed.eventType}")
                        handleSseEvent(parsed)
                    } else {
                        DebugLog.sse("Parser", "no complete event yet (type=${type ?: "null"})")
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    Log.e("Hermex", "SSE stream failure", t)
                    val code = response?.code
                    val errMsg = when {
                        t != null -> t.message ?: "Stream connection lost"
                        code != null -> "Server error ($code)"
                        else -> "Stream connection lost"
                    }
                    val msgs = uiState.messages.toMutableList()
                    val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(isStreaming = false)
                    }
                    uiState = uiState.copy(
                        messages = msgs,
                        isStreaming = false,
                        error = errMsg,
                    )
                }

                override fun onClosed(eventSource: EventSource) {
                    Log.d("Hermex", "SSE stream closed")
                    val msgs = uiState.messages.toMutableList()
                    val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(isStreaming = false)
                    }
                    uiState = uiState.copy(messages = msgs, isStreaming = false)
                }
            },
        )
    }

    override fun stopStreaming() {
        activeEventSource?.cancel()
        activeEventSource = null
        uiState = uiState.copy(isStreaming = false)
    }

    override fun toggleThinking(messageId: String) {
        val msgs = uiState.messages.toMutableList()
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            msgs[idx] = msgs[idx].copy(thinkingExpanded = !msgs[idx].thinkingExpanded)
            uiState = uiState.copy(messages = msgs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }

    // ── SSE event handler ──

    private fun handleSseEvent(event: SseEvent) {
        val msgs = uiState.messages.toMutableList()

        when (event) {
            is SseEvent.RunStarted -> { /* no UI change needed */ }

            is SseEvent.MessageStarted -> {
                DebugLog.sse("Handler", "message.started — updating assistant message ID")
                event.message?.id?.let { serverId ->
                    val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(id = serverId)
                    }
                }
            }

            is SseEvent.AssistantDelta -> {
                event.messageId?.let { msgId ->
                    val idx = msgs.indexOfLast { it.role == "assistant" }
                    if (idx >= 0) {
                        val cur = msgs[idx]
                        // Create NEW immutable copy with updated content —
                        // Compose detects the new instance and recomposes the bubble
                        msgs[idx] = cur.copy(
                            id = if (cur.id.startsWith("asst_")) msgId else cur.id,
                            content = cur.content + (event.delta ?: ""),
                            thinkingHasContent = true,
                            isWaitingForFirstEvent = false,
                        )
                        DebugLog.sse("Handler", "delta → msg[$idx] content now ${msgs[idx].content.length} chars")
                    } else {
                        DebugLog.sse("Handler", "delta → NO assistant message found in list!")
                    }
                }
            }

            is SseEvent.ToolProgress -> {
                if (event.toolName == "_thinking") {
                    val idx = msgs.indexOfLast { it.role == "assistant" }
                    if (idx >= 0) {
                        val cur = msgs[idx]
                        val existing = cur.thinkingText ?: ""
                        msgs[idx] = cur.copy(thinkingText = existing + (event.delta ?: ""), isWaitingForFirstEvent = false)
                    }
                }
            }

            is SseEvent.ToolStarted -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val existing = cur.toolCalls.toMutableList()
                    if (event.toolName != "_thinking") {
                        val tc = UiToolCall(
                            id = event.messageId ?: "tc_${existing.size}",
                            toolName = event.toolName ?: "unknown",
                            preview = event.preview,
                            args = event.args?.toString(),
                        )
                        existing.add(tc)
                    }
                    msgs[idx] = cur.copy(toolCalls = existing)
                }
            }

            is SseEvent.ToolCompleted -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val updated = cur.toolCalls.map { tc ->
                        if (tc.toolName == event.toolName) tc.copy(completed = true) else tc
                    }
                    msgs[idx] = cur.copy(toolCalls = updated)
                }
            }

            is SseEvent.MessageCompleted -> {
                DebugLog.sse("Handler", "message/assistant.completed — finalizing message, clearing stream")
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val finalContent = event.message?.content?.takeIf { it.isNotBlank() } ?: cur.content
                    val finalTools = event.message?.toolCalls?.map {
                        UiToolCall(
                            id = it.id ?: "tc",
                            toolName = it.function?.name ?: "unknown",
                            completed = true,
                        )
                    } ?: cur.toolCalls
                    val usage = event.usage?.let {
                        UiUsage(it.promptTokens ?: 0, it.completionTokens ?: 0, it.totalTokens ?: 0, it.estimatedCostUsd)
                    }
                    msgs[idx] = cur.copy(
                        id = event.message?.id?.toString() ?: cur.id,
                        content = finalContent,
                        toolCalls = finalTools,
                        isStreaming = false,
                        thinkingExpanded = false,
                        thinkingHasContent = true,
                        usage = usage,
                    )
                }
                uiState = uiState.copy(
                    messages = msgs,
                    isStreaming = false,
                    scrollGeneration = uiState.scrollGeneration + 1,
                )
                return  // already set uiState above
            }

            is SseEvent.RunCompleted -> {
                uiState = uiState.copy(isStreaming = false)
            }

            is SseEvent.Done -> {
                uiState = uiState.copy(isStreaming = false)
            }

            is SseEvent.Unknown -> {
                Log.w("Hermex", "Unknown SSE event: ${event.eventType}")
                DebugLog.sse("Chat", "Unknown event: ${event.eventType} — raw: ${event.rawData}")
            }
        }

        // Emit updated state — mutableStateOf writes to Compose's snapshot,
        // triggering recomposition in the next frame. No conflation.
        // scrollGeneration bump triggers auto-scroll in ChatScreen for ANY
        // mutation (deltas, tool calls, thinking) — not just content changes.
        uiState = uiState.copy(messages = msgs, scrollGeneration = uiState.scrollGeneration + 1)
    }

    // ── Mapping ──

    private fun ChatMessage.toUiMessage(): UiMessage = UiMessage(
        id = id.toString(),
        role = role,
        content = content,
        thinkingExpanded = false,
        thinkingHasContent = true,
        toolCalls = toolCalls?.map {
            UiToolCall(id = it.id ?: "tc", toolName = it.function?.name ?: "unknown", completed = true)
        } ?: emptyList(),
        timestamp = parseTimestamp(createdAt),
    )

    private fun parseTimestamp(createdAt: String?): Long {
        if (createdAt == null) return System.currentTimeMillis()
        return try {
            Instant.parse(createdAt).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
