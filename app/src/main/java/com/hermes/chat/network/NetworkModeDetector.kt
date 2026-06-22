package com.hermes.chat.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** Lightweight TCP reachability checks for endpoint auto-selection. */
object NetworkModeDetector {

    /**
     * Probe a configured HTTP(S) endpoint by opening a TCP socket to its host/port.
     * This does not send auth tokens and does not make a Hermes API request.
     */
    suspend fun canReachEndpoint(
        endpointUrl: String,
        timeoutMs: Long = 1200,
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = endpointUrl.trim()
        if (trimmed.isBlank()) return@withContext false

        val target = runCatching { URL(trimmed) }.getOrNull() ?: return@withContext false
        val host = target.host.takeIf { it.isNotBlank() } ?: return@withContext false
        val port = when {
            target.port > 0 -> target.port
            target.protocol.equals("https", ignoreCase = true) -> 443
            else -> 80
        }

        withTimeoutOrNull(timeoutMs) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                    true
                }
            } catch (_: Exception) {
                false
            }
        } ?: false
    }
}
