// src/main/java/com/chatapp/network/SSEStreamingClient.kt

package com.chatapp.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SSEStreamingClient {
    private var connection: HttpURLConnection? = null
    private var connected = false
    private var cancelled = false

    fun connect(
        onEvent: (SSEEvent) -> Unit,
        onError: (Exception) -> Unit,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val url = URL("https://api.example.com/chat/stream")
                connection = url.openConnection() as HttpURLConnection
                connection?.doInput = true
                connection?.connectTimeout = 30000
                connection?.readTimeout = 30000
                connection?.requestMethod = "GET"
                connection?.setRequestProperty("Accept", "text/event-stream")
                connection?.setRequestProperty("Cache-Control", "no-cache")

                val reader = BufferedReader(InputStreamReader(connection?.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null && !cancelled) {
                    val text = line ?: continue
                    if (text.startsWith("data:")) {
                        val content = text.removePrefix("data:").trim()
                        if (content.isEmpty()) {
                            // Keep connection alive
                            continue
                        }
                        val event = parseSSEEvent(content)
                        onEvent(event)
                    }
                }
                
                onComplete()
                connected = false
            } catch (e: Exception) {
                onError(e)
                connected = false
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseSSEEvent(content: String): SSEEvent {
        // Parse SSE event based on content
        return SSEEvent(
            type = SSEEventType.MESSAGE,
            text = content
        )
    }

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    fun close() {
        cancel()
    }
}

private fun viewModelScope() = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
)