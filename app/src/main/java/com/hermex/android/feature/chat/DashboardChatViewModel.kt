package com.hermex.android.feature.chat

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.hermex.core.network.DebugLog
import com.hermex.core.network.JsonRpcClient
import com.hermex.core.network.RpcNotification
import com.hermex.core.network.WsConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dashboard-backed ChatViewModel using JSON-RPC over WebSocket (port 9119).
 *
 * Reuses the same UI models ([UiMessage], [UiToolCall], [UiUsage], [ChatUiState])
 * and the same [mutableStateOf] snapshot-state pattern as the legacy SSE [ChatViewModel].
 * [ChatScreen] requires no changes — data flows underneath the existing Compose UI.
 */
class DashboardChatViewModel(application: Application) : ChatViewModelContract(application) {

    // Compose snapshot state — identical pattern to ChatViewModel
    override var uiState by mutableStateOf(ChatUiState())

    private var sessionId: String = ""
    private var sessionTitle: String = ""
    private val tempIdCounter = AtomicInteger(0)

    // WebSocket + JSON-RPC infrastructure
    private val wsConnection = WsConnectionManager(viewModelScope)
    private val rpcClient = JsonRpcClient(wsConnection, viewModelScope)
    private var notificationCollectorJob: Job? = null

    // ── Public API ──

    /** Initialize with a session. Call once from the composable. */
    override fun init(sessionId: String, title: String?) {
        if (this.sessionId == sessionId) return
        this.sessionId = sessionId
        this.sessionTitle = title ?: sessionId.take(16)
        uiState = ChatUiState(sessionTitle = this.sessionTitle)
        connectWsAndStart()
    }

    override fun loadMessages() {
        if (sessionId.isEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val result = rpcClient.sessionResume(sessionId)
                val messages = result.messages?.map {
                    val messageContent = it.resolvedContent ?: ""
                    UiMessage(
                        id = it.id ?: "msg_${tempIdCounter.incrementAndGet()}",
                        role = it.role ?: "user",
                        content = messageContent,
                        thinkingExpanded = false,
                        thinkingHasContent = true,
                        toolCalls = it.tool_calls?.map { tc ->
                            UiToolCall(
                                id = tc.id ?: "tc",
                                toolName = tc.function?.name ?: "unknown",
                                completed = true,
                            )
                        } ?: emptyList(),
                        timestamp = System.currentTimeMillis(),
                    )
                } ?: emptyList()
                uiState = uiState.copy(
                    isLoading = false,
                    messages = messages,
                    error = null,
                )
                DebugLog.log("RPC", "DashboardChat", "session.resume → ${messages.size} messages")
            } catch (e: Exception) {
                Log.e("Hermex", "DashboardChatViewModel: loadMessages failed", e)
                uiState = uiState.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load messages",
                )
            }
        }
    }

    override fun sendMessage(text: String) {
        if (text.isBlank() || sessionId.isEmpty()) return

        val userMsgId = "user_${tempIdCounter.incrementAndGet()}"
        val assistantMsgId = "asst_${tempIdCounter.incrementAndGet()}"
        val now = System.currentTimeMillis()

        // Add user message immediately
        val userMsg = UiMessage(id = userMsgId, role = "user", content = text, timestamp = now)
        val current = uiState.messages.toMutableList()
        current.add(userMsg)

        // Add empty streaming assistant message placeholder
        val asstMsg = UiMessage(
            id = assistantMsgId, role = "assistant",
            isStreaming = true, isWaitingForFirstEvent = true, timestamp = now,
        )
        current.add(asstMsg)

        uiState = uiState.copy(
            messages = current,
            isStreaming = true,
            error = null,
        )

        viewModelScope.launch {
            try {
                DebugLog.log("RPC", "DashboardChat", "prompt.submit → session=$sessionId")
                rpcClient.promptSubmit(sessionId, text)
            } catch (e: Exception) {
                Log.e("Hermex", "DashboardChatViewModel: promptSubmit failed", e)
                val msgs = uiState.messages.toMutableList()
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    msgs[idx] = msgs[idx].copy(isStreaming = false)
                }
                uiState = uiState.copy(
                    messages = msgs,
                    isStreaming = false,
                    error = e.message ?: "Send failed",
                )
            }
        }
    }

    override fun stopStreaming() {
        viewModelScope.launch {
            try {
                rpcClient.sessionInterrupt(sessionId)
            } catch (_: Exception) { /* best-effort */ }
            uiState = uiState.copy(isStreaming = false)
        }
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
        notificationCollectorJob?.cancel()
        wsConnection.disconnect()
    }

    // ── WebSocket lifecycle ──

    private fun connectWsAndStart() {
        viewModelScope.launch {
            try {
                DebugLog.log("WS", "DashboardChat", "connecting WebSocket")
                wsConnection.connect()
                rpcClient.start()
                DebugLog.log("WS", "DashboardChat", "WebSocket connected, RPC client started")

                // Begin collecting notifications
                notificationCollectorJob = launch {
                    rpcClient.notifications.collect { notification ->
                        handleNotification(notification)
                    }
                }

                // Load session history after connection
                loadMessages()
            } catch (e: Exception) {
                Log.e("Hermex", "DashboardChatViewModel: WebSocket connect failed", e)
                DebugLog.log("ERROR", "DashboardChat", "WS connect failed: ${e.message}")
                uiState = uiState.copy(
                    error = "Connection failed: ${e.message}",
                )
            }
        }
    }

    // ── RPC Notification handler ──

    private fun handleNotification(n: RpcNotification) {
        // Filter: only process events for our session
        val nSid = n.sessionId
        if (nSid != null && nSid.isNotEmpty() && nSid != sessionId) return

        val msgs = uiState.messages.toMutableList()

        when (n) {
            is RpcNotification.GatewayReady -> {
                DebugLog.log("RPC", "DashboardChat", "gateway.ready — agent=${n.agentId} v${n.version}")
            }

            is RpcNotification.RunStarted -> {
                // No UI change needed — streaming placeholder already visible
            }

            is RpcNotification.MessageDelta -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    msgs[idx] = cur.copy(
                        content = cur.content + n.text,
                        thinkingHasContent = true,
                        isWaitingForFirstEvent = false,
                    )
                }
            }

            is RpcNotification.ThinkingDelta -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val existing = cur.thinkingText ?: ""
                    msgs[idx] = cur.copy(
                        thinkingText = existing + n.text,
                        isWaitingForFirstEvent = false,
                    )
                }
            }

            is RpcNotification.ReasoningDelta -> {
                // Treat reasoning same as thinking — append to thinkingText
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val existing = cur.thinkingText ?: ""
                    msgs[idx] = cur.copy(
                        thinkingText = existing + n.text,
                        isWaitingForFirstEvent = false,
                    )
                }
            }

            is RpcNotification.ToolStarted -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val existing = cur.toolCalls.toMutableList()
                    if (n.toolName != "_thinking") {
                        val tc = UiToolCall(
                            id = n.messageId ?: "tc_${existing.size}",
                            toolName = n.toolName,
                            preview = n.preview,
                            args = n.args?.toString(),
                        )
                        existing.add(tc)
                    }
                    msgs[idx] = cur.copy(
                        toolCalls = existing,
                        isWaitingForFirstEvent = false,
                    )
                }
            }

            is RpcNotification.ToolProgress -> {
                if (n.toolName == "_thinking" && n.delta != null) {
                    val idx = msgs.indexOfLast { it.role == "assistant" }
                    if (idx >= 0) {
                        val cur = msgs[idx]
                        val existing = cur.thinkingText ?: ""
                        msgs[idx] = cur.copy(
                            thinkingText = existing + n.delta,
                            isWaitingForFirstEvent = false,
                        )
                    }
                }
            }

            is RpcNotification.ToolCompleted -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val updated = cur.toolCalls.map { tc ->
                        if (tc.toolName == n.toolName) tc.copy(completed = true) else tc
                    }
                    msgs[idx] = cur.copy(toolCalls = updated)
                }
            }

            is RpcNotification.MessageCompleted -> {
                DebugLog.log("RPC", "DashboardChat", "message.completed — finalizing")
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val finalContent = n.content?.takeIf { it.isNotBlank() } ?: cur.content
                    val finalTools = n.toolCalls?.map {
                        UiToolCall(
                            id = it.id ?: "tc",
                            toolName = it.function?.name ?: "unknown",
                            completed = true,
                        )
                    } ?: cur.toolCalls
                    val usage = n.usage?.let {
                        UiUsage(
                            it.promptTokens ?: 0,
                            it.completionTokens ?: 0,
                            it.totalTokens ?: 0,
                            it.estimatedCostUsd,
                        )
                    }
                    msgs[idx] = cur.copy(
                        id = n.messageId ?: cur.id,
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
                return  // already set uiState
            }

            is RpcNotification.RunCompleted -> {
                uiState = uiState.copy(isStreaming = false)
                return
            }

            is RpcNotification.ApprovalRequest -> {
                // Auto-denied by JsonRpcClient — surface as log notice
                DebugLog.log("RPC", "DashboardChat", "approval request auto-denied: ${n.toolName}")
                Log.w("Hermex", "DashboardChat: approval auto-denied for ${n.toolName}")
            }

            is RpcNotification.ClarifyRequest -> {
                DebugLog.log("RPC", "DashboardChat", "clarify request auto-denied: ${n.requestId}")
                Log.w("Hermex", "DashboardChat: clarify auto-denied for ${n.requestId}")
            }

            is RpcNotification.SessionInfo -> {
                DebugLog.log("RPC", "DashboardChat", "session.info received")
            }

            is RpcNotification.Unknown -> {
                Log.w("Hermex", "DashboardChat: unknown event: ${n.eventType}")
                DebugLog.log("RPC", "DashboardChat", "unknown event: ${n.eventType}")
            }
        }

        // Emit updated state with scrollGeneration bump for auto-scroll
        uiState = uiState.copy(
            messages = msgs,
            scrollGeneration = uiState.scrollGeneration + 1,
        )
    }
}
