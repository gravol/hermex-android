// File: app/src/main/java/com/hermex/android/ui/theme/Theme.kt
package com.hermex.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = SecondaryVariant,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = SecondaryVariant,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

/**
 * Dark scheme derived from a user-picked accent color. Container tones are the
 * accent blended over the dark surfaces (alpha-over), so bubbles/buttons get a
 * coherent tint that matches the accent without extra palette work.
 *
 * Visibility notes (v0.1.48): the first version used a 22% primaryContainer and
 * left surfaceVariant untouched, so the chat (user bubbles + assistant
 * bubbles/top bars, which read surfaceVariant) barely moved while sliders and
 * the text cursor (full-strength primary) jumped — "didn't fully work".
 * primaryContainer is now 40% and surfaceVariant takes a 16% accent tint so the
 * whole chat chrome picks up the hue.
 */
private fun accentColorScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = if (isDarkForeground(accent)) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = 0.40f).compositeOver(DarkSurface),
    onPrimaryContainer = OnSurface,
    secondary = accent,
    onSecondary = if (isDarkForeground(accent)) Color.Black else Color.White,
    secondaryContainer = accent.copy(alpha = 0.25f).compositeOver(DarkSurfaceVariant),
    onSecondaryContainer = OnSurface,
    tertiary = accent.copy(alpha = 0.8f),
    onTertiary = if (isDarkForeground(accent)) Color.Black else Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = accent.copy(alpha = 0.16f).compositeOver(DarkSurfaceVariant),
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error,
)

@Composable
fun HermexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // User-picked accent wins over everything (including dynamic/wallpaper)
        accentColor != null -> accentColorScheme(accentColor)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Set status bar color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}