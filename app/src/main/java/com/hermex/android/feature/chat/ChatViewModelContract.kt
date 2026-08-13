package com.hermex.android.feature.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Shared contract for chat ViewModels — both the legacy SSE [ChatViewModel]
 * and the new dashboard [DashboardChatViewModel] implement this.
 * Allows [ChatScreen] to work with either backend without changes.
 */
abstract class ChatViewModelContract(application: Application) : AndroidViewModel(application) {
    abstract var uiState: ChatUiState
    abstract fun init(sessionId: String, title: String?)
    abstract fun loadMessages()
    abstract fun sendMessage(text: String)
    abstract fun stopStreaming()
    /** Retry the last assistant response — re-send the previous user prompt. */
    abstract fun retry()
    abstract fun toggleThinking(messageId: String)
    /** Approve the current pending tool approval request. */
    open fun approveCurrentTool(approveAll: Boolean = false) {}
    /** Deny the current pending tool approval request. */
    open fun denyCurrentTool(denyAll: Boolean = false) {}
    /** Respond to the current pending clarify request. */
    open fun respondToClarify(answer: String) {}
    /** Toggle the Tasks card (agent todo list) expanded state. */
    open fun toggleTodosExpanded() {}
    /** Send a message with an attached image (base64, pre-downscaled). */
    open fun sendMessageWithImage(text: String, imageBase64: String, filename: String?) {}
}
