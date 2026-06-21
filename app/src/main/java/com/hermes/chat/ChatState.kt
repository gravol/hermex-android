package com.hermes.chat

import androidx.compose.runtime.mutableStateListOf
import com.hermes.chat.model.Message

/**
 * Holds chat message state.
 * Messages flow: user types → appended to list → HermesClient sends → response appended.
 * For now, sending just echoes back a placeholder.
 */
class ChatState {
    val messages = mutableStateListOf<Message>()

    fun sendLocalMessage(text: String) {
        if (text.isBlank()) return

        messages.add(
            Message(role = "user", text = text.trim())
        )

        // Placeholder — will be replaced by real Hermes API call
        messages.add(
            Message(
                role = "assistant",
                text = "...",
                timestamp = System.currentTimeMillis() + 1,
            )
        )
    }
}
