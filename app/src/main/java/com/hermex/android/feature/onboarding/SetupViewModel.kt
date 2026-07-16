package com.hermex.android.feature.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.core.data.auth.KeychainStore
import com.hermex.core.network.ApiClient
import com.hermex.core.network.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val serverUrl: String = "http://100.80.204.66:8650",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val connectionOk: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, error = null)
    }

    fun testConnection() {
        val state = _uiState.value
        val url = state.serverUrl.trim()
        val key = state.apiKey.trim()
        if (url.isBlank()) {
            _uiState.value = state.copy(error = "Server URL is required")
            return
        }
        if (key.isBlank()) {
            _uiState.value = state.copy(error = "API key is required")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                ApiClient.setBaseUrl(url)
                ApiClient.setApiKey(key)
                when (val result = ApiClient.testConnection()) {
                    is NetworkResult.Success -> {
                        KeychainStore.save(getApplication(), url, key)
                        val s = result.data
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            connectionOk = true,
                            statusMessage = "✓ Connected — ${s.platform ?: "hermes"} ${s.version ?: ""}",
                            error = null,
                        )
                    }
                    is NetworkResult.HttpError -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Server returned ${result.code}",
                        )
                    }
                    is NetworkResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exception.message ?: "Connection failed",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Hermex", "testConnection crashed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "App error: ${e.message}",
                )
            }
        }
    }
}
