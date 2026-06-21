package com.hermes.chat.network

import com.hermes.chat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal Hermes HTTP client.
 * Sends chat completions to a Hermes-compatible /v1/chat/completions endpoint.
 * No auth headers, no cert pinning, no model switching — just a bare POST.
 */
class HermesOkHttpClient : HermesClient {

    /** Override to point at a different server or port. */
    var baseUrl: String = "http://localhost:8080/v1/chat/completions"

    /** Model name sent in each request. Override to switch models. */
    var model: String = "hermes"

    /** Max tokens in the response. */
    var maxTokens: Int = 4096

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun sendMessage(conversation: List<Message>): Message = withContext(Dispatchers.IO) {
        val requestBody = buildRequest(conversation)
        val httpRequest = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .build()

        val httpResponse: okhttp3.Response = try {
            client.newCall(httpRequest).execute()
        } catch (e: Exception) {
            return@withContext Message(
                role = "assistant",
                text = "⚠️ Connection failed: ${e.message ?: "unknown error"}",
            )
        }

        val bodyString = httpResponse.body?.string() ?: ""
        if (!httpResponse.isSuccessful) {
            return@withContext Message(
                role = "assistant",
                text = "⚠️ HTTP ${httpResponse.code}: ${bodyString.take(200)}",
            )
        }

        try {
            val response = HermesResponse.fromJson(bodyString)
            Message(role = "assistant", text = response.content.ifEmpty { "(empty response)" })
        } catch (e: Exception) {
            Message(
                role = "assistant",
                text = "⚠️ Parse error: ${e.message ?: "unknown"}\n\nRaw:\n${bodyString.take(300)}",
            )
        }
    }

    private fun buildRequest(conversation: List<Message>): okhttp3.RequestBody {
        val apiMessages = conversation.map { msg ->
            HermesRequest.RequestMessage(role = msg.role, content = msg.text)
        }
        val request = HermesRequest(
            model = model,
            messages = apiMessages,
            maxTokens = maxTokens,
        )
        return request.toJson().toRequestBody(jsonMediaType)
    }
}
