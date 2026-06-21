package com.hermes.chat.network

import com.hermes.chat.model.NtfyConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Fires-and-forgets ntfy.sh notifications for chat, error, and system events.
 * Reads the current topic/auth from a [configProvider] lambda so it stays in sync.
 */
class NtfyPublisher(
    private val configProvider: () -> NtfyConfig,
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    fun send(title: String, message: String, tags: List<String> = emptyList()) {
        val config = configProvider()
        if (!config.isConfigured) return

        val body = JSONObject().apply {
            put("title", title)
            put("message", message)
            put("tags", JSONArray().apply {
                tags.forEach { put(it) }
            })
        }.toString()

        val request = Request.Builder()
            .url("https://ntfy.sh/${config.topic}")
            .post(body.toRequestBody(jsonMediaType))
            .apply {
                if (config.authToken.isNotBlank()) {
                    header("Authorization", "Bearer ${config.authToken}")
                }
            }
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* fire-and-forget */ }
            override fun onResponse(call: Call, response: okhttp3.Response) { response.close() }
        })
    }
}
