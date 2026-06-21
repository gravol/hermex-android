package com.hermes.chat

import androidx.compose.runtime.mutableStateListOf
import com.hermes.chat.model.Message
import com.hermes.chat.network.HermesClient
import com.hermes.chat.network.HermesOkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds chat message state and drives send → API → response flow.
 * Requires a HermesClient; defaults to HermesOkHttpClient for convenience.
 */
class ChatState(
    private val client: HermesClient = HermesOkHttpClient(),
) {
    val messages = mutableStateListOf<Message>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message
        val userMsg = Message(role = "user", text = text.trim())
        messages.add(userMsg)

        // Add pending placeholder
        val pendingIndex = messages.size
        messages.add(Message(role = "assistant", text = "..."))

        // Fire API call
        scope.launch {
            val response = client.sendMessage(messages.toList().filter { it.text != "..." })
            // Replace the placeholder with the real response
            if (pendingIndex < messages.size) {
                messages[pendingIndex] = response
            }
        }
    }
}
