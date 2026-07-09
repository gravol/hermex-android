// File: app/src/main/java/com/hermex/android/data/local/entities/Auth.kt
package com.hermex.android.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auth")
data class Auth(
    @PrimaryKey
    val id: String,
    val token: String,
    val expiresAt: Long,
    val userId: String,
    val createdAt: Long
)