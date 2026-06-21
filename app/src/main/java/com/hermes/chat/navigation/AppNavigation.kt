package com.hermes.chat.navigation

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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hermes.chat.ChatState
import com.hermes.chat.model.Message
import com.hermes.chat.network.NtfyClient
import com.hermes.chat.ui.screen.ChatScreen
import com.hermes.chat.ui.screen.DevicesScreen
import com.hermes.chat.ui.screen.LogsScreen
import com.hermes.chat.ui.screen.SecurePromptDialog
import com.hermes.chat.ui.screen.SettingsScreen

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem("chat", "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    BottomNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    BottomNavItem("devices", "Devices", Icons.Filled.Construction, Icons.Outlined.Construction),
    BottomNavItem("logs", "Logs", Icons.Filled.Terminal, Icons.Outlined.Terminal),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val chatState = remember { ChatState() }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("chat") { ChatScreen(chatState) }
            composable("settings") { SettingsScreen(chatState) }
            composable("devices") { DevicesScreen() }
            composable("logs") { LogsScreen() }
        }
    }

    // Secure prompt overlay for privileged commands
    chatState.pendingPrivilegedCommand?.let { command ->
        SecurePromptDialog(
            commandLabel = command::class.simpleName ?: "unknown",
            onAuthenticated = { chatState.executePendingCommand() },
            onDismiss = { chatState.cancelPendingCommand() },
        )
    }

    // ntfy SSE subscription — starts when topic is set, stops on config change / disposal
    val ntfyClient = remember {
        NtfyClient { title, message ->
            chatState.messages.add(
                Message(role = "system", text = "\uD83D\uDD14 $title — $message")
            )
        }
    }
    DisposableEffect(chatState.ntfyConfig) {
        ntfyClient.start(chatState.ntfyConfig)
        onDispose { ntfyClient.stop() }
    }
}
