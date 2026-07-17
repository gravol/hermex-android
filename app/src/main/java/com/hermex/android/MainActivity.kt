package com.hermex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermex.android.feature.chat.ChatScreen
import com.hermex.android.feature.chat.ChatViewModel
import com.hermex.android.feature.chat.DashboardChatViewModel
import com.hermex.android.feature.onboarding.DashboardSetupScreen
import com.hermex.android.feature.onboarding.SetupScreen
import com.hermex.android.feature.sessions.SessionsScreen
import com.hermex.android.feature.settings.SettingsScreen
import com.hermex.core.network.ApiClient
import com.hermex.core.network.DashboardApiClient
import com.hermex.android.ui.theme.HermexTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermexTheme {
                HermexNavGraph()
            }
        }
    }
}

@Composable
fun HermexNavGraph() {
    val navController = rememberNavController()
    // Prefer dashboard auth; fall back to legacy API server setup
    val startDest = when {
        DashboardApiClient.isConfigured -> "home"
        ApiClient.isConfigured -> "home"
        else -> "dashboard-setup"
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
        composable("setup") {
            SetupScreen(
                onDone = {
                    navController.navigate("home") {
                        popUpTo("setup") { inclusive = true }
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
            val chatViewModel = if (DashboardApiClient.isConfigured) {
                viewModel<DashboardChatViewModel>()
            } else {
                viewModel<ChatViewModel>()
            }
            ChatScreen(
                sessionId = sessionId,
                sessionTitle = title,
                onBack = { navController.popBackStack() },
                viewModel = chatViewModel,
            )
        }
    }
}
