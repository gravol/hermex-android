package com.hermex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermex.android.feature.chat.ChatScreen
import com.hermex.android.feature.onboarding.SetupScreen
import com.hermex.android.feature.sessions.SessionsScreen
import com.hermex.core.network.ApiClient
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
    val startDest = if (ApiClient.isConfigured) "home" else "setup"

    NavHost(navController = navController, startDestination = startDest) {
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
                }
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
            ChatScreen(
                sessionId = sessionId,
                sessionTitle = title,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
