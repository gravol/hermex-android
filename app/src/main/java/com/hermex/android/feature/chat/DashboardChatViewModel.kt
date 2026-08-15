package com.hermex.android.feature.chat

import android.app.Application
import android.util.Log
import com.hermex.android.AppState
import com.hermex.android.service.WsKeepaliveService
import com.hermex.android.notify.CronWatcher
import com.hermex.android.notify.NotificationHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.hermex.core.network.DebugLog
import com.hermex.core.network.JsonRpcClient
import com.hermex.core.network.JsonRpcException
import com.hermex.core.network.RpcNotification
import com.hermex.core.network.WsConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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

                // v0.1.73: history stores tool activity as SEPARATE role="tool"
                // rows ({name, context}) and assistant thinking in `reasoning`.
                // Merge tool rows into the preceding assistant message's tool
                // box instead of rendering them as jumbled standalone bubbles;
                // carry reasoning into the thinking box.
                val messages = result.messages?.let { raw ->
                    val out = mutableListOf<UiMessage>()
                    for (it in raw) {
                        val role = it.role ?: "user"
                        if (role == "tool") {
                            val prev = out.lastOrNull()
                            if (prev != null && prev.role == "assistant") {
                                val idx = out.size - 1
                                out[idx] = prev.copy(
                                    toolCalls = prev.toolCalls + UiToolCall(
                                        id = it.id ?: "tc_${tempIdCounter.incrementAndGet()}",
                                        toolName = it.name ?: "tool",
                                        preview = it.context,
                                        completed = true,
                                    )
                                )
                            }
                            continue
                        }
                        val thinking = it.resolvedThinking
                        out.add(
                            UiMessage(
                                id = it.id ?: "msg_${tempIdCounter.incrementAndGet()}",
                                role = role,
                                content = it.resolvedContent ?: "",
                                thinkingExpanded = false,
                                thinkingText = thinking,
                                thinkingHasContent = !thinking.isNullOrBlank(),
                                toolCalls = it.tool_calls?.map { tc ->
                                    UiToolCall(
                                        id = tc.id ?: "tc",
                                        toolName = tc.function?.name ?: "unknown",
                                        args = tc.function?.arguments,
                                        completed = true,
                                    )
                                } ?: emptyList(),
                                timestamp = System.currentTimeMillis(),
                            )
                        )
                    }
                    out
                } ?: emptyList()
                val context = parseContextUsage(result.info)
                // Replay the task list: the last todo tool result in history is
                // the authoritative state (same source the desktop Tasks panel uses).
                val todos = result.messages
                    ?.filter { it.role == "tool" && it.name == "todo" }
                    ?.lastOrNull()
                    ?.let { parseTodosFromString(it.context) }
                uiState = uiState.copy(
                    isLoading = false,
                    isStreaming = false,
                    messages = messages,
                    error = null,
                    contextUsed = context.first,
                    contextMax = context.second,
                    todos = todos.orEmpty(),
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

    /**
     * Submit a prompt, self-healing a reaped/stale session.
     *
     * The dashboard reaps live sessions whose WebSocket went orphaned
     * (ws_orphan_reap / idle_timeout / lru_evict) and the client gets no
     * signal — the next prompt.submit then fails with JSON-RPC 4001
     * "session not found". On 4001 we re-register via session.resume
     * (which re-materializes the session from the DB) and retry once.
     * Any other error, or a second failure, propagates to the caller.
     */
    private suspend fun submitWithSelfHeal(text: String) {
        try {
            rpcClient.promptSubmit(sessionId, text)
        } catch (e: JsonRpcException) {
            if (e.code != 4001) throw e
            DebugLog.log("STATE", "SessionID",
                "prompt.submit 4001 (session reaped) — re-registering via session.resume: dbKey=$sessionId")
            val result = rpcClient.sessionResume(sessionId)
            resumeCount++
            liveSid = result.session_id                    // debug only — never write into sessionId
            resumedSessionId = result.resumed ?: sessionId
            DebugLog.log("STATE", "SessionID",
                "self-heal resume(#$resumeCount): dbKey=$sessionId liveSid=$liveSid — retrying submit")
            rpcClient.promptSubmit(sessionId, text)
        }
    }

    override fun sendMessage(text: String) {
        if (text.isBlank() || sessionId.isEmpty()) return

        // Slash commands (v0.1.67): messages starting with "/" route to
        // slash.exec (same path as desktop/TUI) instead of the agent. The
        // output lands as an assistant message.
        if (text.startsWith("/") && text.length > 1 && !text.startsWith("//")) {
            sendSlashCommand(text)
            return
        }

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
                submitWithSelfHeal(text)
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

    /** Execute a slash command via slash.exec and surface its output. */
    private fun sendSlashCommand(command: String) {
        val userMsgId = "user_${tempIdCounter.incrementAndGet()}"
        val asstMsgId = "asst_${tempIdCounter.incrementAndGet()}"
        val now = System.currentTimeMillis()
        val userMsg = UiMessage(id = userMsgId, role = "user", content = command, timestamp = now)
        // Placeholder with spinner: slash commands (e.g. /compress) can take
        // minutes with no stream events — the user needs to see it working.
        val asstMsg = UiMessage(
            id = asstMsgId, role = "assistant",
            isStreaming = true, isWaitingForFirstEvent = true, timestamp = now,
        )
        val current = uiState.messages.toMutableList()
        current.add(userMsg)
        current.add(asstMsg)
        uiState = uiState.copy(messages = current, error = null)
        viewModelScope.launch {
            try {
                val result = rpcClient.slashExec(sessionId, command)
                val output = result["output"]?.jsonPrimitive?.contentOrNull
                    ?: result.toString()
                val msgs = uiState.messages.toMutableList()
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    msgs[idx] = msgs[idx].copy(content = output, isStreaming = false)
                } else {
                    msgs.add(
                        UiMessage(
                            id = "slash_${tempIdCounter.incrementAndGet()}",
                            role = "assistant",
                            content = output,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                }
                uiState = uiState.copy(messages = msgs)
                // Slash commands can change context (e.g. /compress) — refresh gauge
                try {
                    val res = rpcClient.sessionResume(sessionId, omitMessages = true)
                    val ctx = parseContextUsage(res.info)
                    if (ctx.first != null || ctx.second != null) {
                        uiState = uiState.copy(
                            contextUsed = ctx.first ?: uiState.contextUsed,
                            contextMax = ctx.second ?: uiState.contextMax,
                        )
                    }
                } catch (_: Exception) { }
            } catch (e: Exception) {
                Log.e("Hermex", "slash.exec failed", e)
                val msgs = uiState.messages.toMutableList()
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                val errText = "⚠️ Command failed: ${e.message}"
                if (idx >= 0) {
                    msgs[idx] = msgs[idx].copy(content = errText, isStreaming = false)
                } else {
                    msgs.add(
                        UiMessage(
                            id = "slash_err_${tempIdCounter.incrementAndGet()}",
                            role = "assistant",
                            content = errText,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                }
                uiState = uiState.copy(messages = msgs)
            }
        }
    }

    override fun sendMessageWithImage(text: String, imageBase64: String, filename: String?) {
        if (sessionId.isEmpty()) return
        if (imageBase64.isBlank()) {
            if (text.isNotBlank()) sendMessage(text)
            return
        }
        viewModelScope.launch {
            try {
                DebugLog.log("RPC", "DashboardChat", "image.attach_bytes → staging (${imageBase64.length} chars)")
                val attach = rpcClient.attachImage(sessionId, imageBase64, filename)
                val attachText = attach["text"]?.jsonPrimitive?.contentOrNull
                // Image rides along with the next prompt; blank text uses the
                // server's placeholder ("[User attached image: ...]").
                val finalText = text.ifBlank { attachText ?: "[User attached image]" }
                DebugLog.log("RPC", "DashboardChat", "image staged — submitting prompt")
                sendMessage(finalText)
            } catch (e: Exception) {
                Log.e("Hermex", "DashboardChatViewModel: attachImage failed", e)
                DebugLog.log("ERROR", "DashboardChat", "image.attach failed: ${e.message}")
                uiState = uiState.copy(
                    error = e.message ?: "Image attach failed",
                    isStreaming = false,
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

            // Clear per-message streaming flag so the blinking cursor / thinking
            // ticker / typing dots disappear immediately (same pattern as the
            // error handler in sendMessage).
            val msgs = uiState.messages.toMutableList()
            val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
            if (idx >= 0) {
                msgs[idx] = msgs[idx].copy(isStreaming = false)
            }

            uiState = uiState.copy(
                messages = msgs,
                isStreaming = false,
                pendingApproval = null,   // dismiss any visible approval dialog
                pendingClarify = null,    // dismiss any visible clarify dialog
            )
        }
    }

    /** Retry the last assistant response. Finds the previous user prompt,
     * removes the last assistant message, and re-submits the prompt. */
    override fun retry() {
        if (sessionId.isEmpty()) return

        val msgs = uiState.messages.toMutableList()
        val userIdx = msgs.indexOfLast { it.role == "user" }
        if (userIdx < 0) return
        val lastUserText = msgs[userIdx].content
        if (lastUserText.isBlank()) return

        // Remove the last assistant message (the one we're regenerating)
        val asstIdx = msgs.indexOfLast { it.role == "assistant" }
        if (asstIdx >= 0) {
            msgs.removeAt(asstIdx)
        }

        // Add streaming assistant placeholder
        val assistantMsgId = "asst_${tempIdCounter.incrementAndGet()}"
        val now = System.currentTimeMillis()
        msgs.add(UiMessage(
            id = assistantMsgId, role = "assistant",
            isStreaming = true, isWaitingForFirstEvent = true, timestamp = now,
        ))

        uiState = uiState.copy(
            messages = msgs,
            isStreaming = true,
            error = null,
            pendingApproval = null,
            pendingClarify = null,
        )

        viewModelScope.launch {
            try {
                DebugLog.log("RPC", "DashboardChat",
                    "retry → dbKey=$sessionId liveSid=$liveSid text=\"${lastUserText.take(50)}\"")
                submitWithSelfHeal(lastUserText)
            } catch (e: Exception) {
                val m = uiState.messages.toMutableList()
                val idx = m.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    m[idx] = m[idx].copy(isStreaming = false)
                }
                uiState = uiState.copy(
                    messages = m,
                    isStreaming = false,
                    error = e.message ?: "Retry failed",
                )
            }
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

    /** Respond to the current pending clarify request. */
    override fun respondToClarify(answer: String) {
        val pending = uiState.pendingClarify ?: return
        DebugLog.log("RPC", "DashboardChat",
            "responding to clarify: requestId=${pending.requestId} answer=$answer")
        rpcClient.clarifyRespond(pending.requestId, answer)
        uiState = uiState.copy(pendingClarify = null)
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
        dispose()
    }

    /**
     * Tear down this chat ViewModel's live connection. Called by [ChatVmsHolder]
     * when the Activity finishes (chat VMs now outlive navigation so turns keep
     * running in the background). Keepalive service is NOT stopped here — the
     * holder stops it once every session's VM is disposed.
     */
    fun dispose() {
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

                // Start foreground service to keep the process alive
                // (prevents WS disconnect when phone locks or app backgrounds)
                WsKeepaliveService.start(getApplication())

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
                                    val result = rpcClient.sessionResume(sessionId, omitMessages = true)
                                    resumeCount++
                                    liveSid = result.session_id
                                    resumedSessionId = result.resumed ?: sessionId
                                    DebugLog.log("STATE", "SessionID",
                                        "reconnect resume(#${resumeCount}): " +
                                        "dbKey=$sessionId liveSid=$liveSid " +
                                        "resumed=$resumedSessionId")
                                    // Refresh the context gauge on reconnect too
                                    val ctx = parseContextUsage(result.info)
                                    if (ctx.first != null || ctx.second != null) {
                                        uiState = uiState.copy(
                                            contextUsed = ctx.first ?: uiState.contextUsed,
                                            contextMax = ctx.second ?: uiState.contextMax,
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("Hermex", "DashboardChat: reconnect resume failed", e)
                                    DebugLog.log("ERROR", "DashboardChat",
                                        "reconnect resume failed: ${e.message}")
                                    // v0.1.85 (half-open fix): a failed lightweight
                                    // re-attach leaves the WS connected but the session
                                    // unattached — sends go into the void until the 30s
                                    // timeout ("jpc error"). Retry with the FULL
                                    // re-attach (resume + history reload), which is the
                                    // proven path and handles 4001 internally.
                                    loadMessages()
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
                // v0.1.77: re-arm the cron alarm watcher whenever the app
                // connects (catches schedule changes + missed runs)
                CronWatcher.sync(getApplication())
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

            is RpcNotification.ToolGenerating -> {
                DebugLog.log("RPC", "ToolEvent", "generating: ${n.toolName}")
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val existing = cur.toolCalls.toMutableList()
                    // _thinking and todo are rendered as dedicated UI (thinking
                    // block / Tasks card), not as tool cards.
                    if (n.toolName != "_thinking" && n.toolName != "todo") {
                        val tc = UiToolCall(
                            id = "tc_${existing.size}",
                            toolName = n.toolName,
                        )
                        existing.add(tc)
                    }
                    msgs[idx] = cur.copy(
                        toolCalls = existing,
                        isWaitingForFirstEvent = false,
                    )
                }
            }

            is RpcNotification.ToolStart -> {
                DebugLog.log("RPC", "ToolEvent", "start: toolId=${n.toolId} name=${n.toolName} context=${n.context ?: ""}")
                if (n.toolName == "todo") return
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val updated = cur.toolCalls.map { tc ->
                        // Match by id, or the FIRST not-yet-started card with this
                        // name (a tool that runs twice must not overwrite the
                        // previous run's id — that created duplicate ids → crash).
                        if (tc.id == n.toolId || (tc.toolName == n.toolName && tc.startedAt == null)) tc.copy(
                            id = n.toolId,
                            preview = n.context,
                            startedAt = System.currentTimeMillis(),
                        ) else tc
                    }
                    msgs[idx] = cur.copy(toolCalls = updated)
                }
            }

            is RpcNotification.ToolComplete -> {
                DebugLog.log("RPC", "ToolEvent", "complete: toolId=${n.toolId} name=${n.toolName} summary=${n.summary ?: ""} args=${n.args?.toString()?.take(100) ?: ""}")
                // todo tool.complete is the source of truth for the task list —
                // update the Tasks card, not a tool card.
                if (n.toolName == "todo") {
                    val todos = parseTodos(n.result)
                    if (todos != null) {
                        uiState = uiState.copy(
                            todos = todos,
                            // Auto-expand the first time a task list appears during
                            // a turn; once the user collapses it, respect that.
                            todosExpanded = uiState.todosExpanded ||
                                (uiState.todos.isEmpty() && uiState.isStreaming),
                        )
                    }
                    return
                }
                val idx = msgs.indexOfLast { it.role == "assistant" && it.isStreaming }
                if (idx >= 0) {
                    val cur = msgs[idx]
                    val updated = cur.toolCalls.map { tc ->
                        // Match by id, or the first not-yet-completed card with
                        // this name (repeated tool runs must update THEIR card).
                        if (tc.id == n.toolId || (tc.toolName == n.toolName && !tc.completed)) tc.copy(
                            completed = true,
                            args = n.args?.toString() ?: tc.args,
                            preview = n.summary ?: tc.preview,
                            result = n.result?.toString(),
                            summary = n.summary,
                            inlineDiff = n.inlineDiff ?: tc.inlineDiff,
                        ) else tc
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
                    // Keep live-accumulated tool calls (preserve preview/args from
                    // ToolGenerating/ToolStart/ToolComplete). Only copy server IDs
                    // from message.tool_calls so tool cards don't lose their context
                    // when MessageCompleted replaces the list wholesale.
                    val serverTools = n.toolCalls.orEmpty()
                    val finalTools = cur.toolCalls.map { liveTc ->
                        val serverMatch = serverTools.firstOrNull {
                            it.function?.name == liveTc.toolName
                        }
                        liveTc.copy(
                            id = serverMatch?.id ?: liveTc.id,
                            completed = true,
                        )
                    }
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
                    // v0.1.74/75: turn-finished notification — fire when the
                    // user is NOT watching this chat: navigated away (screen
                    // not visible) OR the app is backgrounded.
                    if (!screenVisible || com.hermex.android.AppState.isBackgrounded) {
                        val title = uiState.sessionTitle.ifBlank { sessionId }
                        NotificationHelper.postTurnFinished(
                            getApplication(), sessionId, title, finalContent,
                        )
                    }
                }
                uiState = uiState.copy(
                    messages = msgs,
                    isStreaming = false,
                    scrollGeneration = uiState.scrollGeneration + 1,
                )
                onTurnFinished()
                return  // already set uiState
            }

            is RpcNotification.RunCompleted -> {
                onTurnFinished()
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
                // v0.1.84: ping when the user isn't watching this chat —
                // otherwise approval requests sit unseen until timeout.
                if (!screenVisible || AppState.isBackgrounded) {
                    NotificationHelper.postApproval(
                        getApplication(),
                        sessionId,
                        n.toolName ?: "unknown",
                        argsStr.take(200),
                    )
                }
            }

            is RpcNotification.ClarifyRequest -> {
                DebugLog.log("RPC", "DashboardChat", "clarify request received: requestId=${n.requestId} question=${n.question ?: ""}")
                uiState = uiState.copy(
                    pendingClarify = PendingClarify(
                        requestId = n.requestId,
                        question = n.question ?: "",
                    )
                )
            }

            is RpcNotification.SessionInfo -> {
                DebugLog.log("RPC", "DashboardChat", "session.info received")
                val context = parseContextUsage(n.info)
                if (context.first != null || context.second != null) {
                    uiState = uiState.copy(
                        contextUsed = context.first ?: uiState.contextUsed,
                        contextMax = context.second ?: uiState.contextMax,
                    )
                }
            }

            is RpcNotification.Unknown -> {
                Log.w("Hermex", "DashboardChat: unknown event: ${n.eventType}")
                DebugLog.log("RPC", "DashboardChat", "unknown event: ${n.eventType} — rawParams=${n.rawParams?.toString()?.take(200)}")
                // v0.1.74: the gateway broadcasts cron.changed whenever the cron
                // list changes (any surface: phone, desktop, Telegram). Forward
                // it to the alarm watcher so schedules re-arm without polling.
                if (n.eventType == "cron.changed") {
                    CronWatcher.sync(getApplication())
                }
            }
        }

        // Emit updated state with scrollGeneration bump for auto-scroll
        uiState = uiState.copy(
            messages = msgs,
            scrollGeneration = uiState.scrollGeneration + 1,
        )
    }

    override fun toggleTodosExpanded() {
        uiState = uiState.copy(todosExpanded = !uiState.todosExpanded)
    }

    override suspend fun completeSlash(text: String): List<JsonRpcClient.SlashItem> =
        rpcClient.completeSlash(text)

    /** Whether the chat screen is currently visible (background-turn tracking). */
    private var screenVisible = true

    override fun setScreenVisible(visible: Boolean) {
        screenVisible = visible
        // If the turn finished while away, the banner shows on re-entry
        if (visible && uiState.completedWhileAway) {
            DebugLog.log("RPC", "DashboardChat", "re-entered chat — completedWhileAway banner shown")
        }
        // Re-fetch live context on every chat open (v0.1.63): the one-time
        // resume snapshot can miss real context after a server-side agent
        // rebuild (e.g. auto-compression), and per-turn session.info events
        // aren't guaranteed to reach this client (single-owner transport).
        // A lightweight resume (messages omitted) gets us usage fresh.
        // v0.1.69: keep retrying (burst then 5s poll) until the gauge has
        // real data — the server always has it for a live session; a null
        // reading is just a transient rebuild/compression window.
        if (visible && sessionId.isNotEmpty()) {
            viewModelScope.launch {
                var attempts = 0
                // v0.1.87: poll CONTINUOUSLY while the chat is open — burst
                // (2s) until first data, then every 30s to keep the gauge live.
                // session.info events aren't guaranteed to reach this client
                // (single-owner transport — the desktop usually owns the live
                // stream), so without this the gauge freezes at the last
                // reading or "—/—" forever. One lightweight resume per 30s is
                // trivial while the screen is open.
                while (screenVisible) {
                    attempts++
                    try {
                        val result = rpcClient.sessionResume(sessionId, omitMessages = true)
                        val ctx = parseContextUsage(result.info)
                        if (ctx.first != null || ctx.second != null) {
                            uiState = uiState.copy(
                                contextUsed = ctx.first ?: uiState.contextUsed,
                                contextMax = ctx.second ?: uiState.contextMax,
                            )
                        }
                    } catch (_: Exception) {
                        // Best-effort — loop back and try again.
                    }
                    delay(if (attempts <= 3) 2_000 else 30_000)
                }
            }
        }
    }

    override fun clearCompletedWhileAway() {
        if (uiState.completedWhileAway) {
            uiState = uiState.copy(completedWhileAway = false)
        }
    }

    /**
     * Called when a turn finishes (message.completed / run.completed). If the
     * chat screen wasn't visible at that moment, flag it for the re-entry banner.
     */
    private fun onTurnFinished() {
        if (!screenVisible && uiState.isStreaming) {
            DebugLog.log("RPC", "DashboardChat", "turn finished while screen away — flagging banner")
            uiState = uiState.copy(completedWhileAway = true)
        }
    }

    companion object {
        /**
         * Extract live context-window occupancy from a session.info JSON payload.
         * The server's session.info carries `usage: {context_used, context_max, ...}`
         * (current window occupancy, NOT cumulative lifetime tokens). Returns
         * (used, max); either side may be null when the server hasn't reported it.
         */
        private fun parseContextUsage(info: JsonObject?): Pair<Long?, Long?> {
            if (info == null) return null to null
            return try {
                val usage = info["usage"]?.jsonObject ?: return null to null
                val used = usage["context_used"]?.jsonPrimitive?.longOrNull
                val max = usage["context_max"]?.jsonPrimitive?.longOrNull
                used to max
            } catch (_: Exception) {
                null to null
            }
        }

        /** Parse `{"todos": [{id, content, status}, ...]}` from a tool.complete result. */
        private fun parseTodos(element: JsonElement?): List<UiTodo>? {
            if (element == null) return null
            return try {
                val arr = element.jsonObject["todos"]?.jsonArray ?: return null
                val todos = arr.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    UiTodo(
                        id = id,
                        content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "pending",
                    )
                }
                todos.ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }

        /** Parse todos from a raw JSON string (tool message `context` in history). */
        private fun parseTodosFromString(raw: String?): List<UiTodo>? {
            if (raw.isNullOrBlank()) return null
            return try {
                parseTodos(Json.parseToJsonElement(raw))
            } catch (_: Exception) {
                null
            }
        }
    }
}
