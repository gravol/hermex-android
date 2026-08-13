package com.hermex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermex.android.feature.chat.ChatScreen
import com.hermex.android.feature.chat.ChatVmsHolder
import com.hermex.android.feature.onboarding.DashboardSetupScreen
import com.hermex.android.feature.sessions.SessionsScreen
import com.hermex.android.feature.settings.SettingsRepository
import com.hermex.android.feature.settings.SettingsScreen
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog
import com.hermex.android.ui.theme.HermexTheme
import com.hermex.android.ui.theme.UiColorOverrides
import com.hermex.android.ui.theme.hexToColor
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Display preferences: UI zoom (dp) + text scale (sp), applied as a
            // density override so every screen picks them up with no per-screen
            // changes. Text scales by zoom × textScale. Accent color (null =
            // follow system/wallpaper) overrides the theme primary.
            val appContext = LocalContext.current.applicationContext
            val settingsRepo = remember { SettingsRepository(appContext) }
            val zoomPercent by settingsRepo.uiZoomPercent.collectAsState(initial = 100)
            val textPercent by settingsRepo.textScalePercent.collectAsState(initial = 100)
            val accentHex by settingsRepo.accentColorHex.collectAsState(initial = null)
            val accentColor = remember(accentHex) {
                accentHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() }
            }
            // Per-part appearance overrides (v0.1.49+) — null = derive from accent
            val uiBgHex by settingsRepo.uiBackgroundHex.collectAsState(initial = null)
            val uiUserBubbleHex by settingsRepo.uiUserBubbleHex.collectAsState(initial = null)
            val uiAssistantBubbleHex by settingsRepo.uiAssistantBubbleHex.collectAsState(initial = null)
            val uiTextHex by settingsRepo.uiTextHex.collectAsState(initial = null)
            val uiMonospace by settingsRepo.uiMonospace.collectAsState(initial = false)
            val uiOverrides = remember(uiBgHex, uiUserBubbleHex, uiAssistantBubbleHex, uiTextHex) {
                UiColorOverrides(
                    background = uiBgHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    userBubble = uiUserBubbleHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    assistantBubble = uiAssistantBubbleHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                    text = uiTextHex?.let { hex -> runCatching { hexToColor(hex) }.getOrNull() },
                )
            }
            HermexTheme(accentColor = accentColor, uiOverrides = uiOverrides, monospace = uiMonospace) {
                val baseDensity = LocalDensity.current
                val scaledDensity = Density(
                    density = baseDensity.density * (zoomPercent / 100f),
                    fontScale = baseDensity.fontScale * (textPercent / 100f),
                )
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    // Activity-scoped: chat ViewModels outlive navigation so turns
                    // keep running when you leave a chat (see ChatVmsHolder).
                    val chatVmsHolder: ChatVmsHolder = viewModel()
                    HermexNavGraph(chatVmsHolder = chatVmsHolder)
                }
            }
        }
    }
}

@Composable
fun HermexNavGraph(chatVmsHolder: ChatVmsHolder) {
    val navController = rememberNavController()
    // Dashboard is the primary path. If not configured, user goes through
    // dashboard-setup flow. Legacy stack cleanup in progress.
    val startDest = when {
        DashboardApiClient.isConfigured -> {
            DebugLog.log("ROUTE", "MainActivity", "startup → home (dashboard configured)")
            "home"
        }
        else -> {
            DebugLog.log("ROUTE", "MainActivity", "startup → dashboard-setup")
            "dashboard-setup"
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable("dashboard-setup") {
            DashboardSetupScreen(
                onDone = {
                    navController.navigate("home") {
                        popUpTo("dashboard-setup") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            SessionsScreen(
                onSessionTap = { session ->
                    val encodedTitle = URLEncoder.encode(session.title ?: session.id, "UTF-8")
                    navController.navigate("chat/${session.id}/$encodedTitle")
                },
                onSettings = {
                    navController.navigate("settings")
                },
                activeSessions = chatVmsHolder.activeSessions,
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "chat/{sessionId}/{title}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
            val title = URLDecoder.decode(encodedTitle, "UTF-8")
            // Chat ViewModels are held at Activity scope (ChatVmsHolder) so the
            // WebSocket survives backing out of the chat — turns keep running
            // in the background instead of being torn down by the server's
            // orphan reaper. Reopening the same session returns the SAME VM
            // with its live state intact.
            val chatViewModel = chatVmsHolder.getOrCreate(sessionId)
            DebugLog.log("ROUTE", "MainActivity", "chat → DashboardChatViewModel (held)")
            ChatScreen(
                sessionId = sessionId,
                sessionTitle = title,
                onBack = { navController.popBackStack() },
                viewModel = chatViewModel,
            )
        }
    }
}
