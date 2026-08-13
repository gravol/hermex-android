package com.hermex.android.feature.chat

/**
 * Shared UI data models used by both DashboardChatViewModel and ChatScreen.
 * Originally defined in ChatViewModel.kt (legacy SSE ViewModel), extracted during legacy stack cleanup.
 */

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
    val result: String? = null,
    val summary: String? = null,
    val startedAt: Long? = null,
    val completed: Boolean = false,
)

data class UiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double? = null,
)

/** One item in the agent's live task list (todo tool state). */
data class UiTodo(
    val id: String,
    val content: String,
    val status: String = "pending",  // pending | in_progress | completed | cancelled
) {
    val isDone: Boolean get() = status == "completed" || status == "cancelled"
    val isActive: Boolean get() = status == "in_progress"
}

data class ChatUiState(
    val sessionTitle: String = "",
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val scrollGeneration: Long = 0L,  // bumped on every SSE-driven list mutation; triggers auto-scroll
    val pendingApproval: PendingApproval? = null,  // non-null when tool needs approval
    val pendingClarify: PendingClarify? = null,    // non-null when clarification is needed
    // Live context-window occupancy (from session.info usage.context_used/context_max).
    // Null until the server reports a real reading.
    val contextUsed: Long? = null,
    val contextMax: Long? = null,
    // Agent task list (todo tool state, from tool.complete events / history replay).
    // Non-empty = the Tasks card shows above the message list.
    val todos: List<UiTodo> = emptyList(),
    val todosExpanded: Boolean = false,
)

/**
 * A tool call waiting for user approval.
 */
data class PendingApproval(
    val toolName: String,
    val toolArgs: String = "",
    val requestId: String = "",
)

/**
 * A clarification request waiting for user input.
 * @property requestId Server-side ID for the clarification request.
 * @property question The question the server is asking the user.
 */
data class PendingClarify(
    val requestId: String,
    val question: String = "",
)
