package com.hermes.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1A1A2E)
private val DarkNavy = Color(0xFF0F1421)
private val SurfaceNavy = Color(0xFF16213E)
private val Amber = Color(0xFFFFBF00)
private val AmberLight = Color(0xFFFFD54F)
private val OnNavy = Color(0xFFE0E0E0)
private val OnAmber = Color(0xFF1A1A2E)

private val HermesDarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = AmberLight,
    secondary = AmberLight,
    onSecondary = OnAmber,
    background = DarkNavy,
    onBackground = OnNavy,
    surface = Navy,
    onSurface = OnNavy,
    surfaceVariant = SurfaceNavy,
    onSurfaceVariant = OnNavy,
    outline = Amber.copy(alpha = 0.4f),
)

@Composable
fun HermesChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HermesDarkScheme,
        content = content,
    )
}
