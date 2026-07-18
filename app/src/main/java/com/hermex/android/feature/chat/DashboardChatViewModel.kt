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
 *
 * SESSION ID LIFECYCLE RULES (Phase 4L.1):
 * - sessionId (DB key, e.g. "20260717_205748_97c893e8") is persistent and is the
 *   ONLY value allowed for ALL RPC calls: session.resume, prompt.submit,
 *   session.interrupt, session.info.
 * - liveSid (transient live RPC SID, e.g. "0b6c225e") is returned by session.resume
 *   and is for debug logging ONLY. NEVER assign it to sessionId.
 * - resumedSessionId stores the "resumed" field from session.resume for logging.
 */
class DashboardChatViewModel(application: Application) : ChatViewModelContract(application) {

    // Compose snapshot state — identical pattern to ChatViewModel
    override var uiState by mutableStateOf(ChatUiState())

    private var sessionId: String = ""         // stable DB session key — ONLY value for RPC calls
    private var liveSid: String = ""           // transient live RPC sid — DEBUG ONLY, NEVER used in RPC
    private var resumedSessionId: String = ""   // "resumed" field from session.resume response
    private var sessionTitle: String = ""
    private val tempIdCounter = AtomicInteger(0)
    private var resumeCount: Int = 0            // how many times session.resume was called

    // WebSocket + JSON-RPC infrastructure
    private val wsConnection = WsConnectionManager(viewModelScope)
    private val rpcClient = JsonRpcClient(wsConnection, viewModelScope)
    private var notificationCollectorJob: Job? = null

    // ─── Debug: timing & state tracking ───
    private var connectStartTime = 0L
    private var sessionLoadStartTime = 0L

    // ── Public API ──

    /** Initialize with a session. Call once from the composable. */
    override fun init(sessionId: String, title: String?) {
        if (this.sessionId == sessionId) {
            DebugLog.log("STATE", "SessionID",
                "init() — unchanged sessionId=$sessionId, skipped")
            return
        }
        val oldSid = this.sessionId.takeIf { it.isNotEmpty() } ?: "(none)"
        this.sessionId = sessionId           // KEEP as stable DB session key — NEVER overwrite with live sid
        this.liveSid = ""                    // reset until session.resume returns
        this.resumedSessionId = ""           // reset until session.resume returns
        this.resumeCount = 0
        this.sessionTitle = title ?: sessionId.take(16)
        uiState = ChatUiState(sessionTitle = this.sessionTitle)
        DebugLog.log("STATE", "SessionID",
            "init() — sessionId ASSIGNED: old=$oldSid new=$sessionId (DB key) title=$sessionTitle")
        connectWsAndStart()
    }

    override fun loadMessages() {
        if (sessionId.isEmpty()) return
        val callNum = resumeCount + 1
        DebugLog.log("STATE", "SessionID",
            "loadMessages(#$callNum) — entering with sessionId=$sessionId")
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val result = rpcClient.sessionResume(sessionId)
                resumeCount++
                // liveSid is DEBUG ONLY — never write into sessionId
                val newLiveSid = result.session_id
                val newResumed = result.resumed ?: sessionId
                val newSessionKey = result.session_key

                DebugLog.log("STATE", "SessionID",
                    "loadMessages(#${resumeCount}) RESULT: " +
                    "input_sessionId=$sessionId " +
                    "result.session_id=$newLiveSid " +
                    "result.resumed=$newResumed " +
                    "result.session_key=$newSessionKey " +
                    "result.message_count=${result.message_count}")

                liveSid = newLiveSid           // DEBUG ONLY — see Phase 4L.1 rule
                resumedSessionId = newResumed   // for logging/reference

                DebugLog.log("STATE", "SessionID",
                    "loadMessages(#${resumeCount}) ASSIGNMENTS: " +
                    "liveSid=$liveSid (debug only) " +
                    "resumedSessionId=$resumedSessionId " +
                    "sessionId UNCHANGED=$sessionId (DB key preserved)")

                if (newSessionKey != null && newSessionKey != sessionId) {
                    DebugLog.log("STATE", "SessionID",
                        "loadMessages(#${resumeCount}) WARNING: " +
                        "session_key=$newSessionKey differs from dbKey=$sessionId")
                }

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
                    isStreaming = false,
                    messages = messages,
                    error = null,
                )
                val loadDuration = if (sessionLoadStartTime > 0) System.currentTimeMillis() - sessionLoadStartTime else -1L
                DebugLog.log("RPC", "DashboardChat",
                    "session.resume → ${messages.size} messages in ${loadDuration}ms" +
                    " (message_count=${result.message_count})")
                sessionLoadStartTime = 0L
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

        DebugLog.log("STATE", "SessionID",
            "sendMessage ENTER: text=\"${text.take(50)}\" " +
            "sessionId=$sessionId (DB key) liveSid=$liveSid (debug) " +
            "resumedSessionId=$resumedSessionId " +
            "resumeCount=${resumeCount}")

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
                DebugLog.log("STATE", "SessionID",
                    "prompt.submit CALL: sessionId=$sessionId (DB key) " +
                    "liveSid=$liveSid (debug only) " +
                    "resumeCount=$resumeCount")
                DebugLog.log("RPC", "DashboardChat",
                    "prompt.submit → dbKey=$sessionId liveSid=$liveSid text=\"${text.take(50)}\"")
                rpcClient.promptSubmit(sessionId, text)
                DebugLog.log("STATE", "SessionID",
                    "prompt.submit OK: sessionId=$sessionId")
            } catch (e: Exception) {
                Log.e("Hermex", "DashboardChatViewModel: promptSubmit failed", e)
                DebugLog.log("STATE", "SessionID",
                    "prompt.submit FAILED: sessionId=$sessionId error=${e.message}")
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
                DebugLog.log("RPC", "DashboardChat",
                    "session.interrupt → dbKey=$sessionId liveSid=$liveSid")
                rpcClient.sessionInterrupt(sessionId)
            } catch (_: Exception) { /* best-effort */ }
            uiState = uiState.copy(isStreaming = false)
        }
    }

    /** Approve the current pending tool call. */
    override fun approveCurrentTool(approveAll: Boolean) {
        val pending = uiState.pendingApproval ?: return
        DebugLog.log("RPC", "DashboardChat",
            "approving tool: ${pending.toolName} all=$approveAll")
        rpcClient.approvalRespond(sessionId, "approve", approveAll)
        uiState = uiState.copy(pendingApproval = null)
    }

    /** Deny the current pending tool call. */
    override fun denyCurrentTool(denyAll: Boolean) {
        val pending = uiState.pendingApproval ?: return
        DebugLog.log("RPC", "DashboardChat",
            "denying tool: ${pending.toolName} all=$denyAll")
        rpcClient.approvalRespond(sessionId, "deny", denyAll)
        uiState = uiState.copy(pendingApproval = null)
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
        DebugLog.log("STATE", "SessionID",
            "onCleared: sessionId=$sessionId liveSid=$liveSid resumeCount=$resumeCount")
        super.onCleared()
        notificationCollectorJob?.cancel()
        wsConnection.disconnect()
    }

    // ── WebSocket lifecycle ──

    private fun connectWsAndStart() {
        viewModelScope.launch {
            connectStartTime = System.currentTimeMillis()
            try {
                DebugLog.log("WS", "DashboardChat", "connecting WebSocket")
                wsConnection.connect()
                val connectDuration = System.currentTimeMillis() - connectStartTime
                DebugLog.log("WS", "DashboardChat",
                    "WebSocket connected in ${connectDuration}ms — starting RPC " +
                    "(sessionId=$sessionId liveSid=$liveSid)")

                // Monitor WS state transitions
                var previousWsState: Any? = null
                launch {
                    wsConnection.state.collect { wsState ->
                        DebugLog.log("WS", "ChatVM",
                            "state=$wsState (sessionId=$sessionId liveSid=$liveSid)")
                        // Detect reconnect: state changed TO Connected
                        if (wsState.toString().uppercase() == "CONNECTED"
                            && previousWsState != null
                            && previousWsState.toString().uppercase() != "CONNECTED") {
                            DebugLog.log("WS", "DashboardChat",
                                "reconnect detected — re-registering session $sessionId")
                            viewModelScope.launch {
                                try {
                                    val result = rpcClient.sessionResume(sessionId)
                                    resumeCount++
                                    liveSid = result.session_id
                                    resumedSessionId = result.resumed ?: sessionId
                                    DebugLog.log("STATE", "SessionID",
                                        "reconnect resume(#${resumeCount}): " +
                                        "dbKey=$sessionId liveSid=$liveSid " +
                                        "resumed=$resumedSessionId")
                                } catch (e: Exception) {
                                    Log.e("Hermex", "DashboardChat: reconnect resume failed", e)
                                    DebugLog.log("ERROR", "DashboardChat",
                                        "reconnect resume failed: ${e.message}")
                                }
                            }
                        }
                        previousWsState = wsState
                    }
                }

                rpcClient.start()
                DebugLog.log("WS", "DashboardChat",
                    "RPC client started, total=${System.currentTimeMillis() - connectStartTime}ms " +
                    "(sessionId=$sessionId)")

                // Begin collecting notifications
                notificationCollectorJob = launch {
                    rpcClient.notifications.collect { notification ->
                        handleNotification(notification)
                    }
                }

                // Load session history after connection
                sessionLoadStartTime = System.currentTimeMillis()
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
        if (nSid != null && nSid.isNotEmpty() && nSid != sessionId && nSid != liveSid) {
            // Notifications may carry either DB key or live SID — match against both
            DebugLog.log("STATE", "SessionID",
                "notification FILTERED: nSid=$nSid != dbKey=$sessionId != liveSid=$liveSid " +
                "event=${n::class.simpleName}")
            return
        }
        if (nSid != null && nSid.isNotEmpty()) {
            DebugLog.log("STATE", "SessionID",
                "notification MATCHED: nSid=$nSid dbKey=$sessionId liveSid=$liveSid " +
                "event=${n::class.simpleName}")
        }

        val msgs = uiState.messages.toMutableList()

        when (n) {
            is RpcNotification.GatewayReady -> {
                DebugLog.log("RPC", "DashboardChat",
                    "gateway.ready — agent=${n.agentId} v${n.version} " +
                    "sessionId=${n.sessionId}")
                val gwSessionId = n.sessionId
                if (gwSessionId != null && gwSessionId.isNotEmpty()) {
                    DebugLog.log("STATE", "SessionID",
                        "gateway.ready gwSessionId=$gwSessionId " +
                        "(our dbKey=${this.sessionId} liveSid=$liveSid)")
                }
            }

            is RpcNotification.RunStarted -> {
                // No UI change needed — streaming placeholder already visible
            }

            is RpcNotification.MessageStarted -> {
                DebugLog.log("RPC", "DashboardChat",
                    "message.start — serverId=${n.messageId}")
                val serverId = n.messageId
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    msgs[idx] = msgs[idx].copy(
                        id = serverId ?: msgs[idx].id,
                        isWaitingForFirstEvent = false,
                    )
                }
            }

            is RpcNotification.ReasoningAvailable -> {
                val idx = msgs.indexOfLast { it.role == "assistant" }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    msgs[idx] = cur.copy(thinkingHasContent = true)
                }
            }

            is RpcNotification.MessageDelta -> {
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
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
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
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
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
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
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
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
                    val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
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
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
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
                DebugLog.log("RPC", "DashboardChat", "approval request received: ${n.toolName}")
                val argsStr = n.args?.toString() ?: ""
                uiState = uiState.copy(
                    pendingApproval = PendingApproval(
                        toolName = n.toolName ?: "unknown",
                        toolArgs = argsStr,
                    )
                )
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
                DebugLog.log("RPC", "DashboardChat", "unknown event: ${n.eventType} — rawParams=${n.rawParams?.toString()?.take(200)}")
            }
        }

        // Emit updated state with scrollGeneration bump for auto-scroll
        uiState = uiState.copy(
            messages = msgs,
            scrollGeneration = uiState.scrollGeneration + 1,
        )
    }
}
