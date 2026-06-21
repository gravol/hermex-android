package com.hermes.chat.network

import com.hermes.chat.model.NtfyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Subscribes to an ntfy.sh topic via SSE (Server-Sent Events) and dispatches
 * incoming messages to [onMessage].
 *
 * Call [start] to begin listening and [stop] to tear down the connection.
 * Only one active subscription is allowed at a time.
 */
class NtfyClient(
    private val onMessage: (title: String, message: String) -> Unit,
) {
    private var job: Job? = null

    fun start(config: NtfyConfig) {
        stop()
        if (!config.isConfigured) return
        job = CoroutineScope(Dispatchers.IO + Job()).launch {
            subscribe(config)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun subscribe(config: NtfyConfig) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://ntfy.sh/${config.topic}/json")
            .header("Accept", "text/event-stream")
            .apply {
                if (config.authToken.isNotBlank()) {
                    header("Authorization", "Bearer ${config.authToken}")
                }
            }
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body ?: return
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            reader.use { r ->
                var dataLine = ""
                for (line in r.lineSequence()) {
                    when {
                        line.startsWith("data: ") -> dataLine = line.removePrefix("data: ")
                        line.isBlank() && dataLine.isNotBlank() -> {
                            parseEvent(dataLine)
                            dataLine = ""
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Connection dropped — re-subscribe on next start()
        }
    }

    private fun parseEvent(json: String) {
        try {
            val obj = JSONObject(json)
            val event = obj.optString("event", "")
            if (event == "message") {
                val title = obj.optString("title", "")
                val message = obj.optString("message", "")
                if (title.isNotBlank() || message.isNotBlank()) {
                    onMessage(title, message)
                }
            }
        } catch (_: Exception) {
            // malformed event, skip
        }
    }
}
