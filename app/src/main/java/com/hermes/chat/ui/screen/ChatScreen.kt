package com.hermes.chat.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hermes.chat.ChatViewModel
import com.hermes.chat.model.AttachmentType
import com.hermes.chat.model.Message
import com.hermes.chat.model.MessageAttachment
import com.hermes.chat.ui.theme.TelegramChatColors
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    chatState: ChatViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    val pendingAttachments = remember { mutableStateListOf<MessageAttachment>() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val recordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            chatState.addSystem("Microphone permission is needed to record voice notes.")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            pendingAttachments.add(
                MessageAttachment(
                    type = AttachmentType.IMAGE,
                    uri = it.toString(),
                    displayName = "Image",
                    mimeType = context.contentResolver.getType(it) ?: "image/jpeg",
                )
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured: Boolean ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (captured && uri != null) {
            pendingAttachments.add(
                MessageAttachment(
                    type = AttachmentType.IMAGE,
                    uri = uri.toString(),
                    displayName = "Camera photo",
                    mimeType = "image/jpeg",
                )
            )
        }
    }

    fun launchCamera() {
        val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver?.query(it, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) { cursor.moveToFirst(); cursor.getString(idx) } else null
            } ?: "File"
            pendingAttachments.add(
                MessageAttachment(
                    type = AttachmentType.FILE,
                    uri = it.toString(),
                    displayName = name,
                )
            )
        }
    }

    fun startVoiceRecording() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (activeRecorder != null) return

        val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val recorder = MediaRecorder()
        runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
            activeRecorder = recorder
            activeRecordingFile = outputFile
            recordingStartedAt = System.currentTimeMillis()
            isRecordingVoice = true
        }.onFailure {
            runCatching { recorder.release() }
            outputFile.delete()
            activeRecorder = null
            activeRecordingFile = null
            isRecordingVoice = false
            chatState.addSystem("Could not start voice recording.")
        }
    }

    fun stopVoiceRecording() {
        val recorder = activeRecorder ?: return
        val outputFile = activeRecordingFile
        val durationMs = System.currentTimeMillis() - recordingStartedAt
        activeRecorder = null
        activeRecordingFile = null
        isRecordingVoice = false

        val stopped = runCatching {
            recorder.stop()
            recorder.release()
        }.isSuccess

        if (!stopped || outputFile == null || durationMs < 600L || outputFile.length() == 0L) {
            runCatching { recorder.release() }
            outputFile?.delete()
            chatState.addSystem("Voice note was too short to attach.")
            return
        }

        pendingAttachments.add(
            MessageAttachment(
                type = AttachmentType.VOICE,
                uri = Uri.fromFile(outputFile).toString(),
                displayName = "Voice note ${durationMs / 1000}s",
                mimeType = "audio/mp4",
            )
        )
    }

    fun send() {
        val text = inputText.trim()
        if (text.isBlank() && pendingAttachments.isEmpty()) return
        val atts = pendingAttachments.toList()
        chatState.sendMessage(text, atts)
        inputText = ""
        pendingAttachments.clear()
        scope.launch {
            if (chatState.messages.isNotEmpty()) {
                listState.animateScrollToItem(chatState.messages.lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelegramChatColors.DarkCanvas),
    ) {
        Surface(
            color = TelegramChatColors.DarkTopBar,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hermes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Online · ${chatState.selectedModelLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TelegramChatColors.Blue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { chatState.clearMessages() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Clear all messages",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = chatState.messages, key = { it.id }) { message ->
                val index = chatState.messages.indexOf(message)
                when {
                    message.isSystem -> SystemMessage(text = message.text)
                    else -> MessageBubble(message = message, chatState = chatState, index = index)
                }
            }
        }

        // Retry-all banner when messages are queued
        if (chatState.failedMessageIndices.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { chatState.retryAllFailed() })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "\u26A0\uFE0F ${chatState.failedMessageIndices.size} message(s) failed \u2014 Tap to retry all",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // Pending attachment chips
        if (pendingAttachments.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingAttachments.toList().forEach { att ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = TelegramChatColors.DarkComposerField,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = when (att.type) {
                                        AttachmentType.IMAGE -> "🖼️ ${att.displayName}"
                                        AttachmentType.VOICE -> "🎤 ${att.displayName}"
                                        AttachmentType.FILE -> "📎 ${att.displayName}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp),
                                )
                                IconButton(
                                    onClick = {
                                        pendingAttachments.remove(att)
                                        if (att.type == AttachmentType.VOICE) {
                                            runCatching { Uri.parse(att.uri).path?.let { File(it).delete() } }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove attachment",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isRecordingVoice) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "● Recording voice note — release mic to attach",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // Input bar
        Surface(
            tonalElevation = 0.dp,
            color = TelegramChatColors.DarkComposer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Attachment paperclip: photo / voice / file
                Box {
                    IconButton(
                        onClick = { attachmentMenuExpanded = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "Open attachment menu",
                            tint = TelegramChatColors.Blue,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = attachmentMenuExpanded,
                        onDismissRequest = { attachmentMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Choose photo") },
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                            onClick = {
                                attachmentMenuExpanded = false
                                imagePicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Take picture") },
                            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                            onClick = {
                                attachmentMenuExpanded = false
                                launchCamera()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Upload file") },
                            leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                            onClick = {
                                attachmentMenuExpanded = false
                                filePicker.launch("*/*")
                            },
                        )
                    }
                }

                // App menu: settings/devices/logs when bottom tabs are tucked away.
                Box {
                    IconButton(
                        onClick = { appMenuExpanded = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Open app menu",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = appMenuExpanded,
                        onDismissRequest = { appMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                appMenuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Devices") },
                            onClick = {
                                appMenuExpanded = false
                                onOpenDevices()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Logs") },
                            onClick = {
                                appMenuExpanded = false
                                onOpenLogs()
                            },
                        )
                    }
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 128.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TelegramChatColors.DarkComposerField)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    singleLine = false,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(TelegramChatColors.Blue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (inputText.isBlank()) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                val micBubbleSize by animateDpAsState(
                    targetValue = if (isRecordingVoice) 68.dp else 40.dp,
                    label = "micBubbleSize",
                )
                val micIconSize by animateDpAsState(
                    targetValue = if (isRecordingVoice) 34.dp else 22.dp,
                    label = "micIconSize",
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    startVoiceRecording()
                                    tryAwaitRelease()
                                    stopVoiceRecording()
                                }
                            )
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (isRecordingVoice) {
                        Surface(
                            modifier = Modifier
                                .size(micBubbleSize)
                                .offset(y = (-18).dp),
                            shape = RoundedCornerShape(34.dp),
                            color = MaterialTheme.colorScheme.error,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Recording voice note",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(micIconSize),
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Hold to record voice note",
                            tint = TelegramChatColors.Blue,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(micIconSize),
                        )
                    }
                }
                IconButton(
                    onClick = { send() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = TelegramChatColors.Blue,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageBubble(message: Message, chatState: ChatViewModel, index: Int) {
    val isFailed = index in chatState.failedMessageIndices
    val isPending = message.isAssistant && message.text == "..." && message.attachments.isEmpty()
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isUser)
        TelegramChatColors.DarkOutgoingBubble
    else
        TelegramChatColors.DarkIncomingBubble

    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 18.dp,
            ),
            color = color,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (!isFailed && !isPending && message.text.isNotBlank())
                            Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(message.text))
                                showCopied = true
                            }
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isPending) {
                    Text(
                        text = "\u23F3 Sending...",
                        color = TelegramChatColors.Blue,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (isFailed) {
                    Text(
                        text = "\u26A0\uFE0F Failed \u2014 tap to retry",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { chatState.retryMessage(index) },
                    )
                } else {
                    // Render attachments
                    message.attachments.forEach { att ->
                        when (att.type) {
                            AttachmentType.IMAGE -> ImageAttachment(att)
                            AttachmentType.VOICE -> VoiceAttachment(att)
                            AttachmentType.FILE -> FileAttachment(att)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (message.text.isNotBlank()) {
                        FormattedMessageText(
                            text = message.text,
                            color = textColor,
                        )
                    }
                }

                if (showCopied) {
                    Text(
                        text = "Copied",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelegramChatColors.Blue,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageAttachment(attachment: MessageAttachment) {
    val context = LocalContext.current
    val model = ImageRequest.Builder(context)
        .data(attachment.uri)
        .crossfade(true)
        .build()

    AsyncImage(
        model = model,
        contentDescription = attachment.displayName,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun VoiceAttachment(attachment: MessageAttachment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "🎤",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileAttachment(attachment: MessageAttachment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "📎",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
