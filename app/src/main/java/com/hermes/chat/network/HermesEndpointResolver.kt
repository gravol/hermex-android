package com.hermes.chat.network

import com.hermes.chat.model.NetworkMode

/**
 * Resolves the Hermes API endpoint URL based on the user's routing mode.
 *
 * Auto mode is intentionally Tailscale-first because Jeff is normally connected
 * to Tailscale and wants the least manual switching.
 */
object HermesEndpointResolver {

    /** Emulator/dev fallback only. Real phones should use configured Local/Tailscale URLs. */
    const val DEBUG_LOCALHOST_URL = "http://localhost:8080/v1/chat/completions"

    fun resolve(
        mode: NetworkMode,
        localUrl: String,
        tailscaleUrl: String,
    ): String {
        val local = localUrl.trim()
        val tailscale = tailscaleUrl.trim()
        return when (mode) {
            NetworkMode.AUTO -> tailscale.ifBlank { local }
            NetworkMode.LOCAL -> local
            NetworkMode.TAILSCALE -> tailscale
        }
    }
}
