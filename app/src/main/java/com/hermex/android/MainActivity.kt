package com.hermex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hermex.android.feature.onboarding.SetupScreen
import com.hermex.android.feature.sessions.SessionsScreen
import com.hermex.core.network.ApiClient
import com.hermex.android.ui.theme.HermexTheme

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
            SessionsScreen()
        }
    }
}
