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

data class OnboardingUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val connectionTested: Boolean = false,
    val authEnabled: Boolean = false,
    val passwordAuthEnabled: Boolean = false,
    val loginSuccess: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateUsername(u: String) {
        _uiState.value = _uiState.value.copy(username = u, error = null)
    }

    fun updatePassword(pw: String) {
        _uiState.value = _uiState.value.copy(password = pw, error = null)
    }

    fun testConnection() {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Server URL is required")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                when (val result = ApiClient.health(url)) {
                    is NetworkResult.Success -> {
                        val health = result.data
                        // Server reachable — if user field is present, already authenticated
                        val isAuth = health.user != null
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            connectionTested = true,
                            authEnabled = !isAuth,
                            passwordAuthEnabled = !isAuth,
                            error = null,
                        )
                    }
                    is NetworkResult.HttpError -> {
                        if (result.code == 401) {
                            // Server alive but requires auth — treat as successful discovery
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                connectionTested = true,
                                authEnabled = true,
                                passwordAuthEnabled = true,
                                error = null,
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Server returned ${result.code}: ${result.message}",
                            )
                        }
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

    fun login() {
        val state = _uiState.value
        val url = state.serverUrl.trim()
        val user = state.username.trim()
        val pw = state.password.trim()
        if (url.isBlank() || user.isBlank() || pw.isBlank()) {
            _uiState.value = state.copy(error = "Server URL, username, and password are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                when (val result = ApiClient.login(url, user, pw)) {
                    is NetworkResult.Success -> {
                        val loginResp = result.data
                        if (loginResp.ok) {
                            KeychainStore.saveCredentials(getApplication(), url, user, pw)
                            ApiClient.setBaseUrl(url)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                loginSuccess = true,
                                error = null,
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Login failed: unauthorized",
                            )
                        }
                    }
                    is NetworkResult.HttpError -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Login failed (${result.code})",
                        )
                    }
                    is NetworkResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exception.message ?: "Login failed",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Hermex", "login crashed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "App error: ${e.message}",
                )
            }
        }
    }
}
