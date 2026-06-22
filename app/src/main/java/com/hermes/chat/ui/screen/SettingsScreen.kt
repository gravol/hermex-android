package com.hermes.chat.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hermes.chat.ChatViewModel
import com.hermes.chat.model.NetworkMode
import com.hermes.chat.ui.theme.TelegramChatColors

@Composable
fun SettingsScreen(chatState: ChatViewModel) {
    var ntfyInput by remember { mutableStateOf(chatState.ntfyConfig.topic) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelegramChatColors.DarkCanvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // ── Model section ──────────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Model",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        chatState.displayedModelIds.forEach { modelId ->
            val selected = chatState.selectedModelId == modelId
            val label = com.hermes.chat.model.ModelType.entries.find { it.apiName == modelId }?.displayName ?: modelId
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = selected,
                        onClick = { chatState.setModelId(modelId) },
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    ),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (label != modelId) {
                        Text(
                            text = modelId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Current: ${chatState.selectedModelLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (chatState.isRefreshingModels) "Refreshing..." else "Refresh models",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = !chatState.isRefreshingModels) { chatState.refreshModels() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        chatState.modelRefreshStatus?.let { status ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }

        // ── Night mode toggle ──────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Night mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(
                checked = chatState.isDarkTheme,
                onCheckedChange = { chatState.toggleDarkTheme() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                ),
            )
        }

        // ── Hermes connection section ──────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Hermes Connection",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        var authTokenInput by remember(chatState.authToken) { mutableStateOf(chatState.authToken) }

        // Auth token
        OutlinedTextField(
            value = authTokenInput,
            onValueChange = { authTokenInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "apiTokenField" },
            label = { Text("API Token") },
            placeholder = { Text("sk-... or Bearer token") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { chatState.updateAuthToken(authTokenInput.trim()) },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (chatState.authToken.isNotBlank()) "✅ Token set" else "Leave blank for anonymous access",
            style = MaterialTheme.typography.bodySmall,
            color = if (chatState.authToken.isNotBlank())
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(8.dp))

        // Test connection button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (chatState.isTestingConnection) "⏳ Testing..." else "Test Connection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = !chatState.isTestingConnection) { chatState.testConnection() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            chatState.connectionTestResult?.let { result ->
                val isError = result.startsWith("❌") || result.contains("failed", ignoreCase = true)
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Clear token action (only shown when token is set)
        if (chatState.authToken.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Clear token",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable { chatState.clearAuthToken() },
            )
        }

        // ── ntfy section ──────────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Notifications (ntfy.sh)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = ntfyInput,
            onValueChange = { ntfyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Topic") },
            placeholder = { Text("e.g. hermes-chat-alerts") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { chatState.setNtfyTopic(ntfyInput.trim()) },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = if (chatState.ntfyConfig.isConfigured)
                "✅ Publishing to: ${chatState.ntfyConfig.topic}"
            else
                "Enter a topic above to enable push notifications",
            style = MaterialTheme.typography.bodySmall,
            color = if (chatState.ntfyConfig.isConfigured)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        // ── Network mode section ──────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Network",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        // Mode indicator
        val modeColor = when (chatState.currentMode) {
            NetworkMode.HOME -> MaterialTheme.colorScheme.primary
            NetworkMode.AWAY -> MaterialTheme.colorScheme.error
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "● ${chatState.currentMode.displayName}",
                style = MaterialTheme.typography.bodyLarge,
                color = modeColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Re-check",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { chatState.refreshNetworkMode() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Away (Tailscale) endpoint field
        var awayUrlInput by remember(chatState.awayBaseUrl) { mutableStateOf(chatState.awayBaseUrl) }
        OutlinedTextField(
            value = awayUrlInput,
            onValueChange = { awayUrlInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Away Endpoint URL") },
            placeholder = { Text("https://tailscale-ip:8080/v1/chat/completions") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { chatState.setAwayUrl(awayUrlInput.trim()) },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if(chatState.currentMode == NetworkMode.AWAY && chatState.awayBaseUrl.isBlank())
                "\u26A0\uFE0F Away mode active but endpoint URL is blank"
            else if(chatState.awayBaseUrl.isNotBlank())
                "\u2705 Away endpoint set: ${chatState.awayBaseUrl}"
            else
                "Set only if connecting over Tailscale / WAN",
            style = MaterialTheme.typography.bodySmall,
            color = if(chatState.currentMode == NetworkMode.AWAY && chatState.awayBaseUrl.isBlank())
                MaterialTheme.colorScheme.error
            else if(chatState.awayBaseUrl.isNotBlank())
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        // ── Clerk device section ───────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Clerk Device",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        var macInput by remember(chatState.clerkMacAddress) { mutableStateOf(chatState.clerkMacAddress) }
        var ipInput by remember(chatState.clerkIpAddress) { mutableStateOf(chatState.clerkIpAddress) }

        OutlinedTextField(
            value = macInput,
            onValueChange = { macInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("MAC Address") },
            placeholder = { Text("e.g. AA:BB:CC:DD:EE:FF") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP Address") },
            placeholder = { Text("e.g. 192.168.1.100") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    chatState.clerkMacAddress = macInput.trim()
                    chatState.clerkIpAddress = ipInput.trim()
                },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (chatState.clerkIpAddress.isNotBlank())
                "✅ Clerk configured — ${chatState.clerkIpAddress}"
            else
                "Enter MAC and IP to enable WoL and status checks",
            style = MaterialTheme.typography.bodySmall,
            color = if (chatState.clerkIpAddress.isNotBlank())
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        // ── Backup & Restore section ──────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Backup & Restore",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Backup button
            val backupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                uri?.let {
                    val json = chatState.exportSettings()
                    try {
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                        chatState.addSystem("\u2705 Settings backed up")
                    } catch (e: Exception) {
                        chatState.addSystem("\u274C Backup failed: ${e.message?.take(100)}")
                    }
                }
            }
            Text(
                text = "Backup settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { backupLauncher.launch("hermes-chat-settings.json") }
                    .padding(vertical = 8.dp),
            )

            Spacer(Modifier.weight(1f))

            // Restore button
            val restoreLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let {
                    try {
                        val json = context.contentResolver.openInputStream(it)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).readText()
                        } ?: return@let
                        if (chatState.importSettings(json)) {
                            // no-op; system message is added by importSettings
                        } else {
                            chatState.addSystem("\u274C Restore failed: invalid file")
                        }
                    } catch (e: Exception) {
                        chatState.addSystem("\u274C Restore failed: ${e.message?.take(100)}")
                    }
                }
            }
            Text(
                text = "Restore settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { restoreLauncher.launch(arrayOf("application/json")) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
