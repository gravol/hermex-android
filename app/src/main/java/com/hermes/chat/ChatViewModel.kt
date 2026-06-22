package com.hermes.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.hermes.chat.model.Message
import com.hermes.chat.model.MessageAttachment
import com.hermes.chat.model.ModelType
import com.hermes.chat.model.NetworkMode
import com.hermes.chat.model.NtfyConfig
import com.hermes.chat.model.SettingsBackup
import com.hermes.chat.network.HermesClient
import com.hermes.chat.network.HermesEndpointResolver
import com.hermes.chat.network.HermesOkHttpClient
import com.hermes.chat.network.NetworkModeDetector
import com.hermes.chat.network.NtfyPublisher
import com.hermes.chat.network.RetryPolicy
import com.hermes.chat.network.retryWithBackoff
import com.hermes.chat.storage.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Hermes Chat screen.
 *
 * Holds chat message state, current model, ntfy config, pending privileged commands,
 * network mode / away endpoint, auth token (persisted in SecureTokenStore),
 * and drives send / slash-command flow.
 *
 * Survives configuration changes. Use via viewModel() in Compose.
 */
class ChatViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val client: HermesClient = HermesOkHttpClient()

    val messages = mutableStateListOf<Message>()

    var currentModel: ModelType by mutableStateOf(ModelType.FLASH)
        private set

    var selectedModelId: String by mutableStateOf(ModelType.FLASH.apiName)
        private set

    val availableModelIds = mutableStateListOf<String>()

    var isRefreshingModels: Boolean by mutableStateOf(false)
        private set

    var modelRefreshStatus: String? by mutableStateOf(null)
        private set

    val displayedModelIds: List<String>
        get() = availableModelIds.ifEmpty { ModelType.entries.map { it.apiName } }

    val selectedModelLabel: String
        get() = ModelType.entries.find { it.apiName == selectedModelId }?.displayName ?: selectedModelId

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

    // ── Theme ─────────────────────────────────────────────────

    /** True = dark scheme (night mode on), False = light scheme. */
    var isDarkTheme: Boolean by mutableStateOf(true)

    // ── Retry / offline queue ──────────────────────────────────

    /** Retry policy for Hermes API calls. */
    var retryPolicy: RetryPolicy = RetryPolicy()
        private set

    /** Indices into [messages] of assistant messages that failed and await retry. */
    val failedMessageIndices = mutableStateListOf<Int>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Publisher is re-reads [ntfyConfig] on each send via the provider lambda. */
    private val publisher = NtfyPublisher { ntfyConfig }

    /** Encrypted token store backed by Android Keystore. */
    private val tokenStore = SecureTokenStore(application)

    // ── Public API ──────────────────────────────────────────────

    /** Switch model and show a confirmation message. */
    fun setModel(model: ModelType) {
        setModelId(model.apiName)
    }

    /** Switch to any model ID advertised by Hermes/OpenAI-compatible `/v1/models`. */
    fun setModelId(modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isBlank() || trimmed == selectedModelId) return
        selectedModelId = trimmed
        ModelType.entries.find { it.apiName == trimmed }?.let { currentModel = it }
        (client as? HermesOkHttpClient)?.model = trimmed
        addSystem("\u2705 Switched to **${selectedModelLabel}**")
        publisher.send("Model Changed", "Switched to $selectedModelLabel", listOf("hermes", "settings"))
    }

    /** Refresh model list from Hermes `/v1/models`, falling back to built-in defaults on failure. */
    fun refreshModels(silent: Boolean = false) {
        if (isRefreshingModels) return
        isRefreshingModels = true
        if (!silent) modelRefreshStatus = "Refreshing models..."

        scope.launch {
            val ids = withContext(Dispatchers.IO) { client.listModels() }
                .distinct()
                .sorted()
            if (ids.isNotEmpty()) {
                availableModelIds.clear()
                availableModelIds.addAll(ids)
                if (selectedModelId !in ids) {
                    val first = ids.first()
                    selectedModelId = first
                    (client as? HermesOkHttpClient)?.model = first
                }
                modelRefreshStatus = "✅ ${ids.size} model(s) detected"
                if (!silent) addSystem(modelRefreshStatus!!)
            } else {
                modelRefreshStatus = "⚠️ Could not detect models; using built-in defaults"
                if (!silent) addSystem(modelRefreshStatus!!)
            }
            isRefreshingModels = false
        }
    }

    /** Toggle between dark and light theme. */
    fun toggleDarkTheme() {
        isDarkTheme = !isDarkTheme
    }

    /** Clear all messages from the chat. */
    fun clearMessages() {
        val count = messages.size
        messages.clear()
        failedMessageIndices.clear()
        addSystem("\uD83D\uDDD1\uFE0F Cleared $count message(s)")
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
            performSend(pendingIndex)
        }
    }

    /**
     * Perform the actual API send with retry/backoff.
     * Called from [sendMessage] and [retryMessage].
     */
    private suspend fun performSend(pendingIndex: Int) {
        val conversation = messages.toList().filterIndexed { i, m ->
            i != pendingIndex && m.text != "..." && !m.isSystem
        }

        val result = retryWithBackoff(retryPolicy) {
            client.sendMessage(conversation)
        }

        result.onSuccess { response ->
            if (pendingIndex < messages.size) {
                messages[pendingIndex] = response
            }
            failedMessageIndices.remove(pendingIndex)
            // Notify via ntfy
            val isError = response.text.startsWith("\u26A0\uFE0F") // ⚠️
            if (isError) {
                publisher.send("Chat Error", response.text.take(200), listOf("hermes", "error"))
            }
        }.onFailure { exception ->
            if (pendingIndex < messages.size) {
                val text = "\u26A0\uFE0F Failed — tap to retry"
                messages[pendingIndex] = Message(role = "assistant", text = text)
            }
            if (!failedMessageIndices.contains(pendingIndex)) {
                failedMessageIndices.add(pendingIndex)
            }
            publisher.send("Chat Error", "Message queued for retry: ${exception.message?.take(100) ?: "unknown"}",
                listOf("hermes", "error"))
        }
    }

    /** Retry a single failed message at [failedIndex]. */
    fun retryMessage(failedIndex: Int) {
        if (failedIndex < 0 || failedIndex >= messages.size) return
        // Reset to pending
        messages[failedIndex] = Message(role = "assistant", text = "...")
        scope.launch {
            performSend(failedIndex)
        }
    }

    /** Retry every message currently in the failed queue. */
    fun retryAllFailed() {
        val snapshot = failedMessageIndices.toList()
        snapshot.forEach { retryMessage(it) }
    }

    /** Called after successful biometric / device-credential auth. */
    fun executePendingCommand() {
        val command = pendingPrivilegedCommand ?: return
        pendingPrivilegedCommand = null
        handleCommand(command)
        val text = "\uD83D\uDD12 Privileged command executed"
        addSystem(text)
        publisher.send("Privileged Command", text, listOf("hermes", "secure"))
    }

    /** Called when the user cancels the auth dialog. */
    fun cancelPendingCommand() {
        pendingPrivilegedCommand = null
    }

    // ── Internal ────────────────────────────────────────────────

    fun addSystem(text: String) {
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
            refreshModels(silent = true)
            addSystem("\uD83D\uDCE1 Network: **${mode.displayName}**")
        }
    }

    /** Set the away (Tailscale) Hermes endpoint URL and re-apply routing. */
    fun setAwayUrl(url: String) {
        awayBaseUrl = url
        applyMode()
        refreshModels(silent = true)
    }

    /** Update the Hermes client's base URL to match the current mode. */
    private fun applyMode() {
        val url = HermesEndpointResolver.resolve(currentMode, awayBaseUrl)
        (client as? HermesOkHttpClient)?.baseUrl = url
    }

    init {
        // Detect network mode on construction (non-blocking)
        (client as? HermesOkHttpClient)?.model = selectedModelId
        refreshNetworkMode()
        // Load persisted auth token and apply to client
        val stored = tokenStore.loadToken()
        if (stored.isNotBlank()) {
            authToken = stored
            (client as? HermesOkHttpClient)?.authToken = stored
        }
    }

    // ── Auth helpers ────────────────────────────────────────────

    /** Set the auth token, persist it, and push to the HTTP client. */
    fun updateAuthToken(token: String) {
        authToken = token
        tokenStore.saveToken(token)
        (client as? HermesOkHttpClient)?.authToken = token
        refreshModels(silent = true)
    }

    /** Wipe the stored auth token from secure storage and the client. */
    fun clearAuthToken() {
        authToken = ""
        tokenStore.clearToken()
        (client as? HermesOkHttpClient)?.authToken = ""
        addSystem("\uD83D\uDDD1\uFE0F Auth token cleared")
    }

    /** Send a minimal test message to verify connectivity and auth. */
    fun testConnection() {
        if (isTestingConnection) return
        isTestingConnection = true
        connectionTestResult = null
        addSystem("\uD83D\uDD04 Testing connection...")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val httpClient = client as? HermesOkHttpClient
                    if (httpClient == null) return@withContext "\u26A0\uFE0F Client not available"

                    // Minimal ping
                    val testMsg = Message(role = "user", text = "ping")
                    val response = httpClient.sendMessage(listOf(testMsg))

                    if (response.text.startsWith("\u26A0\uFE0F")) {
                        val code = response.text.substringAfter("(").substringBefore(")")
                        if (code.contains("401") || code.contains("403"))
                            "\u274C ${response.text}"
                        else
                            "\u2705 Server reached. ${response.text}"
                    } else {
                        "\u2705 Connected and authenticated. Got response."
                    }
                } catch (e: Exception) {
                    "\u274C Connection failed: ${e.message ?: "unknown error"}"
                }
            }
            connectionTestResult = result
            isTestingConnection = false
            // Remove the "Testing..." message and replace with result
            messages.removeAll { it.text == "\uD83D\uDD04 Testing connection..." }
            addSystem(result)
        }
    }

    // ── Settings backup/restore ────────────────────────────────

    /** Export current settings (excluding API token) as a JSON string. */
    fun exportSettings(): String {
        val backup = SettingsBackup(
            model = selectedModelId,
            awayUrl = awayBaseUrl,
            ntfyTopic = ntfyConfig.topic,
            clerkMacAddress = clerkMacAddress,
            clerkIpAddress = clerkIpAddress,
        )
        return backup.toJson()
    }

    /** Import settings from a JSON string. Returns true on success. */
    fun importSettings(json: String): Boolean {
        val backup = SettingsBackup.fromJson(json) ?: return false
        // Apply model
        val backupModelId = ModelType.entries.find { it.displayName == backup.model }?.apiName ?: backup.model
        if (backupModelId.isNotBlank() && backupModelId != selectedModelId) {
            setModelId(backupModelId)
        }
        // Apply away URL
        if (backup.awayUrl != awayBaseUrl) {
            awayBaseUrl = backup.awayUrl
            (client as? HermesOkHttpClient)?.let {
                it.baseUrl = HermesEndpointResolver.resolve(currentMode, awayBaseUrl)
            }
        }
        // Apply ntfy topic
        if (backup.ntfyTopic != ntfyConfig.topic) {
            ntfyConfig = ntfyConfig.copy(topic = backup.ntfyTopic)
        }
        // Apply clerk config
        if (backup.clerkMacAddress != clerkMacAddress) {
            clerkMacAddress = backup.clerkMacAddress
        }
        if (backup.clerkIpAddress != clerkIpAddress) {
            clerkIpAddress = backup.clerkIpAddress
        }
        addSystem("\u2705 Settings restored from backup")
        return true
    }
}
