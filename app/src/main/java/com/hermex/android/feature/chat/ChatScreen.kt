package com.hermex.android.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) {
        viewModel.init(sessionId, sessionTitle)
    }

    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.sessionTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = composerText,
                        onValueChange = { composerText = it },
                        placeholder = { Text("Message Hermes...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        enabled = !state.isStreaming,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (state.isStreaming) {
                        FilledIconButton(
                            onClick = { viewModel.stopStreaming() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (composerText.isNotBlank()) {
                                    viewModel.sendMessage(composerText)
                                    composerText = ""
                                }
                            },
                            enabled = composerText.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading && state.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::loadMessages) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                onToggleThinking = { viewModel.toggleThinking(msg.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    onToggleThinking: () -> Unit,
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = alignment,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(12.dp),
        ) {
            // Thinking / reasoning block (assistant only)
            if (message.role == "assistant" && !message.thinkingText.isNullOrBlank()) {
                ThinkingBlock(
                    text = message.thinkingText,
                    expanded = message.thinkingExpanded,
                    isStreaming = message.isStreaming,
                    onToggle = onToggleThinking,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Content
            if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.isStreaming) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (message.isStreaming && message.thinkingText.isNullOrBlank()) {
                // Streaming hasn't produced content yet
                Text(
                    text = "▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Tool calls (assistant only)
            if (message.toolCalls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                message.toolCalls.forEach { tc ->
                    ToolCallCard(toolCall = tc)
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Usage footer
            message.usage?.let { usage ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("${usage.totalTokens} tokens")
                        usage.estimatedCostUsd?.let { append(" · \$${String.format("%.4f", it)}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ThinkingBlock(
    text: String,
    expanded: Boolean,
    isStreaming: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▼ Thinking" else "▶ Thinking",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
        )
        if (isStreaming) {
            Spacer(Modifier.width(4.dp))
            Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp)
        }
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun ToolCallCard(toolCall: UiToolCall) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (toolCall.completed) "✓" else "◌",
                    color = if (toolCall.completed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = toolCall.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            toolCall.preview?.let { preview ->
                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preview.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
