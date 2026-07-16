package com.hermex.core.data.auth

import kotlinx.serialization.Serializable

// Represents the auth status response from the server
@Serializable
data class AuthStatusResponse(
    val status: String,
    val authEnabled: Boolean,
    val passwordAuthEnabled: Boolean? // Nullable to handle older servers
)

@Serializable
data class LoginResponse(
    val ok: Boolean,
    val token: String? // In a real app, this might be a JWT or session ID
)

// The core state of the authentication manager
enum class AuthState {
    Unconfigured,
    LoggedOut,
    LoggedIn,
    Loading,
    Error
}

// Represents a configured server account
data class ServerAccount(
    val id: String, // Normalized URL string
    val url: String,
    val isActive: Boolean
)

// Custom headers
data class CustomHeader(
    val name: String,
    val value: String
)