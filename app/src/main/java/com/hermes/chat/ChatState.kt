package com.hermes.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.hermes.chat.model.Message
import com.hermes.chat.model.MessageAttachment
import com.hermes.chat.model.ModelType
import com.hermes.chat.model.NetworkMode
import com.hermes.chat.model.NtfyConfig
import com.hermes.chat.network.HermesClient
import com.hermes.chat.network.HermesEndpointResolver
import com.hermes.chat.network.HermesOkHttpClient
import com.hermes.chat.network.NetworkModeDetector
import com.hermes.chat.network.NtfyPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds chat message state, current model, ntfy config, pending privileged commands,
 * network mode / away endpoint, and drives send / slash-command flow.
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

    // ── Network mode (home / away routing) ─────────────────────

    /** Current detected network mode. */
    var currentMode: NetworkMode by mutableStateOf(NetworkMode.HOME)
        private set

    /** User-configured away (Tailscale) Hermes endpoint URL. */
    var awayBaseUrl: String by mutableStateOf("")
        private set

    // ── Hermes auth ────────────────────────────────────────────

    /** Bearer token sent with every request. */
    var authToken: String by mutableStateOf("")
        private set

    /** True while a connection test is running. */
    var isTestingConnection: Boolean by mutableStateOf(false)
        private set

    /** Result of the last connection test (null = not tested). */
    var connectionTestResult: String? by mutableStateOf(null)
        private set

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
    fun sendMessage(text: String, attachments: List<MessageAttachment> = emptyList()) {
        if (text.isBlank() && attachments.isEmpty()) return

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
        val userMsg = Message(role = "user", text = text.trim(), attachments = attachments)
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

    // ── Network mode helpers ───────────────────────────────────

    /** Detect current network mode and update the client endpoint. */
    fun refreshNetworkMode() {
        scope.launch {
            val mode = withContext(Dispatchers.IO) {
                NetworkModeDetector.detect()
            }
            currentMode = mode
            applyMode()
            addSystem("📡 Network: **${mode.displayName}**")
        }
    }

    /** Set the away (Tailscale) Hermes endpoint URL and re-apply routing. */
    fun setAwayUrl(url: String) {
        awayBaseUrl = url
        applyMode()
    }

    /** Update the Hermes client's base URL to match the current mode. */
    private fun applyMode() {
        val url = HermesEndpointResolver.resolve(currentMode, awayBaseUrl)
        (client as? HermesOkHttpClient)?.baseUrl = url
    }

    init {
        // Detect network mode on construction (non-blocking)
        refreshNetworkMode()
    }

    // ── Auth helpers ────────────────────────────────────────────

    /** Set the auth token and push to the HTTP client. */
    fun updateAuthToken(token: String) {
        authToken = token
        (client as? HermesOkHttpClient)?.authToken = token
    }

    /** Send a minimal test message to verify connectivity and auth. */
    fun testConnection() {
        if (isTestingConnection) return
        isTestingConnection = true
        connectionTestResult = null
        addSystem("🔄 Testing connection...")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val httpClient = client as? HermesOkHttpClient
                    if (httpClient == null) return@withContext "⚠️ Client not available"

                    // Minimal ping — empty messages list, expect 422 or 400 which means connected
                    val testMsg = Message(role = "user", text = "ping")
                    val response = httpClient.sendMessage(listOf(testMsg))

                    if (response.text.startsWith("⚠️")) {
                        // Still counts as "reached server" for auth check
                        val code = response.text.substringAfter("(").substringBefore(")")
                        if (code.contains("401") || code.contains("403"))
                            "❌ ${response.text}"
                        else
                            "✅ Server reached. ${response.text}"
                    } else {
                        "✅ Connected and authenticated. Got response."
                    }
                } catch (e: Exception) {
                    "❌ Connection failed: ${e.message ?: "unknown error"}"
                }
            }
            connectionTestResult = result
            isTestingConnection = false
            // Remove the "Testing..." message and replace with result
            messages.removeAll { it.text == "🔄 Testing connection..." }
            addSystem(result)
        }
    }
}
