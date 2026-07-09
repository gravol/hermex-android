package com.hermex.android.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermex.android.feature.chat.ui.MarkdownRenderer

@Composable
fun ReasoningBlock(
    reasoning: Reasoning,
    onToggle: () -> Unit,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (reasoning.isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (reasoning.isExpanded) "Collapse" else "Expand",
                    tint = colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (reasoning.isExpanded) "Hide Reasoning" else "Show Reasoning",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.primary
                )
            }
            
            if (reasoning.isExpanded) {
                Divider(
                    color = colorScheme.outline.copy(alpha = 0.3f)
                )
                
                MarkdownRenderer(
                    content = reasoning.content,
                    isStreaming = false,
                    colorScheme = colorScheme,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
