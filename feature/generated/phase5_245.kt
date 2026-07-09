// Theme.kt
@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme()
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}