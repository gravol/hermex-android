import androidx.room.TypeConverter
import java.util.Date

object Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromString(value: String?): String? {
        return value
    }

    @TypeConverter
    fun toString(value: String?): String? {
        return value
    }
    
    // Generic JSON Converters
    @TypeConverter
    fun fromSessionJson(value: String?): String? {
        return value
    }

    @TypeConverter
    fun toSessionJson(value: String?): String? {
        return value
    }
    
    @TypeConverter
    fun fromMessageJson(value: String?): String? {
        return value
    }

    @TypeConverter
    fun toMessageJson(value: String?): String? {
        return value
    }
}