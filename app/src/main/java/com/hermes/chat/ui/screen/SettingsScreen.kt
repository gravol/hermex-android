package com.hermes.chat.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hermes.chat.ChatState
import com.hermes.chat.model.ModelType
import com.hermes.chat.model.NetworkMode

@Composable
fun SettingsScreen(chatState: ChatState) {
    var ntfyInput by remember { mutableStateOf(chatState.ntfyConfig.topic) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        ModelType.entries.forEach { model ->
            val selected = chatState.currentModel == model
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = selected,
                        onClick = { chatState.setModel(model) },
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
                Text(
                    text = model.displayName,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Current: ${chatState.currentModel.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

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
            modifier = Modifier.fillMaxWidth(),
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
    }
}
