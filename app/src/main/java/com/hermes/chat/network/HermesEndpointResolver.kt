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

    private const val HOME_BASE_URL = "http://localhost:8080/v1/chat/completions"

    /**
     * Returns the full endpoint URL for the given [mode] and [awayUrl].
     *
     * @param awayUrl The user-configured away URL (full path expected).
     *                If blank while in AWAY mode, falls back to HOME.
     */
    fun resolve(mode: NetworkMode, awayUrl: String): String {
        return when (mode) {
            NetworkMode.HOME -> HOME_BASE_URL
            NetworkMode.AWAY -> awayUrl.ifBlank { HOME_BASE_URL }
        }
    }
}
