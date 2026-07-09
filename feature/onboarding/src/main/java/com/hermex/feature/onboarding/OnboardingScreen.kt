data class OnboardingState(
    val serverUrl: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val currentStep: Int = 0,
    val totalPages: Int = 4
)

data class Session(
    val id: String,
    val title: String,
    val date: Long, // Timestamp
    val isCron: Boolean = false,
    val hasMessages: Boolean = false
)