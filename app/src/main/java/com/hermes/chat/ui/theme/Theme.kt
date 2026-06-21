package com.hermes.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1A1A2E)
private val DarkNavy = Color(0xFF0F1421)
private val SurfaceNavy = Color(0xFF16213E)
private val Amber = Color(0xFFFFBF00)
private val AmberLight = Color(0xFFFFD54F)
private val OnNavy = Color(0xFFE0E0E0)
private val OnAmber = Color(0xFF1A1A2E)

// ── Light scheme ──────────────────────────────────────────────
private val LightBackground = Color(0xFFFFF8E1) // warm cream
private val LightSurface = Color(0xFFFFFDF5)
private val LightSurfaceVariant = Color(0xFFFFF0C8)
private val LightOnBackground = Color(0xFF1A1A2E)
private val LightOnSurface = Color(0xFF1A1A2E)

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

private val HermesLightScheme = lightColorScheme(
    primary = Color(0xFF8B6F00),    // dark amber
    onPrimary = Color.White,
    primaryContainer = Amber,
    secondary = AmberLight,
    onSecondary = OnAmber,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnAmber,
    outline = Color(0xFF8B6F00).copy(alpha = 0.4f),
)

@Composable
fun HermesChatTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) HermesDarkScheme else HermesLightScheme,
        content = content,
    )
}
