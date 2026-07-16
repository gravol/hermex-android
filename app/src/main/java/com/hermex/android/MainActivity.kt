package com.hermex.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hermex.android.feature.onboarding.OnboardingScreen
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

    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") {
            OnboardingScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomePlaceholder()
        }
    }
}

@Composable
fun HomePlaceholder() {
    Scaffold {
        Box(
            modifier = Modifier.fillMaxSize().padding(it),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Logged in! Sessions coming soon.",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
