package com.hermes.chat

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.hermes.chat.model.AttachmentType
import com.hermes.chat.model.Message
import com.hermes.chat.model.MessageAttachment
import com.hermes.chat.model.ModelType
import com.hermes.chat.model.NetworkMode
import com.hermes.chat.model.NtfyConfig
import com.hermes.chat.model.SettingsBackup
import com.hermes.chat.network.HermesClient
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
import java.io.File

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

    /** User-selected endpoint routing mode. Defaults to Tailscale-first auto. */
    var currentMode: NetworkMode by mutableStateOf(NetworkMode.AUTO)
        private set

    /** User-configured Local/LAN Hermes endpoint URL. */
    var localBaseUrl: String by mutableStateOf(BuildConfig.DEFAULT_LOCAL_BASE_URL)
        private set

    /** User-configured Tailscale Hermes endpoint URL. */
    var awayBaseUrl: String by mutableStateOf(BuildConfig.DEFAULT_TAILSCALE_BASE_URL)
        private set

    /** Actual endpoint currently applied to the HTTP client. */
    var resolvedBaseUrl: String by mutableStateOf("")
        private set

    /** Human-readable endpoint status shown in Settings. */
    var endpointStatus: String by mutableStateOf("Auto prefers Tailscale, then Local")
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
        val conversation = withContext(Dispatchers.IO) {
            messages.toList()
                .filterIndexed { i, m ->
                    i != pendingIndex && m.text != "..." && !m.isSystem
                }
                .map { message -> prepareMessageAttachments(message) }
        }

        val httpClient = client as? HermesOkHttpClient
        val result = retryWithBackoff(retryPolicy) {
            if (httpClient != null) {
                httpClient.streamMessage(conversation) { delta ->
                    withContext(Dispatchers.Main) {
                        if (pendingIndex < messages.size) {
                            val current = messages[pendingIndex]
                            val nextText = if (current.text == "...") delta else current.text + delta
                            messages[pendingIndex] = current.copy(text = nextText)
                        }
                    }
                }
            } else {
                client.sendMessage(conversation)
            }
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

    private suspend fun prepareMessageAttachments(message: Message): Message {
        if (message.attachments.isEmpty()) return message
        var preparedText = message.text
        val preparedAttachments = mutableListOf<MessageAttachment>()

        message.attachments.forEach { attachment ->
            when (attachment.type) {
                AttachmentType.IMAGE -> {
                    preparedAttachments.add(
                        if (!attachment.dataUrl.isNullOrBlank()) attachment
                        else attachment.copy(dataUrl = imageDataUrlOrNull(attachment))
                    )
                }
                AttachmentType.VOICE -> {
                    val transcript = transcribeVoiceAttachment(attachment)
                    preparedText = buildString {
                        append(preparedText)
                        if (isNotBlank()) append("\n\n")
                        append("Voice note transcript:\n")
                        append(transcript)
                    }.trim()
                }
                AttachmentType.FILE -> preparedAttachments.add(attachment)
            }
        }

        return message.copy(text = preparedText, attachments = preparedAttachments)
    }

    private suspend fun transcribeVoiceAttachment(attachment: MessageAttachment): String {
        val httpClient = client as? HermesOkHttpClient
            ?: return "[Could not transcribe voice note: Hermes HTTP client unavailable.]"
        val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull()
            ?: return "[Could not transcribe voice note: invalid audio URI.]"
        val path = uri.path
            ?: return "[Could not transcribe voice note: missing audio path.]"
        val file = File(path)
        val result = httpClient.transcribeAudio(file)
        return result.getOrElse { error ->
            "[Could not transcribe voice note: ${error.message ?: "unknown error"}.]"
        }
    }

    private fun imageDataUrlOrNull(attachment: MessageAttachment): String? {
        val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull() ?: return null
        val resolver = getApplication<Application>().contentResolver
        val mimeType = attachment.mimeType
            ?: resolver.getType(uri)
            ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) return null

        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val maxBytes = 4 * 1024 * 1024
                if (bytes.size > maxBytes) return null
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mimeType;base64,$encoded"
            }
        }.getOrNull()
    }

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

    /** Re-resolve endpoint routing and update the client endpoint. */
    fun refreshNetworkMode() {
        applyMode()
    }

    fun setNetworkMode(mode: NetworkMode) {
        if (mode == currentMode) return
        currentMode = mode
        applyMode()
    }

    /** Set the Local/LAN Hermes endpoint URL and re-apply routing. */
    fun setLocalUrl(url: String) {
        localBaseUrl = url
        applyMode()
        refreshModels(silent = true)
    }

    /** Set the Tailscale Hermes endpoint URL and re-apply routing. */
    fun setAwayUrl(url: String) {
        awayBaseUrl = url
        applyMode()
        refreshModels(silent = true)
    }

    /** Update the Hermes client's base URL to match the current mode. */
    private fun applyMode() {
        scope.launch {
            val local = localBaseUrl.trim()
            val tailscale = awayBaseUrl.trim()
            val resolved = when (currentMode) {
                NetworkMode.TAILSCALE -> {
                    endpointStatus = if (tailscale.isBlank()) "⚠️ Tailscale selected but URL is blank" else "Using Tailscale endpoint"
                    tailscale
                }
                NetworkMode.LOCAL -> {
                    endpointStatus = if (local.isBlank()) "⚠️ Local selected but URL is blank" else "Using Local endpoint"
                    local
                }
                NetworkMode.AUTO -> {
                    endpointStatus = "Checking Tailscale, then Local..."
                    val tailscaleReachable = withContext(Dispatchers.IO) {
                        NetworkModeDetector.canReachEndpoint(tailscale)
                    }
                    if (tailscaleReachable) {
                        endpointStatus = "✅ Auto selected Tailscale"
                        tailscale
                    } else {
                        val localReachable = withContext(Dispatchers.IO) {
                            NetworkModeDetector.canReachEndpoint(local)
                        }
                        if (localReachable) {
                            endpointStatus = "✅ Auto selected Local"
                            local
                        } else {
                            val fallback = tailscale.ifBlank { local }
                            endpointStatus = if (fallback.isBlank()) {
                                "⚠️ Add a Tailscale or Local endpoint"
                            } else {
                                "⚠️ Neither endpoint probed reachable; using configured fallback"
                            }
                            fallback
                        }
                    }
                }
            }
            resolvedBaseUrl = resolved
            (client as? HermesOkHttpClient)?.baseUrl = resolved
            refreshModels(silent = true)
            if (resolved.isNotBlank()) {
                addSystem("\uD83D\uDCE1 Endpoint: **${currentMode.displayName}**")
            }
        }
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

    /** Test endpoint + API key without spending a chat request. */
    fun testConnection() {
        if (isTestingConnection) return
        isTestingConnection = true
        connectionTestResult = null
        addSystem("\uD83D\uDD04 Testing API key...")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val httpClient = client as? HermesOkHttpClient
                    ?: return@withContext "\u26A0\uFE0F Client not available"
                httpClient.testApiKey()
            }
            connectionTestResult = result
            isTestingConnection = false
            messages.removeAll { it.text == "\uD83D\uDD04 Testing API key..." }
            addSystem(result)
        }
    }

    // ── Settings backup/restore ────────────────────────────────

    /** Export current settings (excluding API token) as a JSON string. */
    fun exportSettings(): String {
        val backup = SettingsBackup(
            model = selectedModelId,
            networkMode = currentMode.name,
            localUrl = localBaseUrl,
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
        // Apply endpoints/routing
        currentMode = runCatching { NetworkMode.valueOf(backup.networkMode) }.getOrDefault(NetworkMode.AUTO)
        if (backup.localUrl != localBaseUrl) {
            localBaseUrl = backup.localUrl
        }
        if (backup.awayUrl != awayBaseUrl) {
            awayBaseUrl = backup.awayUrl
        }
        applyMode()
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
