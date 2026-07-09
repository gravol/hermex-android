class AuthViewModel @Inject constructor(
    private val context: Context,
    private val serverRegistry: ServerRegistry,
    private val authManager: AuthManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.stateFlow
    val errorMessage: StateFlow<String?> = authManager.lastErrorMessage
    val servers: StateFlow<List<ServerAccount>> = authManager.servers
    val activeServerID: StateFlow<String?> = authManager.activeServerID

    fun login(serverUrl: String, password: String) {
        viewModelScope.launch {
            authManager.configure(serverUrl, password).collect { result ->
                when (result) {
                    is AuthManager.AuthResult.Success -> {
                        // Handle success (e.g., navigate to home)
                    }
                    is AuthManager.AuthResult.Failed -> {
                        // Handle error (e.g., show snackbar)
                    }
                }
            }
        }
    }

    fun logout() {
        KeychainStore.clear(context)
        authManager.refreshServers() // Update registry
        authManager._stateFlow.value = AuthState.LoggedOut // Manually set state if not handled by manager logic
        authManager._activeServerID.value = null
    }
}