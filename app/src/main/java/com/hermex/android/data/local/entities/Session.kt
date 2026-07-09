// File: app/src/main/java/com/hermex/android/data/local/entities/Session.kt
package com.hermex.android.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean
)