// File: app/src/main/java/com/hermex/android/data/local/entities/CachedMessage.kt
package com.hermex.android.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey
    val messageId: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long
)