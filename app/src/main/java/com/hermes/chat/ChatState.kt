package com.hermes.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.hermes.chat.model.Message
import com.hermes.chat.model.ModelType
import com.hermes.chat.model.NtfyConfig
import com.hermes.chat.network.HermesClient
import com.hermes.chat.network.HermesOkHttpClient
import com.hermes.chat.network.NtfyPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds chat message state, current model, ntfy config, pending privileged commands,
 * and drives send / slash-command flow.
 * Defaults to HermesOkHttpClient for convenience.
 */
class ChatState(
    private val client: HermesClient = HermesOkHttpClient(),
) {
    val messages = mutableStateListOf<Message>()

    var currentModel: ModelType by mutableStateOf(ModelType.FLASH)
        private set

    /** Non-null when a privileged command is waiting for biometric auth. */
    var pendingPrivilegedCommand: SlashCommand? by mutableStateOf(null)
        private set

    /** ntfy.sh topic and optional auth token. */
    var ntfyConfig: NtfyConfig by mutableStateOf(NtfyConfig())

    /** Clerk device MAC and IP for WoL and status checking. */
    var clerkMacAddress: String by mutableStateOf("")
    var clerkIpAddress: String by mutableStateOf("")

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Publisher is re-reads [ntfyConfig] on each send via the provider lambda. */
    private val publisher = NtfyPublisher { ntfyConfig }

    // ── Public API ──────────────────────────────────────────────

    /** Switch model and show a confirmation message. */
    fun setModel(model: ModelType) {
        if (model == currentModel) return
        currentModel = model
        (client as? HermesOkHttpClient)?.model = model.apiName
        addSystem("✅ Switched to **${model.displayName}**")
        publisher.send("Model Changed", "Switched to ${model.displayName}", listOf("hermes", "settings"))
    }

    /** Update the ntfy topic and publish a test event when non-empty. */
    fun setNtfyTopic(topic: String) {
        if (ntfyConfig.topic == topic) return
        ntfyConfig = ntfyConfig.copy(topic = topic)
        if (topic.isNotBlank()) {
            publisher.send("Hermes Chat", "ntfy pipeline configured for: $topic", listOf("hermes", "settings"))
        }
    }

    /** Send a message or handle a slash command (privileged commands are blocked until auth). */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Check for slash commands first
        val command = SlashCommandParser.parse(text)
        if (command != null) {
            if (command.isPrivileged) {
                pendingPrivilegedCommand = command
                return
            }
            handleCommand(command)
            return
        }

        // Normal message flow
        val userMsg = Message(role = "user", text = text.trim())
        messages.add(userMsg)

        val pendingIndex = messages.size
        messages.add(Message(role = "assistant", text = "..."))

        scope.launch {
            val response = client.sendMessage(
                messages.toList().filter { it.text != "..." && !it.isSystem }
            )
            if (pendingIndex < messages.size) {
                messages[pendingIndex] = response
            }
            // Notify via ntfy
            val isError = response.text.startsWith("\u26A0\uFE0F") // ⚠️
            if (isError) {
                publisher.send("Chat Error", response.text.take(200), listOf("hermes", "error"))
            }
        }
    }

    /** Called after successful biometric / device-credential auth. */
    fun executePendingCommand() {
        val command = pendingPrivilegedCommand ?: return
        pendingPrivilegedCommand = null
        handleCommand(command)
        val text = "🔒 Privileged command executed"
        addSystem(text)
        publisher.send("Privileged Command", text, listOf("hermes", "secure"))
    }

    /** Called when the user cancels the auth dialog. */
    fun cancelPendingCommand() {
        pendingPrivilegedCommand = null
    }

    // ── Internal ────────────────────────────────────────────────

    private fun addSystem(text: String) {
        messages.add(Message(role = "system", text = text))
    }

    private fun handleCommand(command: SlashCommand) {
        when (command) {
            is SlashCommand.SetModel -> setModel(command.model)
            is SlashCommand.Secure -> { /* handled by executePendingCommand */ }
        }
    }
}
