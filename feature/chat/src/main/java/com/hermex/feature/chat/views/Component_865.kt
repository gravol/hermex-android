// ChatRepository.kt
interface ChatRepository {
    suspend fun getMessages(sessionId: String): List<ChatMessage>
    suspend fun saveMessage(message: ChatMessage)
    suspend fun streamResponse(
        sessionId: String,
        text: String,
        attachments: List<Attachment>
    ): Flow<StreamEvent>

    suspend fun uploadAttachment(uri: Uri): Uri
    suspend fun getAvailableModels(): List<ModelOption>
    suspend fun selectModel(modelId: String)
    suspend fun setWorkspacePath(path: String)
}

sealed class StreamEvent {
    data class MessageUpdate(val messageId: String, val content: String) : StreamEvent()
    data class ToolCallUpdate(val toolCall: ToolCall) : StreamEvent()
    data class ReasoningUpdate(val block: ReasoningBlock) : StreamEvent()
    data object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}