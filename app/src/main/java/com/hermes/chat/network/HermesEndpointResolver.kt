package com.hermes.chat.network

import com.hermes.chat.model.NetworkMode

/**
 * Resolves the Hermes API endpoint URL based on the current [NetworkMode].
 *
 * - [HOME] → localhost:8080 (dev default).
 * - [AWAY] → the user-configured [awayUrl] (typically a Tailscale address).
 *
 * If [awayUrl] is blank and mode is AWAY, falls back to the HOME URL.
 */
object HermesEndpointResolver {

    /**
     * Default local development base URL. Only meaningful when the app
     * is running inside an emulator on the same machine as the Hermes server.
     */
    const val HOME_BASE_URL = "http://localhost:8080/v1/chat/completions"

    /**
     * Resolve the Hermes API endpoint to use.
     *
     * - [HOME] → [HOME_BASE_URL] (localhost dev default).
     * - [AWAY] → [awayUrl] if non-blank, otherwise returns empty string
     *   (caller must handle the "no endpoint configured" case gracefully).
     */
    fun resolve(mode: NetworkMode, awayUrl: String): String {
        return when (mode) {
            NetworkMode.HOME -> HOME_BASE_URL
            NetworkMode.AWAY -> awayUrl.trim().ifEmpty { "" }
        }
    }
}
