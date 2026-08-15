package com.hermex.android.feature.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog
import com.hermex.core.network.JsonRpcClient
import com.hermex.core.network.NetworkResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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

    // Report screen visibility so the VM can flag turns that finish while the
    // user is away (background turns — v0.1.60).
    DisposableEffect(Unit) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }

    val state = viewModel.uiState
    val listState = rememberLazyListState()
    var composerText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Photo attach state ──
    var pendingImageB64 by remember { mutableStateOf<String?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val encoded = downscaleAndEncode(context, uri)
            if (encoded != null) {
                pendingImageB64 = encoded.first
                pendingImageUri = uri
            } else {
                Toast.makeText(context, "Couldn't read image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Voice message state ──
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.webm")
            val rec = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.WEBM)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            recordingFile = file
            recordingSeconds = 0
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    fun stopAndTranscribe() {
        val rec = recorder ?: return
        val file = recordingFile ?: return
        try {
            runCatching { rec.stop() }
        } catch (_: Exception) { /* short recording */ }
        rec.release()
        recorder = null
        recordingFile = null
        isRecording = false
        scope.launch {
            isTranscribing = true
            try {
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUrl = "data:audio/webm;base64,$b64"
                when (val r = DashboardApiClient.transcribeAudio(dataUrl, "audio/webm")) {
                    is NetworkResult.Success -> {
                        val transcript = r.data.transcript.orEmpty().trim()
                        if (transcript.isBlank()) {
                            Toast.makeText(context, "No speech detected", Toast.LENGTH_SHORT).show()
                        } else {
                            composerText = if (composerText.isBlank()) transcript
                            else "$composerText $transcript"
                        }
                    }
                    is NetworkResult.HttpError -> {
                        Toast.makeText(
                            context,
                            "Transcription failed (${r.code})",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            context,
                            "Transcription failed: ${r.exception.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Transcription failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isTranscribing = false
                file.delete()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Microphone permission needed for voice messages", Toast.LENGTH_LONG).show()
    }

    // Recording elapsed timer
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingSeconds++
        }
    }

    fun sendComposer() {
        val text = composerText
        val img = pendingImageB64
        if (text.isBlank() && img == null) return
        if (img != null) {
            viewModel.sendMessageWithImage(text, img, pendingImageUri?.lastPathSegment)
        } else {
            viewModel.sendMessage(text)
        }
        composerText = ""
        pendingImageB64 = null
        pendingImageUri = null
    }

    // ── Slash-command completions (v0.1.65) ──
    var composerFocused by remember { mutableStateOf(false) }
    var slashItems by remember { mutableStateOf<List<JsonRpcClient.SlashItem>?>(null) }

    // ── Model picker (v0.1.88) ──
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(composerText, composerFocused, state.isStreaming) {
        val text = composerText
        if (composerFocused && !state.isStreaming && text.startsWith("/")) {
            delay(150)  // debounce while typing
            slashItems = runCatching { viewModel.completeSlash(text) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        } else {
            slashItems = null
        }
    }

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

    // ─── Streaming auto-scroll: state-change driven (replaces 100ms poll) ───
    // Single source of truth for auto-scroll during streaming.
    // snapshotFlow + distinctUntilChanged fires ONLY when the last message's
    // visible content actually changes (text growth, thinking growth, new
    // message, tool card), instead of waking every 100ms — kills the no-op
    // wake-ups during thinking.
    // CRITICAL: read state via viewModel.uiState (the MutableState getter), NOT
    // the captured `state` val — a plain field read on the captured instance
    // registers no snapshot read, so snapshotFlow emits exactly once and never
    // re-fires (v0.1.44 regression: stream only scrolled at placeholder
    // creation, viewport never tracked the growing bubble).
    // The collect block runs to completion per emission in one coroutine; it is
    // NOT cancelled by rapid state writes (unlike the old LaunchedEffect keyed
    // on scrollGeneration), so the two-step scrollToItem+scrollBy compensation
    // still never gets interrupted mid-flight.
    // Respects manual scrolling: skips when userScrolledUp=true, resumes
    // automatically when user returns to bottom (userScrolledUp→false).
    // Gated on message presence (not isStreaming) so a session resumed while
    // the assistant is mid-response still tracks the stream.
    LaunchedEffect(state.isStreaming, state.messages.isNotEmpty()) {
        if (state.messages.isNotEmpty()) {
            DebugLog.log("SCROLL", "StreamLoop", "started (messages=${state.messages.size})")
            snapshotFlow {
                val s = viewModel.uiState
                val last = s.messages.last()
                Triple(
                    s.messages.size,
                    last.content.length + (last.thinkingText?.length ?: 0),
                    last.toolCalls.size,
                )
            }
                .distinctUntilChanged()
                .collect {
                    val s = viewModel.uiState
                    if (!userScrolledUp && s.messages.isNotEmpty()) {
                        autoScrollToBottom(
                            listState = listState,
                            targetIndex = s.messages.lastIndex,
                            totalItems = s.messages.size,
                            scrollGeneration = s.scrollGeneration,
                            reason = "StreamLoop",
                        )
                    }
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

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.sessionTitle,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Live context-window occupancy (session.info usage).
                        // v0.1.71: ALWAYS visible once the chat is open —
                        // mirrors the desktop's never-blank behavior. Shows
                        // the last-known reading when the server is quiet
                        // (e.g. reaped agent after app update), and "—" before
                        // any reading exists, instead of hiding the slot.
                        val ctxUsed = state.contextUsed
                        val ctxMax = state.contextMax
                        val knownMax = ctxMax != null && ctxMax > 0
                        val knownUsed = knownMax && ctxUsed != null
                        val fraction = if (knownUsed) {
                            (ctxUsed.toFloat() / ctxMax.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // v0.1.88: model chip — shows current model + reasoning
                            // effort; tap opens the picker sheet.
                            val modelText = buildString {
                                append(state.currentModel?.let { shortModelName(it) } ?: "")
                                if (isNotBlank()) append(" · ")
                                append(state.currentReasoning?.let { effortShort(it) } ?: "")
                            }
                            if (modelText.isNotBlank()) {
                                Text(
                                    text = modelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showModelPicker = true }
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (knownUsed && fraction > 0.8f) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = when {
                                    knownUsed -> "${formatTokens(ctxUsed)}/${formatTokens(ctxMax)}"
                                    knownMax -> "—/${formatTokens(ctxMax)}"
                                    else -> "—/—"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (knownUsed) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            )
                        }
                    }
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
                Column {
                    // Pending image thumbnail (removable)
                    if (pendingImageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = pendingImageUri,
                                contentDescription = "Attached image",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Image attached",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                pendingImageB64 = null
                                pendingImageUri = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image")
                            }
                        }
                    }
                    // Recording indicator
                    if (isRecording || isTranscribing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isTranscribing) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color(0xFFFF3B30)
                                    }),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isTranscribing -> "Transcribing…"
                                    else -> "Recording %d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isTranscribing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFFF3B30)
                                },
                            )
                        }
                    }
                    // Slash-command completions — pop up above the composer.
                    // Capture to a local: LazyColumn DEFERS its content lambda
                    // (runs later inside intervalContentState derivedStateOf),
                    // so a `!!` re-reading the mutable state there would NPE if
                    // the LaunchedEffect nulls slashItems (focus loss, text
                    // edit, suggestion tap) between guard and deferred exec.
                    val slashItemsNow = slashItems
                    if (slashItemsNow != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                itemsIndexed(slashItemsNow) { _, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Server completions omit the
                                                // leading "/" (already typed) —
                                                // restore it or the command
                                                // becomes plain text.
                                                composerText = if (item.text.startsWith("/")) {
                                                    item.text
                                                } else {
                                                    "/" + item.text
                                                }
                                                slashItems = null
                                                focusRequester.requestFocus()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = item.display ?: item.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.kind == "skill") {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.kind == "skill") {
                                            Text(
                                                text = "skill",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        item.meta?.let { meta ->
                                            if (meta.isNotBlank()) {
                                                Text(
                                                    text = meta,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(0.9f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Photo attach
                        IconButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !isRecording && !isTranscribing,
                        ) {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                contentDescription = "Attach photo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Voice message
                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    stopAndTranscribe()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) startRecording()
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isTranscribing,
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Filled.Mic else Icons.Outlined.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Voice message",
                                tint = if (isRecording) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                        Spacer(Modifier.width(4.dp))
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
                                    onClick = { sendComposer() },
                                    enabled = (composerText.isNotBlank() || pendingImageB64 != null) &&
                                        !isTranscribing,
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

            // Agent task list (todo tool state) — pinned above the messages
            if (state.todos.isNotEmpty()) {
                TasksCard(
                    todos = state.todos,
                    expanded = state.todosExpanded,
                    isStreaming = state.isStreaming,
                    onToggle = { viewModel.toggleTodosExpanded() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Completed-while-away banner (background turns, v0.1.60)
            if (state.completedWhileAway) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✓ Turn finished while you were away",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            viewModel.clearCompletedWhileAway()
                            scope.launch {
                                if (state.messages.isNotEmpty()) {
                                    listState.scrollToItem(state.messages.lastIndex)
                                }
                            }
                        }) {
                            Text("View latest")
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

                            // During streaming, live thinking + tool activity live
                            // in the docked LiveActivityPanel (bottom); the
                            // in-stream versions only render once the turn is
                            // done (tools + thinking above the final answer).
                            if (showLiveThinking && !state.isStreaming) {
                                LiveThinkingTicker(text = msg.thinkingText)
                            }

                            // After the turn, thinking persists as a scrollable
                            // box above the tools+answer (during streaming it
                            // lives in the docked live panel instead).
                            if (msg.role == "assistant" && msg.thinkingText?.isNotBlank() == true && !msg.isStreaming) {
                                ThinkingScrollBox(text = msg.thinkingText)
                            }

                            // Tool calls render ABOVE the response in one
                            // scrollable box (v0.1.68); tap a row for the full
                            // card (args/diff).
                            if (msg.role == "assistant" && msg.toolCalls.isNotEmpty() && !msg.isStreaming) {
                                ToolScrollBox(toolCalls = msg.toolCalls)
                            }

                            // Skip the empty bubble for tool-only assistant rows
                            // from history (content blank, not streaming) — the
                            // thinking + tools boxes above already tell the story.
                            if (msg.content.isNotBlank() || msg.isStreaming) {
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

            // Docked live-activity panel (v0.1.64): while a turn is running,
            // thinking + tool calls stream in a small scrollable box above the
            // composer. On completion the panel vanishes and the finished
            // message shows tools + thinking above the final answer instead.
            val liveMsg = state.messages.lastOrNull { it.isStreaming }
            val livePanelVisible = state.isStreaming && liveMsg != null &&
                (liveMsg.thinkingText?.isNotBlank() == true || liveMsg.toolCalls.isNotEmpty())
            if (livePanelVisible) {
                LiveActivityPanel(
                    thinking = liveMsg!!.thinkingText.orEmpty(),
                    toolCalls = liveMsg.toolCalls,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }

    // ── Model Picker Sheet (v0.1.88) ──
    if (showModelPicker) {
        ModelPickerSheet(
            viewModel = viewModel,
            onDismiss = { showModelPicker = false },
        )
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

/**
 * Scrollable thinking box shown above the final answer once the turn is done
 * (v0.1.66). During streaming the live docked panel owns thinking instead.
 */
@Composable
private fun ThinkingScrollBox(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "THINKING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

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
                // v0.1.87: assistant bubbles are full-bleed (edge to edge) —
                // only user bubbles keep side insets.
                start = if (isUser) 8.dp else 0.dp,
                end = if (isUser) 8.dp else 0.dp,
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

        // Assistant bubbles span the full width (edge to edge); user bubbles
        // stay capped at a chat-style max width, aligned right.
        val bubbleWidthModifier = if (isUser) {
            Modifier.widthIn(min = 60.dp, max = 320.dp)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(
            modifier = bubbleWidthModifier
                .clip(bubbleShape)
                .background(bubbleColor)
                // Border in the context-gauge color (primary) — subtle outline
                // around each message (v0.1.67).
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    shape = bubbleShape,
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Thinking no longer renders in the message (v0.1.65): it streams
            // in the docked Live Activity panel while working — showing it here
            // too caused double-thinking. Tool cards above the answer remain.

            // Content
            if (message.content.isNotBlank()) {
                // Use immediate=true to avoid Loading→Success height oscillation
                // during streaming (fixes scroll-crazy feedback loop).
                // Override heading typography with reasonable sizes (not
                // displayLarge ~57sp which "balloons" text in the bubble).
                val mdState = com.mikepenz.markdown.model.rememberMarkdownState(
                    content = message.content,
                    immediate = true,
                )
                Markdown(
                    markdownState = mdState,
                    // v0.1.87: no width cap for assistant messages — text spans
                    // the full bubble edge to edge. User bubbles keep the cap
                    // (they're 320dp max anyway).
                    modifier = if (isUser) Modifier.widthIn(max = 400.dp) else Modifier.fillMaxWidth(),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.titleLarge,
                        h2 = MaterialTheme.typography.titleMedium,
                        h3 = MaterialTheme.typography.titleSmall,
                        h4 = MaterialTheme.typography.bodyLarge,
                        h5 = MaterialTheme.typography.bodyMedium,
                        h6 = MaterialTheme.typography.bodyMedium,
                        text = MaterialTheme.typography.bodyMedium,
                    ),
                    components = markdownComponents(
                        codeBlock = highlightedCodeBlock,
                        codeFence = highlightedCodeFence,
                    ),
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
    var expanded by remember { mutableStateOf(false) }
    val now by remember { mutableStateOf(System.currentTimeMillis()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // ── Header row: icon + name + elapsed + expand arrow ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                // Tool icon
                Text(
                    text = toolIcon(toolCall.toolName),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(6.dp))
                // Tool name
                Text(
                    text = toolCall.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                // Elapsed time
                Text(
                    text = formatElapsed(toolCall.startedAt, now, toolCall.completed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                // Status + expand arrow
                Text(
                    text = if (toolCall.completed) "✓" else "◌",
                    color = if (toolCall.completed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }

            // ── Preview (always visible, one line) ──
            val previewText = toolCall.preview
                ?: toolCall.summary
                ?: toolCall.args?.take(100)
            if (!previewText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // ── Expandable detail section ──
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    // Args section
                    if (!toolCall.args.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Arguments",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = toolCall.args,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(6.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }

                    // Result section (completed only)
                    if (toolCall.completed && !toolCall.result.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Result",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = toolCall.result.take(500),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(6.dp),
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }

                    // Diff section (file edits — server inline_diff, desktop-style)
                    if (!toolCall.inlineDiff.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Diff",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(2.dp))
                        DiffView(toolCall.inlineDiff)
                    }
                }
            }
        }
    }
}

/**
 * Desktop-style unified diff: monospace lines with red/green tinting for
 * removed/added lines, highlighted hunk headers and file lines. The server's
 * inline_diff carries ANSI color codes (terminal rendering) — we strip them
 * and re-classify by line prefix so colors follow the app theme.
 */
@Composable
private fun DiffView(diffText: String) {
    val lines = remember(diffText) { parseDiffLines(diffText) }
    Surface(
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(line.bgColor),
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = line.textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private enum class DiffKind { FILE, HUNK, ADD, DEL, CONTEXT, OTHER }

private data class DiffLine(val text: String, val kind: DiffKind) {
    val textColor: Color
        get() = when (kind) {
            DiffKind.ADD -> Color(0xFFA5D6A7)
            DiffKind.DEL -> Color(0xFFEF9A9A)
            DiffKind.HUNK -> Color(0xFF82B1FF)
            DiffKind.FILE -> Color(0xFF80CBC4)
            else -> Color(0xFFB0BEC5)
        }
    val bgColor: Color
        get() = when (kind) {
            DiffKind.ADD -> Color(0x1F2E7D32)
            DiffKind.DEL -> Color(0x1FB71C1C)
            else -> Color.Transparent
        }
}

private val ansiRegex = Regex("\u001B\\[[0-9;]*[A-Za-z]")

private fun stripAnsi(text: String): String = ansiRegex.replace(text, "")

/** Parse ANSI-stripped inline_diff text into classified lines (desktop-style). */
private fun parseDiffLines(raw: String): List<DiffLine> {
    return raw.lineSequence().mapNotNull { rawLine ->
        val line = stripAnsi(rawLine).trimEnd('\r')
        if (line.isBlank() && !rawLine.startsWith(" ")) return@mapNotNull null
        val kind = when {
            line.startsWith("@@") -> DiffKind.HUNK
            line.startsWith("+") && !line.startsWith("+++") -> DiffKind.ADD
            line.startsWith("-") && !line.startsWith("---") -> DiffKind.DEL
            line.startsWith(" ") -> DiffKind.CONTEXT
            line.startsWith("a/") || line.startsWith("b/") || line.contains("→") -> DiffKind.FILE
            else -> DiffKind.OTHER
        }
        DiffLine(line, kind)
    }.toList()
}

/** Map tool name to an icon character. */
private fun toolIcon(name: String): String = when {
    name.contains("web_search", ignoreCase = true) -> "🔍"
    name.contains("web_fetch", ignoreCase = true) || name.contains("http", ignoreCase = true) -> "🌐"
    name.contains("read_file", ignoreCase = true) || name.contains("cat", ignoreCase = true) -> "📄"
    name.contains("write_file", ignoreCase = true) || name.contains("edit", ignoreCase = true) -> "✏️"
    name.contains("bash", ignoreCase = true) || name.contains("terminal", ignoreCase = true) ||
        name.contains("command", ignoreCase = true) || name.contains("shell", ignoreCase = true) -> "💻"
    name.contains("python", ignoreCase = true) || name.contains("code", ignoreCase = true) ||
        name.contains("run", ignoreCase = true) -> "▶️"
    name.contains("search", ignoreCase = true) -> "🔎"
    name.contains("list", ignoreCase = true) || name.contains("dir", ignoreCase = true) -> "📋"
    name.contains("think", ignoreCase = true) -> "🧠"
    else -> "⚙️"
}

/** Format elapsed time for a tool call. Shows live duration for running tools. */
private fun formatElapsed(startedAt: Long?, now: Long, completed: Boolean): String {
    val start = startedAt ?: return ""
    val elapsedMs = now - start
    val seconds = elapsedMs / 1000
    val millis = elapsedMs % 1000
    return if (completed || seconds >= 60) {
        // Show mm:ss for long or completed tools
        val m = seconds / 60
        val s = seconds % 60
        if (m > 0) "${m}m ${s}s" else "${seconds}.${millis / 100}s"
    } else {
        // Show X.Xs for short running tools
        "${seconds}.${millis / 100}s"
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

/** Compact token count formatting: 85123 → "85.1k", 1048576 → "1.0M". */
private fun formatTokens(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000f)
        tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000f)
        else -> tokens.toString()
    }
}

/**
 * Docked live-activity panel (v0.1.64): small scrollable box above the composer
 * showing thinking + tool calls as they stream, while the answer grows above it.
 * Disappears when the turn completes (the finished message then shows tools +
 * thinking above the final answer instead).
 */
@Composable
private fun LiveActivityPanel(
    thinking: String,
    toolCalls: List<UiToolCall>,
    modifier: Modifier = Modifier,
) {
    val now by remember { mutableStateOf(System.currentTimeMillis()) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Live activity",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (toolCalls.isNotEmpty()) {
                    Text(
                        text = "${toolCalls.count { !it.completed }} working · ${toolCalls.size} tools",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
            ) {
                if (thinking.isNotBlank()) {
                    item(key = "thinking") {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = "THINKING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = thinking,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
                // itemsIndexed (positional key): tool ids can duplicate (history
                // replay defaults them to "tc") — an explicit key would crash.
                itemsIndexed(toolCalls) { _, tc ->
                    ToolActivityRow(toolCall = tc, now = now)
                }
            }
            // Auto-scroll the panel to the newest activity
            LaunchedEffect(thinking.length, toolCalls.size) {
                val count = toolCalls.size + if (thinking.isNotBlank()) 1 else 0
                if (count > 0) listState.scrollToItem(count - 1)
            }
        }
    }
}

/** One compact tool row: icon · name · elapsed · spinner-or-check. */
@Composable
private fun ToolActivityRow(toolCall: UiToolCall, now: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = toolIcon(toolCall.toolName),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = toolCall.toolName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatElapsed(toolCall.startedAt, now, toolCall.completed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        if (toolCall.completed) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF4CAF50),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Scrollable tool-call box shown above the finished answer (v0.1.68).
 * Compact rows; tap a row for the full card (args/diff/preview).
 */
@Composable
private fun ToolScrollBox(toolCalls: List<UiToolCall>) {
    val now by remember { mutableStateOf(System.currentTimeMillis()) }
    var detail by remember { mutableStateOf<UiToolCall?>(null) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TOOLS · ${toolCalls.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                toolCalls.forEach { tc ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detail = tc },
                    ) {
                        ToolActivityRow(toolCall = tc, now = now)
                    }
                }
            }
        }
    }

    detail?.let { tc ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(tc.toolName) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 420.dp),
                ) {
                    if (tc.preview != null) {
                        Text(
                            text = "CALL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(tc.preview, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (tc.args != null) {
                        Text(
                            text = "ARGS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = tc.args,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (tc.inlineDiff != null) {
                        DiffView(diffText = tc.inlineDiff)
                    } else if (tc.summary != null) {
                        Text(
                            text = "RESULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(tc.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("Close") }
            },
        )
    }
}

/**
 * Downscale an image URI to ≤1600px and encode as base64 JPEG (data URL).
 * Returns (dataUrl, filename) or null on failure. Runs on the caller's thread
 * (pick-launcher callback — small images decode fast; 1600px cap keeps it sane).
 */
private fun downscaleAndEncode(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val maxDim = 1600
        while ((bounds.outWidth / sample) > maxDim || (bounds.outHeight / sample) > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        val name = "photo_${System.currentTimeMillis()}.jpg"
        "data:image/jpeg;base64,$b64" to name
    } catch (_: Exception) {
        null
    }
}

/**
 * Collapsible agent task list card, pinned above the messages.
 * Collapsed: "Tasks 2/5" + active task + thin progress bar.
 * Expanded: full list with done / active (spinner) / pending states.
 */
@Composable
private fun TasksCard(
    todos: List<UiTodo>,
    expanded: Boolean,
    isStreaming: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneCount = todos.count { it.isDone }
    val active = todos.firstOrNull { it.isActive }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$doneCount/${todos.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (doneCount == todos.size && todos.isNotEmpty()) {
                            "All tasks done"
                        } else {
                            "Tasks"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isStreaming && active != null) {
                        Text(
                            text = active.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { if (todos.isEmpty()) 0f else doneCount.toFloat() / todos.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    todos.forEach { todo ->
                        TodoRow(todo)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: UiTodo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            todo.isDone -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            todo.isActive -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Pending",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                todo.isDone -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                todo.isActive -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (todo.isActive) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (todo.status == "cancelled") TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Short display name for a model id (v0.1.88). */
private fun shortModelName(model: String): String {
    // "deepseek/deepseek-v4-pro" → "v4-pro"; "deepseek-v4-flash" → "v4-flash"
    val last = model.substringAfterLast('/').substringAfterLast(':')
    return last.removePrefix("deepseek-").removePrefix("deepseek").take(24)
}

/** Reasoning effort label (v0.1.88). */
private fun effortShort(effort: String): String = when (effort.lowercase()) {
    "ultra" -> "ultra"
    "high" -> "high"
    "medium", "med" -> "med"
    "low" -> "low"
    else -> effort.take(8)
}

private val EFFORT_OPTIONS = listOf("low", "medium", "high")

/**
 * Model + reasoning picker (v0.1.88). Reads model.options from the server,
 * shows providers grouped with their models; the selection persists and
 * applies to NEW sessions (desktop-composer contract — there is no
 * mid-conversation switch RPC yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    viewModel: ChatViewModelContract,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<JsonRpcClient.ModelOptionsResult?>(null) }
    var selectedModel by remember { mutableStateOf("") }
    // Start from the session's current effort so "Apply to this chat" doesn't
    // silently reset reasoning to medium when the user only meant to switch model.
    var selectedEffort by remember { mutableStateOf(viewModel.uiState.currentReasoning ?: "medium") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val opts = viewModel.loadModelOptions()
            options = opts
            selectedModel = opts.model ?: ""
            selectedEffort = viewModel.uiState.currentReasoning ?: selectedEffort
        } catch (e: Exception) {
            error = e.message ?: "Failed to load models"
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Model & Reasoning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Apply to this chat, or save as the default for new chats.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            if (options == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                options!!.providers.forEach { provider ->
                    if (provider.models.isEmpty()) return@forEach
                    Text(
                        text = provider.name ?: provider.slug,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    provider.models.forEach { model ->
                        val selected = model == selectedModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable { selectedModel = model }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = shortModelName(model),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Reasoning effort", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    EFFORT_OPTIONS.forEach { effort ->
                        FilterChip(
                            selected = selectedEffort == effort,
                            onClick = { selectedEffort = effort },
                            label = { Text(effort.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // v0.1.89: switch THIS chat immediately (slash commands)…
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            viewModel.applyModelToSession(selectedModel, selectedEffort)
                            saving = false
                            Toast.makeText(context, "Applied to this chat", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = !saving && selectedModel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Applying…" else "Apply to this chat")
                }
                Spacer(Modifier.height(8.dp))
                // …and persist the pick for future chats.
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            saving = true
                            viewModel.saveModelPick(selectedModel, selectedEffort)
                            saving = false
                            Toast.makeText(context, "Saved — applies to new chats", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    enabled = !saving && selectedModel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save for new chats")
                }
            }
        }
    }
}
