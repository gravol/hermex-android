package com.hermes.chat.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.chat.ChatState
import com.hermes.chat.model.DeviceState
import com.hermes.chat.network.DeviceStatusChecker
import com.hermes.chat.network.WoLClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(chatState: ChatState) {
    var clerkState by remember { mutableStateOf(DeviceState.UNKNOWN) }
    val scope = rememberCoroutineScope()

    // Poll clerk status every 15 seconds
    LaunchedEffect(chatState.clerkIpAddress) {
        while (true) {
            clerkState = DeviceStatusChecker.checkStatus(chatState.clerkIpAddress)
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Devices",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        DeviceCard(
            name = "Clerk",
            macAddress = chatState.clerkMacAddress.ifBlank { "—" },
            ipAddress = chatState.clerkIpAddress.ifBlank { "—" },
            state = clerkState,
            onWake = {
                scope.launch {
                    WoLClient.sendWakeOnLan(chatState.clerkMacAddress)
                    // Poll once more right after sending WoL
                    clerkState = DeviceStatusChecker.checkStatus(chatState.clerkIpAddress)
                }
            },
        )
    }
}

@Composable
private fun DeviceCard(
    name: String,
    macAddress: String,
    ipAddress: String,
    state: DeviceState,
    onWake: () -> Unit,
) {
    val stateColor = when (state) {
        DeviceState.AWAKE -> MaterialTheme.colorScheme.primary
        DeviceState.OFF -> MaterialTheme.colorScheme.error
        DeviceState.UNKNOWN -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    val stateLabel = when (state) {
        DeviceState.AWAKE -> "● Awake"
        DeviceState.OFF -> "● Off"
        DeviceState.UNKNOWN -> "● Unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = stateColor,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "MAC: $macAddress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = "IP: $ipAddress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            if (state != DeviceState.AWAKE) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onWake,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Wake")
                }
            }
        }
    }
}
