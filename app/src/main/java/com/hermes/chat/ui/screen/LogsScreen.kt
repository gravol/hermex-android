package com.hermes.chat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hermes.chat.ui.theme.TelegramChatColors

@Composable
fun LogsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelegramChatColors.DarkCanvas),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Logs",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
