package com.hermex.android.feature.sessions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.core.network.ApiClient
import com.hermex.core.network.NetworkResult
import com.hermex.core.network.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                when (val result = ApiClient.sessions()) {
                    is NetworkResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            sessions = result.data.data,
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
                            error = result.exception.message ?: "Failed to load sessions",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Hermex", "loadSessions crashed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unexpected error",
                )
            }
        }
    }
}
