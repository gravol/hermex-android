package com.hermes.chat.network

import com.hermes.chat.model.Message

/**
 * Interface for communicating with the Hermes API.
 * The real implementation will connect to the Hermes Agent server.
 * For now, only used as a type placeholder.
 */
interface HermesClient {
    /** Send a user message and return the assistant's response. */
    suspend fun sendMessage(text: String): Message
}
