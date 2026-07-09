 ```kotlin
package com.hermex.android.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color

@Composable
fun Composer(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    isStreaming: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Attach Button
            IconButton(
                onClick = onAttach,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach"
                )
            }
            
            // Text Input
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = {
                    Text(
                        text = "Type a message...",
                        color = colorScheme.onSurfaceVariant
                    )
                },
                singleLine = false,
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surface,
                    unfocusedContainerColor = colorScheme.surface,
                    focusedIndicatorColor = colorScheme.primary,
                    unfocusedIndicatorColor = colorScheme.outline
                )
            )
            
            // Send/Stop Button
            if (isStreaming) {
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Stop"
                    )
                }
            } else {
                Button(
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Send")
                }
            }
        }
    }
}
```
```
