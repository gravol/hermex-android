package com.hermes.chat.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HermesModelListResponseTest {

    @Test
    fun `parses OpenAI style model IDs`() {
        val json = """
            {
              "object": "list",
              "data": [
                { "id": "deepseek-v4-flash", "object": "model" },
                { "id": "deepseek-v4-pro", "object": "model" },
                { "id": "local-test-model", "object": "model" }
              ]
            }
        """.trimIndent()

        val response = HermesModelListResponse.fromJson(json)

        assertEquals(
            listOf("deepseek-v4-flash", "deepseek-v4-pro", "local-test-model"),
            response.ids,
        )
    }

    @Test
    fun `missing data returns empty list`() {
        val response = HermesModelListResponse.fromJson("{}")

        assertEquals(emptyList<String>(), response.ids)
    }
}
