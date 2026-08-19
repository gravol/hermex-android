package com.hermex.android.feature.sessions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.feature.settings.SettingsRepository
import com.hermex.core.network.DebugLog
import com.hermex.core.network.JsonRpcClient
import com.hermex.core.network.SessionSummary
import com.hermex.core.network.WsConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

    /** Locally pinned session ids (desktop-style client-side pinning). */
    val pinnedIds: StateFlow<Set<String>> = settingsRepo.pinnedSessionIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        loadSessions()
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
            startedAt = created_at?.let { parseIsoToEpoch(it) },
            lastActive = updated_at?.let { parseIsoToEpoch(it) },
            messageCount = message_count ?: 0,
            preview = preview,
        )
    }

    companion object {
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Parse ISO-8601 timestamp (e.g. "2026-07-17T12:34:56") to epoch seconds.
         * Returns null on parse failure.
         */
        private fun parseIsoToEpoch(iso: String): Double? {
            return try {
                val date = isoFormat.parse(iso.substringBefore('.').substringBefore('Z'))
                date?.time?.toDouble()?.div(1000.0)
            } catch (_: Exception) {
                null
            }
        }
    }
}
