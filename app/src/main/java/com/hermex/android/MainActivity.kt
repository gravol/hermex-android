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
import com.hermex.android.feature.chat.DashboardChatViewModel
import com.hermex.android.feature.onboarding.DashboardSetupScreen
import com.hermex.android.feature.sessions.SessionsScreen
import com.hermex.android.feature.settings.SettingsRepository
import com.hermex.android.feature.settings.SettingsScreen
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog
import com.hermex.android.ui.theme.HermexTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermexTheme {
                // Display preferences: UI zoom (dp) + text scale (sp), applied
                // as a density override so every screen picks them up with no
                // per-screen changes. Text scales by zoom × textScale.
                val appContext = LocalContext.current.applicationContext
                val settingsRepo = remember { SettingsRepository(appContext) }
                val zoomPercent by settingsRepo.uiZoomPercent.collectAsState(initial = 100)
                val textPercent by settingsRepo.textScalePercent.collectAsState(initial = 100)
                val baseDensity = LocalDensity.current
                val scaledDensity = Density(
                    density = baseDensity.density * (zoomPercent / 100f),
                    fontScale = baseDensity.fontScale * (textPercent / 100f),
                )
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    HermexNavGraph()
                }
            }
        }
    }
}

@Composable
fun HermexNavGraph() {
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
            val chatViewModel = viewModel<DashboardChatViewModel>()
            DebugLog.log("ROUTE", "MainActivity", "chat → DashboardChatViewModel")
            ChatScreen(
                sessionId = sessionId,
                sessionTitle = title,
                onBack = { navController.popBackStack() },
                viewModel = chatViewModel,
            )
        }
    }
}
