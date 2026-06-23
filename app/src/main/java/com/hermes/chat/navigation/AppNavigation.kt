package com.hermes.chat.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.hermes.chat.ui.theme.TelegramChatColors

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.Chat.route, "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    BottomNavItem(Routes.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    BottomNavItem(Routes.Devices.route, "Devices", Icons.Filled.Construction, Icons.Outlined.Construction),
    BottomNavItem(Routes.Logs.route, "Logs", Icons.Filled.Terminal, Icons.Outlined.Terminal),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val hideBottomBar = currentRoute == Routes.Chat.route && isKeyboardVisible
    val chatViewModel: ChatViewModel = viewModel()

    HermesChatTheme(isDarkTheme = chatViewModel.isDarkTheme) {
        Scaffold(
            bottomBar = {
            if (!hideBottomBar) {
                NavigationBar(
                    containerColor = TelegramChatColors.DarkTopBar,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Chat.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Chat.route) { ChatScreen(chatViewModel) }
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
