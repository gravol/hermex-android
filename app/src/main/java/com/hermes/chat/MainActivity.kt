package com.hermes.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hermes.chat.navigation.AppNavigation
import com.hermes.chat.storage.SecureTokenStore
import com.hermes.chat.ui.theme.HermesChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = SecureTokenStore(applicationContext)
        enableEdgeToEdge()
        setContent {
            HermesChatTheme {
                AppNavigation(tokenStore = tokenStore)
            }
        }
    }
}
