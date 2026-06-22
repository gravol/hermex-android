package com.hermes.chat.network

import com.hermes.chat.model.Message

/**
 * Interface for communicating with the Hermes API.
 * The concrete implementation will POST to a Hermes-compatible server.
 */
interface HermesClient {
    /**
     * Send the full conversation history and return the assistant's response.
     * Implementations must be safe to call from any thread.
     */
    suspend fun sendMessage(conversation: List<Message>): Message

    /**
     * Return model IDs advertised by the Hermes/OpenAI-compatible `/v1/models` endpoint.
     */
    suspend fun listModels(): List<String>
}
