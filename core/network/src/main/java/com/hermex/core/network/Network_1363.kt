// Start a chat
lifecycleScope.launch {
    val result = ApiClient.startChat(
        sessionID = "123",
        message = "Hello",
        workspace = "dev",
        model = "gpt-4",
        explicitModelPick = true
    )
    
    when (result) {
        is NetworkResult.Success -> {
            // Handle success
            val chatResponse = result.data
        }
        is NetworkResult.Error -> {
            // Network error (no internet, timeout)
            result.exception.printStackTrace()
        }
        is NetworkResult.HttpError -> {
            // HTTP error (401, 404, 500)
            Log.e("API", "HTTP ${result.code}: ${result.message}")
        }
    }
}

// Start SSE for chat stream
val url = ApiClient.getChatStreamURL("123", replayAfterSeq = 5)
val eventSourceListener = object : EventSourceListener() {
    override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
        // Connection established
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        // Handle incoming data string
        val jsonElement = Json.parseToJsonElement(data)
        // Process jsonElement
    }

    override fun onFailure(eventSource: EventSource, t: Throwable, response: okhttp3.Response?) {
        t.printStackTrace()
    }
}

ApiClient.startSSE(url, eventSourceListener)