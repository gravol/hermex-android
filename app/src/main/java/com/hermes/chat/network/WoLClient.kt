package com.hermes.chat.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends a Wake-on-LAN magic packet over UDP broadcast.
 *
 * The magic packet is 6 bytes of 0xFF followed by the target MAC
 * address repeated 16 times.
 */
object WoLClient {

    suspend fun sendWakeOnLan(macAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val macBytes = parseMac(macAddress)
            val packet = ByteArray(6 + 16 * 6)

            // 6 bytes of 0xFF
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            // MAC repeated 16 times
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                val addr = InetAddress.getByName("255.255.255.255")
                val dp = DatagramPacket(packet, packet.size, addr, 9)
                socket.send(dp)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseMac(mac: String): ByteArray {
        val cleaned = mac.replace(":", "").replace("-", "").replace(" ", "")
        return (0 until 12 step 2).map { i ->
            (cleaned.substring(i, i + 2).toInt(16) and 0xFF).toByte()
        }.toByteArray()
    }
}
