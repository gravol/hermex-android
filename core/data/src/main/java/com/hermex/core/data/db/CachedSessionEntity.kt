import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "cached_sessions")
data class CachedSessionEntity(
    @PrimaryKey
    val cacheKey: String,
    val serverURLString: String,
    val sessionDataJson: String, // Serialized SessionSummary
    val cachedAt: Long, // Timestamp in milliseconds
    val updatedAt: Long
)

@Entity(tableName = "cached_messages")
data class CachedMessageEntity(
    @PrimaryKey
    val cacheKey: String,
    val serverURLString: String,
    val sessionID: String,
    val messageDataJson: String, // Serialized ChatMessage
    val sortIndex: Int,
    val cachedAt: Long,
    val updatedAt: Long
)