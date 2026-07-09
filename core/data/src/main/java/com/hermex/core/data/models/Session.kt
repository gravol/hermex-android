// HermesMobile/Models/Session.kt
package com.hermes.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    @SerialName("session_id")
    val sessionId: String,
    
    @SerialName("user_id")
    val userId: String?,
    
    @SerialName("created_at")
    val createdAt: String?,
    
    @SerialName("is_active")
    val isActive: Boolean? = true
)