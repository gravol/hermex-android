package com.hermes.chat.network

import com.hermes.chat.model.DeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Lightweight device-status checker.
 * Attempts a TCP connection to port 22 (SSH) with a short timeout.
 * Reachable → AWAKE.  Unreachable → OFF.  Blank IP → UNKNOWN.
 */
object DeviceStatusChecker {

    suspend fun checkStatus(ipAddress: String): DeviceState = withContext(Dispatchers.IO) {
        if (ipAddress.isBlank()) return@withContext DeviceState.UNKNOWN

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, 22), 2_000)
            }
            DeviceState.AWAKE
        } catch (_: Exception) {
            DeviceState.OFF
        }
    }
}
