package com.hermex.android.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermex.core.network.DebugLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModelContract,
) {
    LaunchedEffect(sessionId) {
        viewModel.init(sessionId, sessionTitle)
    }

    val state = viewModel.uiState
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // Track whether user has manually scrolled away from the bottom
    var userScrolledUp by remember { mutableStateOf(false) }

    // Reset flag when user scrolls back to the bottom
    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward && userScrolledUp) {
            userScrolledUp = false
            DebugLog.log("SCROLL", "DragDetect", "userScrolledUp=false (scrolled back to bottom)")
        }
    }

    // ─── Debug: message count tracking ───
    var prevMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(state.messages.size) {
        val newCount = state.messages.size
        if (newCount != prevMessageCount) {
            DebugLog.log("UI", "MsgCount",
                "changed: $prevMessageCount → $newCount " +
                "(delta=${newCount - prevMessageCount}) " +
                "isStreaming=${state.isStreaming}")
            prevMessageCount = newCount
        }
    }

    // ─── One-shot: scroll to bottom on initial message load ───
    // Uses isNotEmpty() as key — fires exactly once when messages first arrive,
    // does NOT re-fire on subsequent sends (key stays true).
    LaunchedEffect(state.messages.isNotEmpty()) {
        if (state.messages.isNotEmpty()) {
            userScrolledUp = false
            autoScrollToBottom(
                listState = listState,
                targetIndex = state.messages.lastIndex,
                totalItems = state.messages.size,
                scrollGeneration = 0L,
                reason = "SessionOpen",
            )
        }
    }

    // ─── Streaming auto-scroll: continuous polling loop ───
    // Single source of truth for auto-scroll during streaming.
    // Replaces scrollGeneration-keyed LaunchedEffect (which restarted on every
    // SSE event, cancelling the scrollBy compensation mid-flight).
    // Polls every 50ms — fast enough to keep up with rapid content growth,
    // slow enough to avoid CPU churn.
    // Respects manual scrolling: skips when userScrolledUp=true, resumes
    // automatically when user returns to bottom (userScrolledUp→false).
    LaunchedEffect(state.isStreaming) {
        if (state.isStreaming && state.messages.isNotEmpty()) {
            DebugLog.log("SCROLL", "StreamLoop", "started (messages=${state.messages.size})")
            while (state.isStreaming && state.messages.isNotEmpty()) {
                if (!userScrolledUp) {
                    autoScrollToBottom(
                        listState = listState,
                        targetIndex = state.messages.lastIndex,
                        totalItems = state.messages.size,
                        scrollGeneration = state.scrollGeneration,
                        reason = "StreamLoop",
                    )
                }
                delay(50)
            }
            DebugLog.log("SCROLL", "StreamLoop", "ended (isStreaming=${state.isStreaming} messages=${state.messages.size})")
        }
    }

    // Detect keyboard open/close for scroll.
    // Read WindowInsets.ime BEFORE Scaffold.imePadding() consumes it.
    // Uses scrollToItem (instant) — animateScrollToItem's spring animation
    // fights the LazyColumn layout changes from imePadding() settling.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val sysBottom = WindowInsets.systemBars.getBottom(density)
    val sysTop = WindowInsets.systemBars.getTop(density)
    val keyboardOpen = imeBottom > 0
    var wasKeyboardOpen by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(imeBottom) {
        val prevFirstVisible = listState.firstVisibleItemIndex
        val prevTotalItems = state.messages.size
        val prevViewportHeight = listState.layoutInfo.viewportSize.height
        val isOpen = imeBottom > 0
        val justOpened = isOpen && wasKeyboardOpen != true
        val justClosed = !isOpen && wasKeyboardOpen == true
        wasKeyboardOpen = isOpen

        // Log only on state transitions, not every intermediate frame
        if (justOpened || justClosed) {
            DebugLog.log("UI", "Keyboard",
                "event=${if (isOpen) "OPEN" else "CLOSE"} " +
                "imeHeight=${imeBottom}px messages=$prevTotalItems " +
                "firstVisibleBefore=$prevFirstVisible " +
                "viewportHeightBefore=$prevViewportHeight")
        }

        // Scroll adjustment on EVERY imeBottom change while open (tracks animation)
        if (isOpen && state.messages.isNotEmpty()) {
            // Log scroll-relevant metrics on first open frame (transition only)
            if (justOpened) {
                DebugLog.log("UI", "Keyboard",
                    "keyboard open details: ime=${imeBottom}px " +
                    "sysBottom=${sysBottom}px sysTop=${sysTop}px " +
                    "density=${density.density}")
            }

            // Wait for keyboard animation + layout to settle.
            // Uses frame-based waits (not a fixed delay) so we re-check
            // after Compose processes the IME-driven layout pass.
            var prevHeight = listState.layoutInfo.viewportSize.height
            repeat(3) { attempt ->
                withFrameNanos { }
                val currentHeight = listState.layoutInfo.viewportSize.height
                if (currentHeight != prevHeight) {
                    if (justOpened) {
                        DebugLog.log("UI", "Keyboard",
                            "frame $attempt: viewportHeight changed $prevHeight→$currentHeight (waiting for settle)")
                    }
                    prevHeight = currentHeight
                }
            }

            // Log viewport state after keyboard settles, before scroll
            val layoutInfo = listState.layoutInfo
            val firstVis = layoutInfo.visibleItemsInfo.firstOrNull()
            val lastVis = layoutInfo.visibleItemsInfo.lastOrNull()
            val targetIdx = state.messages.lastIndex
            val viewportHeightBefore = layoutInfo.viewportSize.height
            if (justOpened) {
                DebugLog.log("SCROLL", "Keyboard",
                    "reason=keyboard_open ime=${imeBottom}px " +
                    "target=$targetIdx totalItems=${state.messages.size} " +
                    "viewportBefore=[${firstVis?.index}..${lastVis?.index}] " +
                    "viewportHeight=$viewportHeightBefore " +
                    "totalViewportItems=${layoutInfo.visibleItemsInfo.size}")
            }

            autoScrollToBottom(
                listState = listState,
                targetIndex = targetIdx,
                totalItems = state.messages.size,
                scrollGeneration = state.scrollGeneration,
                reason = "Keyboard",
            )
            if (justOpened) {
                val viewportHeightAfter = listState.layoutInfo.viewportSize.height
                val firstVisAfter = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val lastVisAfter = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                DebugLog.log("SCROLL", "Keyboard",
                    "scroll_result: target=$targetIdx " +
                    "firstVisible=${firstVisAfter?.index} lastVisible=${lastVisAfter?.index} " +
                    "viewportHeight=$viewportHeightAfter→$viewportHeightBefore")
            }
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            // Retry button — visible when not streaming and last msg is assistant
                            if (!state.isStreaming && state.messages.any { it.role == "assistant" }) {
                                IconButton(onClick = { viewModel.retry() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                                }
                            }
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
                            .fillMaxWidth()
                            .nestedScroll(remember {
                                object : NestedScrollConnection {
                                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                        if (source == NestedScrollSource.UserInput && available.y > 0) {
                                            if (!userScrolledUp) {
                                                userScrolledUp = true
                                                DebugLog.log("SCROLL", "DragDetect",
                                                    "userScrolledUp=true (source=$source, " +
                                                    "available.y=${available.y}, " +
                                                    "isScrollInProgress=${listState.isScrollInProgress})")
                                            }
                                        }
                                        return Offset.Zero
                                    }
                                }
                            }),
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

    // ── Tool Approval Dialog ──
    val pendingApproval = state.pendingApproval
    if (pendingApproval != null) {
        Dialog(onDismissRequest = { /* must approve or deny */ }) {
            Card(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 400.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Approve Tool?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pendingApproval.toolName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (pendingApproval.toolArgs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = pendingApproval.toolArgs,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.denyCurrentTool() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Deny")
                        }
                        Button(
                            onClick = { viewModel.approveCurrentTool() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Approve")
                        }
                    }
                }
            }
        }
    }

    // ── Clarify Dialog ──
    val pendingClarify = state.pendingClarify
    if (pendingClarify != null) {
        var clarifyAnswer by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { /* must answer */ }) {
            Card(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 400.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Clarification Needed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (pendingClarify.question.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pendingClarify.question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = clarifyAnswer,
                        onValueChange = { clarifyAnswer = it },
                        placeholder = { Text("Type your answer...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Send empty string as "dismiss" to unblock the turn
                                viewModel.respondToClarify("")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { viewModel.respondToClarify(clarifyAnswer) },
                            modifier = Modifier.weight(1f),
                            enabled = clarifyAnswer.isNotBlank(),
                        ) {
                            Text("Send")
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
                // No content yet — show typing dots while waiting, cursor otherwise
                if (message.isWaitingForFirstEvent) {
                    TypingDots()
                } else {
                    Text(
                        text = "▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
            toolCall.args?.let { args ->
                if (args.isNotBlank() && (toolCall.preview?.isNotBlank() != true)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = args.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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

// ── TYPING DOTS (bouncing animation, iMessage-style) ──

@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val delays = listOf(0, 150, 300)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        delays.forEachIndexed { i, delayMs ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    ),
            )
        }
    }
}

// ── AUTO-SCROLL HELPER ──
// Scrolls to the target item, then compensates for items that extend beyond
// the viewport (common during streaming when content grows taller than the
// visible area). Uses scrollToItem (instant) + scrollBy for the remainder.
// Logs comprehensive debug info: first/last visible indices, viewport height,
// canScrollForward, and actual item bottom offset.
//
// IMPORTANT: This function MUST complete fully (both steps) to keep the
// bottom of a tall message visible. The polling loop (StreamLoop) ensures
// this function runs uninterrupted — it is NOT cancelled by rapid key
// changes (unlike the old LaunchedEffect on scrollGeneration).

private suspend fun autoScrollToBottom(
    listState: LazyListState,
    targetIndex: Int,
    totalItems: Int,
    scrollGeneration: Long,
    reason: String,
) {
    val beforeFirst = listState.firstVisibleItemIndex
    val beforeLast = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val beforeCanScroll = listState.canScrollForward
    val beforeViewportHeight = listState.layoutInfo.viewportSize.height

    DebugLog.log("SCROLL", reason,
        "scrollToItem(target=$targetIndex) totalItems=$totalItems " +
        "gen=$scrollGeneration " +
        "firstVisibleBefore=$beforeFirst " +
        "lastVisibleBefore=$beforeLast " +
        "viewportHeight=$beforeViewportHeight " +
        "canScrollForward=$beforeCanScroll")

    // Step 1: default scroll to make the target item visible
    listState.scrollToItem(targetIndex)

    // Step 2: compensate for item taller than viewport.
    // After scrollToItem, if the last visible item includes the target and
    // canScrollForward is still true, the item extends below the viewport.
    // Compute the remaining scroll distance from layout info and scrollBy it.
    val afterFirst = listState.firstVisibleItemIndex
    val afterCanScroll = listState.canScrollForward
    val layoutInfo = listState.layoutInfo
    val viewportHeight = layoutInfo.viewportSize.height
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()

    if (afterCanScroll && lastVisible != null && lastVisible.index >= targetIndex) {
        val itemBottom = lastVisible.offset + lastVisible.size
        val beyondViewport = itemBottom - viewportHeight
        if (beyondViewport > 0) {
            DebugLog.log("SCROLL", reason,
                "compensating: item[${lastVisible.index}] offset=${lastVisible.offset} " +
                "size=${lastVisible.size} bottom=$itemBottom " +
                "viewportHeight=$viewportHeight beyond=$beyondViewport px")
            listState.scrollBy(beyondViewport.toFloat())
        }
    }

    val afterFirst2 = listState.firstVisibleItemIndex
    val afterLast2 = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val afterCanScroll2 = listState.canScrollForward
    DebugLog.log("SCROLL", reason,
        "done: firstVisible=$afterFirst2 " +
        "lastVisible=$afterLast2 " +
        "canScrollForward=$afterCanScroll2")
}
