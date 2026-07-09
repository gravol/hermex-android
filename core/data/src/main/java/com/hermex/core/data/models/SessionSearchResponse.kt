import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

// Main response class with tolerant decoding
@Serializable
data class SessionSearchResponse(
    // Assuming there are other fields, this is just the visible one
    @SerialName("server_tz")
    val serverTz: String? = null,
    
    // Add other fields as needed
    @SerialName("other_field")
    val otherField: String? = null
)

// For tolerant decoding (ignoring unknown keys)
val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

// Example with more complete structure
@Serializable
data class SessionSearchResponse(
    @SerialName("server_tz")
    val serverTz: String?,
    
    @SerialName("session_id")
    val sessionId: String?,
    
    @SerialName("created_at")
    val createdAt: String?
) {
    // Companion object for custom deserialization if needed
    companion object {
        // Custom JSON instance with tolerant settings
        private val jsonConfig = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }
}

// If you have enums, use sealed classes
@Serializable
sealed class ServerStatus {
    @Serializable
    @SerialName("active")
    data object Active : ServerStatus()
    
    @Serializable
    @SerialName("inactive")
    data object Inactive : ServerStatus()
    
    @Serializable
    @SerialName("pending")
    data object Pending : ServerStatus()
}

// Usage example
fun decodeSessionResponse(jsonString: String): SessionSearchResponse {
    return json.decodeFromString(SessionSearchResponse.serializer(), jsonString)
}