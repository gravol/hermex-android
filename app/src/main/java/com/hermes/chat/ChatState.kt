package com.hermes.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.hermes.chat.model.Message
import com.hermes.chat.model.ModelType
import com.hermes.chat.network.HermesClient
import com.hermes.chat.network.HermesOkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds chat message state, current model, and drives send / slash-command flow.
 * Defaults to HermesOkHttpClient for convenience.
 */
class ChatState(
    private val client: HermesClient = HermesOkHttpClient(),
) {
    val messages = mutableStateListOf<Message>()

    var currentModel: ModelType by mutableStateOf(ModelType.FLASH)
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Switch model and show a confirmation message. */
    fun setModel(model: ModelType) {
        if (model == currentModel) return
        currentModel = model
        (client as? HermesOkHttpClient)?.model = model.apiName
        messages.add(
            Message(
                role = "system",
                text = "✅ Switched to **${model.displayName}**",
            )
        )
    }

    /** Send a message or handle a slash command. */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Check for slash commands first
        val command = SlashCommandParser.parse(text)
        if (command != null) {
            handleCommand(command)
            return
        }

        // Normal message flow
        val userMsg = Message(role = "user", text = text.trim())
        messages.add(userMsg)

        // Add pending placeholder
        val pendingIndex = messages.size
        messages.add(Message(role = "assistant", text = "..."))

        // Fire API call
        scope.launch {
            val response = client.sendMessage(
                messages.toList().filter { it.text != "..." && !it.isSystem }
            )
            if (pendingIndex < messages.size) {
                messages[pendingIndex] = response
            }
        }
    }

    private fun handleCommand(command: SlashCommand) {
        when (command) {
            is SlashCommand.SetModel -> setModel(command.model)
        }
    }
}
