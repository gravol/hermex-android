package com.hermex.android.feature.sessions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.feature.settings.SettingsRepository
import com.hermex.core.network.DebugLog
import com.hermex.core.network.JsonRpcClient
import com.hermex.core.network.RpcNotification
import com.hermex.core.network.SessionSummary
import com.hermex.core.network.WsConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val deleting: Boolean = false,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private val settingsRepo = SettingsRepository(application)

    /**
     * Persistent RPC client used to observe `sessions.changed` broadcasts. A
     * dedicated WS connection lives for the ViewModel's lifetime so session-list
     * changes reach us without a full reconnect (see [init]).
     */
    private var observerClient: JsonRpcClient? = null

    /** Locally pinned session ids (desktop-style client-side pinning). */
    val pinnedIds: StateFlow<Set<String>> = settingsRepo.pinnedSessionIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        loadSessions()
        // Establish a persistent observer connection and start watching for
        // `sessions.changed` broadcasts. The dashboard fires this after any
        // external session-list change (cron runs, other clients, turn
        // completion). Without refetching on it, the cached list goes stale —
        // which is exactly why Insights showed "no usage data" even after the
        // gateway started returning token counts.
        viewModelScope.launch {
            try {
                val ws = WsConnectionManager(viewModelScope)
                ws.connect()
                observerClient = JsonRpcClient(ws, viewModelScope).apply { start() }
            } catch (_: Exception) {
                observerClient = null
            }
            observerClient?.let { client ->
                client.notifications.collect { n ->
                    if (n is RpcNotification.SessionChanged) {
                        DebugLog.log("INFO", "SessionsVM", "sessions.changed → reload")
                        loadDashboardSessions()
                    }
                }
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            loadDashboardSessions()
        }
    }

    fun togglePin(sessionId: String) {
        viewModelScope.launch {
            settingsRepo.togglePinned(sessionId)
        }
    }

    /** Create a new session server-side, then hand the live session id to [onDone]. */
    fun createSession(onDone: (String?) -> Unit) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            val wsConnection = WsConnectionManager(viewModelScope)
            try {
                wsConnection.connect()
                val rpcClient = JsonRpcClient(wsConnection, viewModelScope)
                rpcClient.start()
                // v0.1.88: apply the user's model/effort pick (sticky, like the
                // desktop composer) to the new session.
                val modelPick = settingsRepo.modelPick.first().ifBlank { null }
                val reasoningPick = settingsRepo.reasoningPick.first().ifBlank { null }
                val sid = rpcClient.createSession(model = modelPick, reasoningEffort = reasoningPick)
                DebugLog.log("INFO", "SessionsVM",
                    "session.create → $sid (model=$modelPick effort=$reasoningPick)")
                onDone(sid)
            } catch (e: Exception) {
                Log.e("Hermex", "SessionsViewModel: createSession failed", e)
                DebugLog.log("ERROR", "SessionsVM", "session.create failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to create session",
                )
                onDone(null)
            } finally {
                wsConnection.disconnect()
                _uiState.value = _uiState.value.copy(isCreating = false)
            }
        }
    }

    /** Delete a session server-side, then reload the session list. */
    fun deleteSession(sessionId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleting = true, error = null)
            val wsConnection = WsConnectionManager(viewModelScope)
            try {
                wsConnection.connect()
                val rpcClient = JsonRpcClient(wsConnection, viewModelScope)
                rpcClient.start()
                rpcClient.sessionDelete(sessionId)
                DebugLog.log("INFO", "SessionsVM", "session.delete → $sessionId")
                _uiState.value = _uiState.value.copy(deleting = false)
                loadSessions()
                onDone()
            } catch (e: Exception) {
                Log.e("Hermex", "SessionsViewModel: deleteSession failed", e)
                DebugLog.log("ERROR", "SessionsVM", "session.delete failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    deleting = false,
                    error = e.message ?: "Failed to delete session",
                )
                onDone()
            } finally {
                wsConnection.disconnect()
            }
        }
    }

    // ── Dashboard JSON-RPC session.list ──

    private suspend fun loadDashboardSessions() {
        DebugLog.log("INFO", "SessionsVM", "loadSessions via DASHBOARD JsonRpcClient.sessionList()")
        Log.d("Hermex", "SessionsViewModel: loading dashboard sessions")

        val wsConnection = WsConnectionManager(viewModelScope)

        try {
            // Connect WS
            wsConnection.connect()

            val rpcClient = JsonRpcClient(wsConnection, viewModelScope)
            rpcClient.start()

            val rpcSessions = rpcClient.sessionList()
            DebugLog.log("INFO", "SessionsVM", "session.list → ${rpcSessions.size} sessions")

            val mapped = rpcSessions.map { it.toSessionSummary() }
            val filtered = mapped.filter { it.messageCount > 0 }
            val filteredCount = mapped.size - filtered.size
            if (filteredCount > 0) {
                DebugLog.log("INFO", "SessionsVM", "filtered out $filteredCount empty sessions")
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sessions = filtered,
                error = null,
            )
        } catch (e: Exception) {
            Log.e("Hermex", "SessionsViewModel: dashboard session load failed", e)
            DebugLog.log("ERROR", "SessionsVM", "dashboard session.list failed: ${e.message}")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Dashboard: ${e.message ?: "Session load failed"}",
            )
        } finally {
            wsConnection.disconnect()
        }
    }

    // ── Mapping: JsonRpcClient.SessionInfo → SessionSummary ──

    private fun JsonRpcClient.SessionInfo.toSessionSummary(): SessionSummary {
        return SessionSummary(
            id = id,
            title = title,
            source = source,
            model = model,
            // Server sends started_at/ended_at as epoch seconds (Double), not ISO strings.
            startedAt = started_at,
            endedAt = ended_at,
            messageCount = message_count ?: 0,
            preview = preview,
            inputTokens = input_tokens ?: -1,
            outputTokens = output_tokens ?: -1,
        )
    }

    companion object {
    }
}
