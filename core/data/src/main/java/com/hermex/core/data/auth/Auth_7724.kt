package com.example.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.content.Context
import android.util.Log

class AuthManager(
    private val context: Context,
    private val serverRegistry: ServerRegistry, // Assume this exists or is mocked
    private val headerStore: CustomHeaderStore = CustomHeaderStore.shared,
    private val cookieJar: CookieJar = NetworkCookieJar()
) {
    companion object {
        private const val HEALTH_PATH = "/health"
        private const val LOGIN_PATH = "/api/auth/login"
        private const val PASSKEY_ONLY_MESSAGE = "This server signs in with passkeys, which App doesn't support yet."
        private const val ENTER_PASSWORD_MESSAGE = "Enter the server password."
    }

    // State Management
    private val _stateFlow = MutableStateFlow<AuthState>(AuthState.Unconfigured)
    val stateFlow: StateFlow<AuthState> = _stateFlow.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerAccount>>(emptyList())
    val servers: StateFlow<List<ServerAccount>> = _servers.asStateFlow()

    private val _activeServerID = MutableStateFlow<String?>(null)
    val activeServerID: StateFlow<String?> = _activeServerID.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()

    init {
        restoreSavedServer()
        refreshServers()
    }

    // --- Public API ---

    fun testConnection(serverURLString: String): Flow<AuthStatusResponse> {
        return flow {
            val serverURL = normalizeURL(serverURLString)
            val client = okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    // Inject custom headers here if needed
                    chain.proceed(chain.request())
                }
                .build()

            val healthResponse = client.newCall(Request.Builder()
                .url("${serverURL}${HEALTH_PATH}")
                .get()
                .build()
            ).execute()

            if (healthResponse.code == 200) {
                val body = healthResponse.body?.string() ?: throw Exception("Empty health response")
                val statusResponse = json.decodeFromString<AuthStatusResponse>(body)
                emit(statusResponse)
            } else {
                throw Exception("Health check failed: ${healthResponse.code}")
            }
        }.catch { e ->
            throw e
        }
    }

    fun configure(serverURLString: String, password: String): Flow<AuthResult> {
        return flow {
            _lastErrorMessage.value = null
            _stateFlow.value = AuthState.Loading

            try {
                val serverURL = normalizeURL(serverURLString)
                val authStatus = testConnection(serverURLString).first()

                // Check for Passkey-only server
                if (authStatus.authEnabled && authStatus.passwordAuthEnabled == false) {
                    _lastErrorMessage.value = PASSKEY_ONLY_MESSAGE
                    _stateFlow.value = AuthState.Error
                    emit(AuthResult.Failed(PASSKEY_ONLY_MESSAGE))
                    return@flow
                }

                if (authStatus.authEnabled) {
                    if (password.isBlank()) {
                        _lastErrorMessage.value = ENTER_PASSWORD_MESSAGE
                        _stateFlow.value = AuthState.Error
                        emit(AuthResult.Failed(ENTER_PASSWORD_MESSAGE))
                        return@flow
                    }

                    // Perform Login
                    val loginJson = json.encodeToString(json.encodeToJsonElement(PasswordRequest(password)))
                    val request = Request.Builder()
                        .url("${serverURL}${LOGIN_PATH}")
                        .post(loginJson.toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseString = response.body?.string() ?: throw Exception("Empty login response")
                    
                    val loginResponse = json.decodeFromString<LoginResponse>(responseString)

                    if (!loginResponse.ok) {
                        _stateFlow.value = AuthState.LoggedOut
                        _lastErrorMessage.value = "Unauthorized"
                        emit(AuthResult.Failed("Unauthorized"))
                        return@flow
                    }

                    // Success Path
                    saveServerData(serverURLString, loginResponse.token ?: "")
                    serverRegistry.activate(serverURLString) // Assuming ServerRegistry handles this
                    refreshServers()
                    _activeServerID.value = serverURLString
                    _stateFlow.value = AuthState.LoggedIn
                    emit(AuthResult.Success(serverURLString, loginResponse.token))
                } else {
                    // Auth not enabled on server
                    _stateFlow.value = AuthState.LoggedOut
                    emit(AuthResult.Success(serverURLString, null))
                }
            } catch (e: Exception) {
                _lastErrorMessage.value = e.message ?: "Unknown error"
                _stateFlow.value = AuthState.Error
                emit(AuthResult.Failed(e.message ?: "Unknown error"))
            }
        }
    }

    // --- Private Helpers ---

    private fun restoreSavedServer() {
        val savedUrl = KeychainStore.getServerUrl(context)
        if (savedUrl != null) {
            // Attempt to restore state
            val token = KeychainStore.getToken(context)
            if (token != null) {
                _activeServerID.value = savedUrl
                _stateFlow.value = AuthState.LoggedIn
                refreshServers()
            } else {
                _stateFlow.value = AuthState.LoggedOut
                refreshServers()
            }
        }
    }

    private fun saveServerData(url: String, token: String) {
        KeychainStore.save(context, url, token)
    }

    private fun refreshServers() {
        // In a real app, this would read from the serverRegistry
        // For now, we simulate fetching the list
        _servers.value = serverRegistry.getServers() 
    }

    private fun normalizeURL(input: String): URL {
        // Basic validation and normalization
        val trimmed = input.trim()
        return try {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                URL("https://$trimmed")
            } else {
                URL(trimmed)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL: $input", e)
        }
    }

    // --- Inner Classes for Requests ---
    @Serializable
    data class PasswordRequest(val password: String)
    
    sealed class AuthResult {
        data class Success(val serverUrl: String, val token: String?) : AuthResult()
        data class Failed(val message: String) : AuthResult()
    }
}