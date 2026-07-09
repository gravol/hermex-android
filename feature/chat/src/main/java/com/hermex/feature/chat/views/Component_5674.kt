// src/main/java/com/chatapp/ui/components/MessageItem.kt

package com.chatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noties.markwon.Markwon
import com.chatapp.data.*
import com.chatapp.viewmodel.TranscriptMessage

@Composable
fun MessageItem(
    message: TranscriptMessage,
    toolCalls: List<ToolCallGroup>,
    isLive: Boolean,
    reasoningGroups: List<ReasoningGroup>,
    onToggleReasoning: (ReasoningGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = when (message) {
            is TranscriptMessage.User -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        // User Message
        if (message is TranscriptMessage.User) {
            UserMessageCard(
                message = message,
                modifier = Modifier
                    .align(Alignment.End)
                    .widthIn(max = 300.dp)
            )
        }

        // Assistant Message
        if (message is TranscriptMessage.Assistant) {
            AssistantMessageCard(
                message = message,
                toolCalls = toolCalls,
                isLive = isLive,
                reasoningGroups = reasoningGroups,
                onToggleReasoning = onToggleReasoning,
                modifier = Modifier
                    .align(Alignment.Start)
                    .widthIn(max = 300.dp)
            )
        }
    }
}

@Composable
fun UserMessageCard(
    message: TranscriptMessage.User,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message.message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            if (message.message.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.message.attachments.forEach { attachment ->
                        AttachmentPreview(
                            attachment = attachment,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantMessageCard(
    message: TranscriptMessage.Assistant,
    toolCalls: List<ToolCallGroup>,
    isLive: Boolean,
    reasoningGroups: List<ReasoningGroup>,
    onToggleReasoning: (ReasoningGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Reasoning Blocks (if any)
        if (reasoningGroups.isNotEmpty()) {
            reasoningGroups.forEach { group ->
                ReasoningBlock(
                    group = group,
                    onToggle = { onToggleReasoning(group) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Main Text Content
        MarkdownText(
            text = message.message.text,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
        )

        // Tool Call Cards
        if (toolCalls.isNotEmpty()) {
            toolCalls.forEach { group ->
                group.toolCalls.forEach { toolCall ->
                    ToolCallCard(
                        toolCall = toolCall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Live Indicator
        if (isLive) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Generating...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    // Use Markwon for markdown rendering
    val markwon = remember { Markwon.create(LocalContext.current) }
    
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        linkColor = MaterialTheme.colorScheme.primary
    )
}