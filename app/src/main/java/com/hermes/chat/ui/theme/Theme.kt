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

object TelegramChatColors {
    val Blue = Color(0xFF2AABEE)
    val BlueDark = Color(0xFF168AC5)
    val DarkCanvas = Color(0xFF0E1621)
    val DarkTopBar = Color(0xFF17212B)
    val DarkIncomingBubble = Color(0xFF182533)
    val DarkOutgoingBubble = Color(0xFF2B5278)
    val DarkComposer = Color(0xFF17212B)
    val DarkComposerField = Color(0xFF242F3D)
    val LightCanvas = Color(0xFFE7EBF0)
    val LightIncomingBubble = Color(0xFFFFFFFF)
    val LightOutgoingBubble = Color(0xFFD8F2C2)
}

// ── Light scheme ──────────────────────────────────────────────
private val LightBackground = Color(0xFFFFF8E1) // warm cream
private val LightSurface = Color(0xFFFFFDF5)
private val LightSurfaceVariant = Color(0xFFFFF0C8)
private val LightOnBackground = Color(0xFF1A1A2E)
private val LightOnSurface = Color(0xFF1A1A2E)

private val HermesDarkScheme = darkColorScheme(
    primary = TelegramChatColors.Blue,
    onPrimary = Color.White,
    primaryContainer = TelegramChatColors.BlueDark,
    secondary = TelegramChatColors.Blue,
    onSecondary = Color.White,
    background = TelegramChatColors.DarkCanvas,
    onBackground = OnNavy,
    surface = TelegramChatColors.DarkTopBar,
    onSurface = OnNavy,
    surfaceVariant = TelegramChatColors.DarkComposerField,
    onSurfaceVariant = OnNavy,
    outline = TelegramChatColors.Blue.copy(alpha = 0.45f),
)

private val HermesLightScheme = lightColorScheme(
    primary = TelegramChatColors.BlueDark,
    onPrimary = Color.White,
    primaryContainer = TelegramChatColors.Blue,
    secondary = TelegramChatColors.Blue,
    onSecondary = Color.White,
    background = TelegramChatColors.LightCanvas,
    onBackground = LightOnBackground,
    surface = TelegramChatColors.LightIncomingBubble,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF4F7FA),
    onSurfaceVariant = LightOnSurface,
    outline = TelegramChatColors.BlueDark.copy(alpha = 0.35f),
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
