package com.hermes.chat.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hermes.chat.ChatViewModel
import com.hermes.chat.model.Message
import com.hermes.chat.network.NtfyClient
import com.hermes.chat.ui.screen.ChatScreen
import com.hermes.chat.ui.screen.DevicesScreen
import com.hermes.chat.ui.screen.LogsScreen
import com.hermes.chat.ui.screen.SecurePromptDialog
import com.hermes.chat.ui.screen.SettingsScreen
import com.hermes.chat.ui.theme.HermesChatTheme

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()

    HermesChatTheme(isDarkTheme = chatViewModel.isDarkTheme) {
        Scaffold { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.Chat.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Routes.Chat.route) {
                    ChatScreen(
                        chatState = chatViewModel,
                        onOpenSettings = { navController.navigate(Routes.Settings.route) },
                        onOpenDevices = { navController.navigate(Routes.Devices.route) },
                        onOpenLogs = { navController.navigate(Routes.Logs.route) },
                    )
                }
                composable(Routes.Settings.route) { SettingsScreen(chatViewModel) }
                composable(Routes.Devices.route) { DevicesScreen(chatViewModel) }
                composable(Routes.Logs.route) { LogsScreen() }
            }
        }

        // Secure prompt overlay for privileged commands
        chatViewModel.pendingPrivilegedCommand?.let { command ->
            SecurePromptDialog(
                commandLabel = command::class.simpleName ?: "unknown",
                onAuthenticated = { chatViewModel.executePendingCommand() },
                onDismiss = { chatViewModel.cancelPendingCommand() },
            )
        }

        // ntfy SSE subscription — starts when topic is set, stops on config change / disposal
        val ntfyClient = remember {
            NtfyClient { title, message ->
                chatViewModel.messages.add(
                    Message(role = "system", text = "\uD83D\uDD14 $title — $message")
                )
            }
        }
        DisposableEffect(chatViewModel.ntfyConfig) {
            ntfyClient.start(chatViewModel.ntfyConfig)
            onDispose { ntfyClient.stop() }
        }
    }
}
