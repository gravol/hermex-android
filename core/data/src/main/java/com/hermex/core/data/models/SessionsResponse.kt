import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SessionsResponse(
    @SerialName("sessions")
    val sessions: List<SessionSummary>? = null,

    @SerialName("cliCount")
    val cliCount: Int? = null,

    @SerialName("serverTime")
    val serverTime: Double? = null
)