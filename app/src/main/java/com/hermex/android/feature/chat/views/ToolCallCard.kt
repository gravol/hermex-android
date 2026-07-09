 ```kotlin
package com.hermex.android.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ToolCallCard(
    toolCall: ToolCall,
    onExecute: () -> Unit = {},
    onDismiss: () -> Unit = {},
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (toolCall.status == ToolCallStatus.RUNNING) {
                colorScheme.primary.copy(alpha = 0.1f)
            } else {
                colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (toolCall.status) {
                        ToolCallStatus.PENDING -> Icons.Default.Timer
                        ToolCallStatus.RUNNING -> Icons.Default.Refresh
                        ToolCallStatus.COMPLETED -> Icons.Default.CheckCircle
                        ToolCallStatus.FAILED -> Icons.Default.Error
                    },
                    contentDescription = "Tool Status",
                    tint = when (toolCall.status) {
                        ToolCallStatus.PENDING -> Color.Gray
                        ToolCallStatus.RUNNING -> colorScheme.primary
                        ToolCallStatus.COMPLETED -> colorScheme.secondary
                        ToolCallStatus.FAILED -> Color.Red
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = toolCall.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (toolCall.status == ToolCallStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = toolCall.arguments,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            if (toolCall.status != ToolCallStatus.RUNNING) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toolCall.status == ToolCallStatus.COMPLETED) {
                        Button(
                            onClick = onExecute,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary
                            )
                        ) {
                            Text("View Result")
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss"
                        )
                    }
                }
            }
        }
    }
}
```
