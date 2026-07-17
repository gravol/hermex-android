package com.hermex.android.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val state = viewModel.uiState
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // Session open: instantly jump to last message (no animation)
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    // Streaming auto-scroll: only when content changes, with near-bottom guard
    LaunchedEffect(state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            if (lastVisible >= total - 3) {
                listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    // Detect keyboard open/close for scroll.
    // Read WindowInsets.ime BEFORE Scaffold.imePadding() consumes it.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(200)  // wait for keyboard animation
            // Always scroll when keyboard opens — user wants to type, see latest
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // Track composer focus separately (for other uses if needed)
    var composerFocused by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.imePadding(),
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
                modifier = Modifier.navigationBarsPadding(),
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
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { composerFocused = it.isFocused },
                        maxLines = 4,
                        enabled = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Error banner for send failures (wrap_content height)
            if (state.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.loadMessages() }, modifier = Modifier.size(24.dp)) {
                            Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            when {
                state.isLoading && state.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(state.messages, key = { _, msg -> msg.id }) { index, msg ->
                            val sameSender = index > 0 && state.messages[index - 1].role == msg.role

                            // Live thinking block: shown ABOVE the assistant bubble
                            // while thinking is streaming and no real content has arrived yet
                            val showLiveThinking = msg.role == "assistant"
                                    && msg.thinkingText != null
                                    && msg.isStreaming
                                    && !msg.thinkingHasContent

                            if (showLiveThinking) {
                                LiveThinkingTicker(text = msg.thinkingText)
                            }

                            MessageBubble(
                                message = msg,
                                sameSender = sameSender,
                                onLongPress = {
                                    val textToCopy = if (msg.content.isNotBlank()) msg.content
                                        else msg.thinkingText ?: return@MessageBubble
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Hermes message", textToCopy))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                onToggleThinking = { viewModel.toggleThinking(msg.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── LIVE THINKING TICKER ──
// Shown above the assistant bubble while the model is thinking
// and no real content has arrived yet. Dimmed, italic, live-updating.

@Composable
private fun LiveThinkingTicker(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Spinning indicator
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── MESSAGE BUBBLE (Telegram-style) ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: UiMessage,
    sameSender: Boolean,
    onLongPress: () -> Unit,
    onToggleThinking: () -> Unit,
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    // Telegram-style corner radii: user = top-left/bottom-left/bottom-right rounded,
    // top-right sharp; assistant = top-right/bottom-right/bottom-left rounded, top-left sharp
    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 4.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (sameSender) 1.dp else 6.dp,
                bottom = 0.dp,
            ),
        horizontalAlignment = alignment,
    ) {
        // Timestamp centered above first message of a group
        if (!sameSender) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .widthIn(min = 60.dp, max = 320.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Thinking block (collapsed toggle — only when thinking has content or is done)
            val showToggle = message.role == "assistant"
                    && message.thinkingText != null
                    && (message.thinkingHasContent || !message.isStreaming)
            if (showToggle) {
                ThinkingToggle(
                    expanded = message.thinkingExpanded,
                    onToggle = onToggleThinking,
                )
                AnimatedVisibility(
                    visible = message.thinkingExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = message.thinkingText ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                if (message.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Content
            if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.isStreaming) {
                    Text(
                        text = " ▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (message.isStreaming && message.thinkingText == null) {
                // Streaming hasn't produced content yet, no thinking either
                Text(
                    text = "▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (message.isStreaming && message.thinkingHasContent && message.content.isBlank()) {
                // Thinking done, content starting soon — show cursor
                Text(
                    text = "▌",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Tool calls (assistant only)
            if (message.toolCalls.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                message.toolCalls.forEach { tc ->
                    ToolCallCard(toolCall = tc)
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Usage footer + inline timestamp
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                message.usage?.let { usage ->
                    Text(
                        text = buildString {
                            append("${usage.totalTokens} tokens")
                            usage.estimatedCostUsd?.let { append(" · \$${String.format("%.4f", it)}") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── THINKING TOGGLE (collapsed state) ──

@Composable
private fun ThinkingToggle(
    expanded: Boolean,
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
            text = if (expanded) "▼ Thinking" else "▶ Show thinking",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── TOOL CALL CARD ──

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

// ── TIMESTAMP HELPERS ──

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    .withZone(ZoneId.systemDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochMillis: Long): String {
    return try {
        dateFormatter.format(Instant.ofEpochMilli(epochMillis))
    } catch (_: Exception) {
        ""
    }
}

private fun formatTime(epochMillis: Long): String {
    return try {
        timeFormatter.format(Instant.ofEpochMilli(epochMillis))
    } catch (_: Exception) {
        ""
    }
}
