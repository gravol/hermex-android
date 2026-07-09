// Theme.kt
object ChatTheme {
    val DarkColorPalette = DarkColorPalette(
        primary = Color(0xFF007AFF),
        primaryVariant = Color(0xFF0056CC),
        secondary = Color(0xFF34C759),
        secondaryVariant = Color(0xFF269640),
        background = Color(0xFF000000),
        surface = Color(0xFF1C1C1E),
        error = Color(0xFFFF3B30),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        onError = Color.White
    )

    val LightColorPalette = LightColorPalette(
        primary = Color(0xFF007AFF),
        primaryVariant = Color(0xFF0056CC),
        secondary = Color(0xFF34C759),
        secondaryVariant = Color(0xFF269640),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFE5E5EA),
        error = Color(0xFFFF3B30),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        onError = Color.White
    )

    @Composable
    fun ChatTheme(
        darkTheme: Boolean = true,
        content: @Composable () -> Unit
    ) {
        val colorPalette = if (darkTheme) DarkColorPalette else LightColorPalette

        MaterialTheme(
            colors = if (darkTheme) {
                darkColorPalette(colorPalette)
            } else {
                lightColorPalette(colorPalette)
            },
            typography = ChatTypography,
            content = content
        )
    }

    private fun darkColorPalette(colors: DarkColorPalette): DarkColorPalette {
        return DarkColorPalette(
            primary = colors.primary,
            primaryVariant = colors.primaryVariant,
            secondary = colors.secondary,
            secondaryVariant = colors.secondaryVariant,
            background = colors.background,
            surface = colors.surface,
            error = colors.error,
            onPrimary = colors.onPrimary,
            onSecondary = colors.onSecondary,
            onBackground = colors.onBackground,
            onSurface = colors.onSurface,
            onError = colors.onError
        )
    }

    private fun lightColorPalette(colors: LightColorPalette): LightColorPalette {
        return LightColorPalette(
            primary = colors.primary,
            primaryVariant = colors.primaryVariant,
            secondary = colors.secondary,
            secondaryVariant = colors.secondaryVariant,
            background = colors.background,
            surface = colors.surface,
            error = colors.error,
            onPrimary = colors.onPrimary,
            onSecondary = colors.onSecondary,
            onBackground = colors.onBackground,
            onSurface = colors.onSurface,
            onError = colors.onError
        )
    }
}

@Serializable
data class DarkColorPalette(
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val secondaryVariant: Color,
    val background: Color,
    val surface: Color,
    val error: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onError: Color
)

@Serializable
data class LightColorPalette(
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val secondaryVariant: Color,
    val background: Color,
    val surface: Color,
    val error: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onError: Color
)

val ChatTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)