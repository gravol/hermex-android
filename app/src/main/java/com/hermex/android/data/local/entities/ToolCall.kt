// File: app/src/main/java/com/hermex/android/data/local/entities/ToolCall.kt
package com.hermex.android.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tool_calls")
data class ToolCall(
    @PrimaryKey
    val id: String,
    val messageId: String,
    val name: String,
    val arguments: String,
    val status: String,
    val result: String?
)