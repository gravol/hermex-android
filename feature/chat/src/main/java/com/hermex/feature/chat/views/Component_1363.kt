// ChatRepository.kt
class ChatRepository {
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun streamResponse(prompt: String): Flow<Message> = flow {
        val request = Request.Builder()
            .url("https://api.example.com/chat")
            .post(RequestBody.create(
                MediaType.parse("application/json"),
                """{ "prompt": "$prompt" }"""
            ))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use

            val body = response.body?.string()
            // Parse SSE stream and emit messages
            body?.lines()?.forEach { line ->
                if (line.startsWith("data: ")) {
                    val json = line.removePrefix("data: ")
                    val message = parseMessage(json)
                    emit(message)
                }
            }
        }
    }

    fun cancelStream(messageId: String) {
        // Implement cancellation logic
    }

    private fun parseMessage(json: String): Message {
        // Parse JSON to Message object
        return Message(
            id = UUID.randomUUID().toString(),
            role = Role.ASSISTANT,
            content = json,
            isStreaming = true
        )
    }
}