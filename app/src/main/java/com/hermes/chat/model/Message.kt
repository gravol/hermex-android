package com.hermes.chat.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<MessageAttachment> = emptyList(),
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
    val isSystem: Boolean get() = role == "system"
}
