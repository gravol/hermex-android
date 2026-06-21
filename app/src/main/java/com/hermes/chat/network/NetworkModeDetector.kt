package com.hermes.chat.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import com.hermes.chat.model.NetworkMode
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Detects whether the device is on the home network by probing the local
 * Hermes server at localhost:8080 (the default dev endpoint).
 *
 * If the probe succeeds the device is [NetworkMode.HOME].
 * If it fails (timeout / refused) the device is [NetworkMode.AWAY].
 */
object NetworkModeDetector {

    /**
     * Try to connect to the local Hermes server.
     * Returns [NetworkMode.HOME] on success, [NetworkMode.AWAY] on failure.
     *
     * @param host Hostname to probe (default localhost).
     * @param port TCP port to probe (default 8080).
     * @param timeoutMs Connect timeout in milliseconds (default 2000).
     */
    suspend fun detect(
        host: String = "localhost",
        port: Int = 8080,
        timeoutMs: Long = 2000,
    ): NetworkMode = withContext(Dispatchers.IO) {
        val connected = withTimeoutOrNull(timeoutMs) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                    true
                }
            } catch (_: Exception) {
                false
            }
        } ?: false

        if (connected) NetworkMode.HOME else NetworkMode.AWAY
    }
}
