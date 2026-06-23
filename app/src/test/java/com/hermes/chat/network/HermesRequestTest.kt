package com.hermes.chat.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HermesRequestTest {

    @Test
    fun `serializes multimodal content arrays`() {
        val content = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", "What is this?")
            })
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/png;base64,abc123")
                    put("detail", "auto")
                })
            })
        }

        val request = HermesRequest(
            model = "hermes-agent",
            messages = listOf(
                HermesRequest.RequestMessage(
                    role = "user",
                    content = content,
                )
            ),
        )

        val root = JSONObject(request.toJson())
        val message = root.getJSONArray("messages").getJSONObject(0)
        val serializedContent = message.getJSONArray("content")

        assertEquals("user", message.getString("role"))
        assertEquals("text", serializedContent.getJSONObject(0).getString("type"))
        assertEquals("What is this?", serializedContent.getJSONObject(0).getString("text"))
        assertEquals(
            "data:image/png;base64,abc123",
            serializedContent.getJSONObject(1).getJSONObject("image_url").getString("url"),
        )
    }
}
