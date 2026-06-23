package com.hermes.chat.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI-style chat completion request body.
 */
data class HermesRequest(
    val model: String = "hermes",
    val messages: List<RequestMessage>,
    val maxTokens: Int = 4096,
    val stream: Boolean = false,
) {
    data class RequestMessage(
        val role: String,
        val content: Any,
    )

    fun toJson(): String = JSONObject().apply {
        put("model", model)
        put("max_tokens", maxTokens)
        put("stream", stream)
        put("messages", JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", when (val content = msg.content) {
                        is JSONArray -> content
                        is JSONObject -> content
                        else -> content.toString()
                    })
                })
            }
        })
    }.toString()
}

/**
 * OpenAI-style chat completion response body.
 */
data class HermesResponse(
    val id: String = "",
    val choices: List<Choice>? = null,
) {
    data class Choice(
        val message: ResponseMessage,
        val finishReason: String = "",
    )

    data class ResponseMessage(
        val role: String,
        val content: String,
    )

    /** The first choice's content, or empty string. */
    val content: String get() = choices?.firstOrNull()?.message?.content ?: ""

    companion object {
        fun fromJson(json: String): HermesResponse {
            val root = JSONObject(json)
            val choicesArr = root.optJSONArray("choices")
            val choices = if (choicesArr != null) {
                (0 until choicesArr.length()).map { i ->
                    val c = choicesArr.getJSONObject(i)
                    val msg = c.getJSONObject("message")
                    Choice(
                        message = ResponseMessage(
                            role = msg.optString("role", "assistant"),
                            content = msg.optString("content", ""),
                        ),
                        finishReason = c.optString("finish_reason", "")
                    )
                }
            } else emptyList()

            return HermesResponse(
                id = root.optString("id", ""),
                choices = choices,
            )
        }
    }
}

/**
 * OpenAI-style `/v1/models` response body.
 */
data class HermesModelListResponse(
    val ids: List<String>,
) {
    companion object {
        fun fromJson(json: String): HermesModelListResponse {
            val root = JSONObject(json)
            val data = root.optJSONArray("data") ?: JSONArray()
            val ids = (0 until data.length()).mapNotNull { i ->
                data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
            }
            return HermesModelListResponse(ids)
        }
    }
}
