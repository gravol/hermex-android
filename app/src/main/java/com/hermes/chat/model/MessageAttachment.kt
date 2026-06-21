package com.hermes.chat.model

import java.util.UUID

enum class AttachmentType { IMAGE, VOICE }

data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val uri: String,
    val displayName: String,
)
