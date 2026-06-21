package com.hermes.chat.model

data class NtfyConfig(
    val topic: String = "",
    val authToken: String = "",
) {
    val isConfigured: Boolean get() = topic.isNotBlank()
}
