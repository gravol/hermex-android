package com.hermes.chat.model

import org.json.JSONObject
import java.time.Instant

/**
 * Serializable snapshot of user-configurable settings.
 *
 * @property version Schema version for forward-compatibility.
 * @property exportedAt ISO-8601 timestamp of export.
 * @property model Current model display name (e.g. "Flash").
 * @property awayUrl Away (Tailscale) endpoint URL.
 * @property ntfyTopic ntfy.sh notification topic.
 * @property clerkMacAddress WoL MAC address for the Clerk machine.
 * @property clerkIpAddress IP address for Clerk status checks.
 *
 * NOTE: API token is deliberately excluded for security.
 */
data class SettingsBackup(
    val version: Int = 1,
    val exportedAt: String = Instant.now().toString(),
    val model: String = "",
    val awayUrl: String = "",
    val ntfyTopic: String = "",
    val clerkMacAddress: String = "",
    val clerkIpAddress: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("exportedAt", exportedAt)
        put("model", model)
        put("awayUrl", awayUrl)
        put("ntfyTopic", ntfyTopic)
        put("clerkMacAddress", clerkMacAddress)
        put("clerkIpAddress", clerkIpAddress)
    }.toString(2)

    companion object {
        fun fromJson(json: String): SettingsBackup? = try {
            val obj = JSONObject(json)
            SettingsBackup(
                version = obj.optInt("version", 1),
                exportedAt = obj.optString("exportedAt", ""),
                model = obj.optString("model", ""),
                awayUrl = obj.optString("awayUrl", ""),
                ntfyTopic = obj.optString("ntfyTopic", ""),
                clerkMacAddress = obj.optString("clerkMacAddress", ""),
                clerkIpAddress = obj.optString("clerkIpAddress", ""),
            )
        } catch (_: Exception) {
            null
        }
    }
}
