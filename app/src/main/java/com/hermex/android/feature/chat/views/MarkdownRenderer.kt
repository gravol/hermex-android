 ```kotlin
// StreamingMarkdownRenderer.kt
package com.hermex.android.feature.chat

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorScheme
import androidx.compose.ui.unit.Long
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun StreamingMarkdownRenderer(
    content: String,
    isStreaming: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onContentChanged: (String) -> Unit = {}
) {
    var displayedContent by remember { mutableStateOf(content) }
    
    DisposableEffect(isStreaming) {
        if (isStreaming) {
            val job = kotlinx.coroutines.launch {
                while (isStreaming) {
                    kotlinx.coroutines.delay(50)
                    onContentChanged(displayedContent)
                }
            }
            onDispose { job.cancel() }
        }
    }

    MarkdownRenderer(
        content = displayedContent,
        isStreaming = isStreaming,
        colorScheme = colorScheme,
        modifier = modifier
    )
}

@Composable
fun FadeAnimationWrapper(
    content: @Composable () -> Unit,
    isStreaming: Boolean,
    animationDuration: Long = 1000
) {
    var opacity by remember { mutableStateOf(1f) }
    
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = animationDuration)
            ) { opacity = it }
        } else {
            animateFloatAsState(
                targetValue = 0f,
                animationSpec = tween(durationMillis = animationDuration)
            ) { opacity = it }
        }
    }

    Box(
        modifier = Modifier.alpha(opacity)
    ) {
        content()
    }
}
```
