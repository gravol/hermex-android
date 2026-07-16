package com.hermex.android.feature.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.core.network.ApiClient
import com.hermex.core.network.ChatMessage
import com.hermex.core.network.ChatRequest
import com.hermex.core.network.NetworkResult
import com.hermex.core.network.SseEvent
import com.hermex.core.network.SseParser
import com.hermex.core.network.ToolCallData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.util.concurrent.atomic.AtomicInteger

// ── UI models ──

data class UiMessage(
    val id: String,
    val role: String,      // "user" | "assistant"
    val content: String = "",
    val isStreaming: Boolean = false,
    val thinkingText: String? = null,     // collapsible reasoning block
    val thinkingExpanded: Boolean = true,  // starts expanded
    val toolCalls: List<UiToolCall> = emptyList(),
    val usage: UiUsage? = null,
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
)

// ── ViewModel ──

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var sessionTitle: String = ""
    private var activeEventSource: EventSource? = null
    private val tempIdCounter = AtomicInteger(0)

    // ── Public API ──

    /** Initialize with a session. Call once from the composable. */
    fun init(sessionId: String, title: String?) {
        if (this.sessionId == sessionId) return
        this.sessionId = sessionId
        this.sessionTitle = title ?: sessionId.take(16)
        _uiState.value = ChatUiState(sessionTitle = this.sessionTitle)
        loadMessages()
    }

    fun loadMessages() {
        if (sessionId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = ApiClient.sessionMessages(sessionId)) {
                is NetworkResult.Success -> {
                    val messages = result.data.data.map { it.toUiMessage() }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messages = messages,
                        error = null,
                    )
                }
                is NetworkResult.HttpError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Server error (${result.code})",
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exception.message ?: "Failed to load messages",
                    )
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || sessionId.isEmpty()) return

        val userMsgId = "user_${tempIdCounter.incrementAndGet()}"
        val assistantMsgId = "asst_${tempIdCounter.incrementAndGet()}"

        // Add user message immediately
        val userMsg = UiMessage(id = userMsgId, role = "user", content = text)
        val current = _uiState.value.messages.toMutableList()
        current.add(userMsg)

        // Add empty streaming assistant message placeholder
        val asstMsg = UiMessage(id = assistantMsgId, role = "assistant", isStreaming = true)
        current.add(asstMsg)

        _uiState.value = _uiState.value.copy(
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
                    val event = SseParser.feed("event: ${type ?: ""}") ?: return
                    val event2 = SseParser.feed("data: $data")
                    val parsed = SseParser.feed("")
                    handleSseEvent(parsed ?: event)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    Log.e("Hermex", "SSE stream failure", t)
                    val code = response?.code
                    val errMsg = when {
                        t != null -> t.message ?: "Stream connection lost"
                        code != null -> "Server error ($code)"
                        else -> "Stream connection lost"
                    }
                    // Finalize the in-flight assistant message
                    val msgs = _uiState.value.messages.toMutableList()
                    val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(isStreaming = false)
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = msgs,
                        isStreaming = false,
                        error = errMsg,
                    )
                }

                override fun onClosed(eventSource: EventSource) {
                    Log.d("Hermex", "SSE stream closed")
                    val msgs = _uiState.value.messages.toMutableList()
                    val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(isStreaming = false)
                    }
                    _uiState.value = _uiState.value.copy(messages = msgs, isStreaming = false)
                }
            },
        )
    }

    fun stopStreaming() {
        activeEventSource?.cancel()
        activeEventSource = null
        _uiState.value = _uiState.value.copy(isStreaming = false)
    }

    fun toggleThinking(messageId: String) {
        val msgs = _uiState.value.messages.toMutableList()
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            msgs[idx] = msgs[idx].copy(thinkingExpanded = !msgs[idx].thinkingExpanded)
            _uiState.value = _uiState.value.copy(messages = msgs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }

    // ── SSE event handler ──

    private fun handleSseEvent(event: SseEvent) {
        val msgs = _uiState.value.messages.toMutableList()

        when (event) {
            is SseEvent.RunStarted -> { /* no UI change needed */ }

            is SseEvent.MessageStarted -> {
                // Update the in-flight assistant message ID to the server-assigned one
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
                        msgs[idx] = cur.copy(
                            id = if (cur.id.startsWith("asst_")) msgId else cur.id,
                            content = cur.content + (event.delta ?: ""),
                        )
                    }
                }
            }

            is SseEvent.ToolProgress -> {
                if (event.toolName == "_thinking") {
                    // Reasoning — append to the thinking block, NOT a tool card
                    val idx = msgs.indexOfLast { it.role == "assistant" }
                    if (idx >= 0) {
                        val cur = msgs[idx]
                        val existing = cur.thinkingText ?: ""
                        msgs[idx] = cur.copy(thinkingText = existing + (event.delta ?: ""))
                    }
                }
                // Non-_thinking tool.progress is a general progress event — ignore for now
            }

            is SseEvent.ToolStarted -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val existing = cur.toolCalls.toMutableList()
                    // Don't add "_thinking" as a tool card — it's handled by ToolProgress
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
                        usage = usage,
                    )
                }
            }

            is SseEvent.RunCompleted -> {
                _uiState.value = _uiState.value.copy(isStreaming = false)
            }

            is SseEvent.Done -> {
                _uiState.value = _uiState.value.copy(isStreaming = false)
            }

            is SseEvent.Unknown -> {
                Log.d("Hermex", "Unknown SSE event: ${event.eventType}")
            }
        }

        _uiState.value = _uiState.value.copy(messages = msgs)
    }

    // ── Mapping ──

    private fun ChatMessage.toUiMessage(): UiMessage = UiMessage(
        id = id.toString(),
        role = role,
        content = content,
        toolCalls = toolCalls?.map {
            UiToolCall(id = it.id ?: "tc", toolName = it.function?.name ?: "unknown", completed = true)
        } ?: emptyList(),
    )
}
