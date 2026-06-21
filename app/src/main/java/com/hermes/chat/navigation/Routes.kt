package com.hermes.chat.navigation

/**
 * Type-safe navigation routes for Hermes Chat.
 *
 * Each route maps to one screen in the bottom-nav layout.
 * Usage:  NavHost(startDestination = Routes.Chat.route)
 *         composable(Routes.Chat.route) { ChatScreen(...) }
 */
sealed class Routes(val route: String) {
    data object Chat : Routes("chat")
    data object Settings : Routes("settings")
    data object Devices : Routes("devices")
    data object Logs : Routes("logs")
}
