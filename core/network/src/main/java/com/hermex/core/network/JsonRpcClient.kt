package com.hermex.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * JSON-RPC 2.0 client over WebSocket.
 *
 * Request/response correlation: every outgoing request gets a unique id.
 * Incoming frames with a matching "id" complete the pending CompletableDeferred.
 * Frames with "method" but no "id" are server-pushed notifications — routed
 * via [notifications] Flow.
 *
 * Usage:
 *   val client = JsonRpcClient(connectionManager)
 *   client.start()  // begins consuming frames from WS
 *
 *   val sessions: SessionListResult = client.request("session.list")
 *   client.request("prompt.submit", mapOf("session_id" to sid, "text" to "hello"))
 *
 *   client.notifications.collect { event -> ... }
 */
class JsonRpcClient(
    @PublishedApi internal val connection: WsConnectionManager,
    @PublishedApi internal val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @PublishedApi
    internal val requestCounter = AtomicLong(1)
    @PublishedApi
    internal val pendingRequests = ConcurrentHashMap<Long, kotlinx.coroutines.CancellableContinuation<JsonElement>>()

    // Separate channel for notifications to avoid backpressure on the WS message flow
    private val notificationChannel = Channel<RpcNotification>(UNLIMITED)
    /** Server-pushed events (message.delta, tool.started, approval.request, etc.). */
    val notifications: Flow<RpcNotification> = notificationChannel.receiveAsFlow()

    private var consumerJob: Job? = null

    // ── Lifecycle ──

    /** Start consuming WS frames. Must be called after [connection.connect()]. */
    fun start() {
        if (consumerJob?.isActive == true) return
        Log.d("Hermex", "JsonRpcClient.start()")
        DebugLog.log("RPC", "Client", "start — consuming WS frames")
        consumerJob = scope.launch {
            connection.messages.collect { rawFrame ->
                processFrame(rawFrame)
            }
        }
    }

    /** Stop consuming. Pending requests are NOT cancelled — they'll timeout naturally. */
    fun stop() {
        consumerJob?.cancel()
        consumerJob = null
        Log.d("Hermex", "JsonRpcClient.stop()")
    }

    // ── Request/Response ──

    /**
     * Send a JSON-RPC request and await the response.
     *
     * @param method  e.g. "session.list", "prompt.submit"
     * @param params  Named parameters (nulls filtered out)
     * @return  Deserialized result
     * @throws JsonRpcException  on JSON-RPC error response
     * @throws Exception  on timeout, connection loss, or parse failure
     */
    @PublishedApi
    internal suspend inline fun <reified T> request(
        method: String,
        params: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 30_000,
    ): T {
        val id = requestCounter.getAndIncrement()

        // Build params as JsonObject
        val paramsObj = buildJsonObject {
            params.filterValues { it != null }.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, JsonPrimitive(v))
                    is Number -> put(k, JsonPrimitive(v.toString()))
                    is Boolean -> put(k, JsonPrimitive(v))
                    is JsonElement -> put(k, v)
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }
        val paramsStr = json.encodeToString(JsonObject.serializer(), paramsObj)

        val requestJson = buildString {
            append("{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$paramsStr}")
        }

        DebugLog.log("RPC", "Request", "[$id] $method — params=$paramsStr")
        Log.d("Hermex", "JsonRpcClient.request: [$id] $method — params=$paramsStr")

        return suspendCancellableCoroutine { cont ->
            pendingRequests[id] = cont

            // Send the request
            connection.send(requestJson)

            // Timeout handler
            val timeoutJob = scope.launch {
                kotlinx.coroutines.delay(timeoutMs)
                if (pendingRequests.remove(id) != null) {
                    DebugLog.log("RPC", "Timeout", "[$id] $method — timed out after ${timeoutMs}ms")
                    cont.resumeWithException(
                        JsonRpcException(-1, "Request [$id] $method timed out after ${timeoutMs}ms")
                    )
                }
            }

            cont.invokeOnCancellation {
                pendingRequests.remove(id)
                timeoutJob.cancel()
            }
        }.let { element ->
            json.decodeFromJsonElement<T>(element)
        }
    }

    // ── Notification (no response expected) ──

    /**
     * Send a JSON-RPC notification (no "id" field — no response expected).
     * Used for fire-and-forget calls like approval.respond.
     */
    fun notify(method: String, params: Map<String, Any?> = emptyMap()) {
        val paramsFiltered = params.filterValues { it != null }
        val paramsStr = if (paramsFiltered.isNotEmpty()) {
            paramsFiltered.entries.joinToString(",") { (k, v) ->
                val valStr = when (v) {
                    is String -> "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    is Number -> v.toString()
                    is Boolean -> v.toString()
                    else -> "\"$v\""
                }
                "\"$k\":$valStr"
            }.let { "{$it}" }
        } else {
            "{}"
        }
        val msg = """{"jsonrpc":"2.0","method":"$method","params":$paramsStr}"""
        connection.send(msg)
        DebugLog.log("RPC", "Notify", method)
    }

    // ── Frame processing ──

    private fun processFrame(raw: String) {
        try {
            val frame = json.parseToJsonElement(raw).jsonObject

            val id = frame["id"]?.jsonPrimitive?.content?.toLongOrNull()
            val method = frame["method"]?.jsonPrimitive?.content
            val result = frame["result"]
            val error = frame["error"]
            val params = frame["params"]?.jsonObject

            when {
                // Response to one of our requests
                id != null && result != null -> {
                    handleResponse(id, result)
                }
                // Error response
                id != null && error != null -> {
                    handleError(id, error.jsonObject)
                }
                // Server-pushed notification (no id, has method)
                id == null && method != null && params != null -> {
                    handleNotification(method, params)
                }
                // Server-pushed notification with method at top level
                id == null && method != null -> {
                    handleNotification(method, params ?: JsonObject(emptyMap()))
                }
                else -> {
                    DebugLog.log("RPC", "Unknown", "unrecognized frame shape: ${raw.take(200)}")
                    Log.w("Hermex", "JsonRpcClient: unrecognized frame: ${raw.take(200)}")
                }
            }
        } catch (e: Exception) {
            DebugLog.log("RPC", "ParseError", "failed to parse frame: ${e.message} — raw: ${raw.take(200)}")
            Log.e("Hermex", "JsonRpcClient: parse error", e)
        }
    }

    private fun handleResponse(id: Long, result: JsonElement) {
        val cont = pendingRequests.remove(id)
        if (cont != null) {
            DebugLog.log("RPC", "Response", "[$id] resolved")
            cont.resume(result)
        } else {
            DebugLog.log("RPC", "Response", "[$id] no pending request (stale/timed out)")
        }
    }

    private fun handleError(id: Long, error: JsonObject) {
        val code = error["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
        val message = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
        val cont = pendingRequests.remove(id)
        if (cont != null) {
            DebugLog.log("RPC", "Error", "[$id] code=$code message=$message")
            cont.resumeWithException(JsonRpcException(code, message))
        } else {
            DebugLog.log("RPC", "Error", "[$id] no pending request — $message")
        }
    }

    // ── Notification parser ──

    private fun handleNotification(method: String, params: JsonObject) {
        val eventType = params["type"]?.jsonPrimitive?.content ?: method
        val sessionId = params["session_id"]?.jsonPrimitive?.content
            ?: params["sessionId"]?.jsonPrimitive?.content

        val notification = when (eventType) {
            "gateway.ready" -> RpcNotification.GatewayReady(
                agentId = params["agent_id"]?.jsonPrimitive?.content,
                version = params["version"]?.jsonPrimitive?.content,
                sessionId = sessionId,
            )

            "message.delta" -> {
                val payload = params["payload"]?.jsonObject
                val text = payload?.get("text")?.jsonPrimitive?.content ?: ""
                RpcNotification.MessageDelta(sessionId ?: "", text)
            }

            "message.start", "message.started" -> {
                val payload = params["payload"]?.jsonObject
                RpcNotification.MessageStarted(
                    sessionId = sessionId,
                    messageId = payload?.get("id")?.jsonPrimitive?.content
                        ?: params["message_id"]?.jsonPrimitive?.content,
                )
            }

            "reasoning.available" -> RpcNotification.ReasoningAvailable(sessionId = sessionId)

            "thinking.delta" -> {
                val payload = params["payload"]?.jsonObject
                val text = payload?.get("text")?.jsonPrimitive?.content ?: ""
                RpcNotification.ThinkingDelta(sessionId ?: "", text)
            }

            "reasoning.delta" -> {
                val payload = params["payload"]?.jsonObject
                val text = payload?.get("text")?.jsonPrimitive?.content ?: ""
                RpcNotification.ReasoningDelta(sessionId ?: "", text)
            }

            "tool.started" -> RpcNotification.ToolStarted(
                sessionId = sessionId ?: "",
                toolName = params["tool_name"]?.jsonPrimitive?.content ?: "unknown",
                messageId = params["message_id"]?.jsonPrimitive?.content,
                preview = params["preview"]?.jsonPrimitive?.content,
                args = params["args"],
            )

            "tool.progress" -> RpcNotification.ToolProgress(
                sessionId = sessionId ?: "",
                toolName = params["tool_name"]?.jsonPrimitive?.content ?: "unknown",
                delta = params["delta"]?.jsonPrimitive?.content
                    ?: params["payload"]?.jsonObject?.get("text")?.jsonPrimitive?.content,
            )

            "tool.completed" -> RpcNotification.ToolCompleted(
                sessionId = sessionId ?: "",
                toolName = params["tool_name"]?.jsonPrimitive?.content ?: "unknown",
            )

            "run.started" -> RpcNotification.RunStarted(sessionId ?: "")

            "run.completed" -> RpcNotification.RunCompleted(sessionId ?: "")

            "message.completed", "assistant.completed", "message.complete" -> {
                // Try payload first (Dashboard WS convention), fall back to message (REST SSE convention)
                val msgObj = params["payload"]?.jsonObject ?: params["message"]?.jsonObject
                val toolCalls = msgObj?.get("tool_calls")?.let { tcArray ->
                    json.decodeFromJsonElement<List<RpcNotification.ToolCallInfo>>(tcArray)
                }
                val usage = params["usage"]?.let {
                    json.decodeFromJsonElement<RpcNotification.UsageInfo>(it)
                }
                RpcNotification.MessageCompleted(
                    sessionId = sessionId ?: "",
                    messageId = msgObj?.get("id")?.jsonPrimitive?.content,
                    content = msgObj?.get("content")?.jsonPrimitive?.content,
                    toolCalls = toolCalls,
                    usage = usage,
                )
            }

            "approval.request" -> RpcNotification.ApprovalRequest(
                sessionId = sessionId ?: "",
                sessionKey = params["session_key"]?.jsonPrimitive?.content ?: "",
                toolName = params["tool_name"]?.jsonPrimitive?.content,
                args = params["args"],
            )

            "clarify.request" -> RpcNotification.ClarifyRequest(
                sessionId = sessionId ?: "",
                requestId = params["request_id"]?.jsonPrimitive?.content ?: "",
                question = params["question"]?.jsonPrimitive?.content,
            )

            "session.info" -> RpcNotification.SessionInfo(
                sessionId = sessionId ?: "",
                info = params,
            )

            else -> RpcNotification.Unknown(
                eventType = eventType,
                rawParams = params,
                sessionId = sessionId,
            )
        }

        // Auto-handle clarify in v1: auto-deny
        when (notification) {
            is RpcNotification.ClarifyRequest -> {
                DebugLog.log("RPC", "Clarify", "auto-deny requestId=${notification.requestId}")
                Log.w("Hermex", "JsonRpcClient: auto-denying clarify request ${notification.requestId}")
                notify("clarify.respond", mapOf(
                    "request_id" to notification.requestId,
                    "answer" to "",
                ))
            }
            else -> { /* pass through to notification channel */ }
        }

        notificationChannel.trySend(notification)
    }

    // ── v1 convenience methods ──

    @Serializable
    data class SessionListResult(val sessions: List<SessionInfo>)

    @Serializable
    data class SessionInfo(
        val id: String,
        val title: String? = null,
        val created_at: String? = null,
        val updated_at: String? = null,
        val message_count: Int? = null,
        val model: String? = null,
        val source: String? = null,
        val preview: String? = null,
        val is_active: Boolean? = null,
    )

    @Serializable
    data class SessionResumeResult(
        val session_id: String,
        val resumed: String? = null,       // original DB session key returned by server
        val session_key: String? = null,    // stable lookup key returned by server
        val message_count: Int? = null,
        val messages: List<MessageData>? = null,
        val info: JsonObject? = null,
    )

    @Serializable
    data class MessageData(
        val id: String? = null,
        val role: String? = null,
        val content: String? = null,
        val text: String? = null,  // server sends "text" field, not "content"
        val name: String? = null,  // tool-result messages have "name"
        val context: String? = null,  // tool-result messages have "context"
        val created_at: String? = null,
        val tool_calls: List<RpcNotification.ToolCallInfo>? = null,
    ) {
        /** Resolve message text from any known field. */
        val resolvedContent: String?
            get() = content ?: text ?: context
    }

    @Serializable
    data class PromptSubmitResult(
        val session_id: String? = null,
        val turn_id: String? = null,
        val status: String? = null,
    )

    suspend fun sessionList(): List<SessionInfo> {
        val result: SessionListResult = request("session.list")
        return result.sessions
    }

    suspend fun sessionResume(sessionId: String): SessionResumeResult {
        DebugLog.log("STATE", "SessionID",
            "sessionResume called with sessionId=$sessionId")
        val result: SessionResumeResult =
            request("session.resume", mapOf("session_id" to sessionId))
        DebugLog.log("STATE", "SessionID",
            "sessionResume result: session_id=${result.session_id} " +
            "resumed=${result.resumed} session_key=${result.session_key} " +
            "message_count=${result.message_count}")
        return result
    }

    suspend fun promptSubmit(sessionId: String, text: String): PromptSubmitResult =
        request("prompt.submit", mapOf("session_id" to sessionId, "text" to text))

    suspend fun sessionInterrupt(sessionId: String): JsonObject =
        request("session.interrupt", mapOf("session_id" to sessionId))

    /**
     * Respond to a tool approval request.
     * Server expects session_id (DB key), choice ("approve"|"deny"), and optional all.
     * Uses notify() (fire-and-forget) — the server processes it either way.
     */
    fun approvalRespond(sessionId: String, choice: String, all: Boolean = false) {
        notify("approval.respond", mapOf(
            "session_id" to sessionId,
            "choice" to choice,
            "all" to all.takeIf { it },
        ))
    }

    /**
     * Respond to a clarify request.
     * Server expects request_id and answer.
     * Uses notify() (fire-and-forget).
     */
    fun clarifyRespond(requestId: String, answer: String) {
        notify("clarify.respond", mapOf(
            "request_id" to requestId,
            "answer" to answer,
        ))
    }
}

// ── Helpers ──

/** JSON-RPC error response. */
class JsonRpcException(val code: Int, message: String) : Exception("JSON-RPC error $code: $message")
