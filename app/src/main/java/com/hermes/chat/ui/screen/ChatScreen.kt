package com.hermes.chat.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hermes.chat.ChatViewModel
import com.hermes.chat.model.AttachmentType
import com.hermes.chat.model.Message
import com.hermes.chat.model.MessageAttachment
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(chatState: ChatViewModel) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val pendingAttachments = remember { mutableStateListOf<MessageAttachment>() }
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            pendingAttachments.add(
                MessageAttachment(
                    type = AttachmentType.IMAGE,
                    uri = it.toString(),
                    displayName = "Image",
                )
            )
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver?.query(it, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) { cursor.moveToFirst(); cursor.getString(idx) } else null
            } ?: "Voice"
            pendingAttachments.add(
                MessageAttachment(
                    type = AttachmentType.VOICE,
                    uri = it.toString(),
                    displayName = name,
                )
            )
        }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chat",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = { chatState.clearMessages() },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Clear all messages",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
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
                    pendingAttachments.forEach { att ->
                        Text(
                            text = (if (att.type == AttachmentType.IMAGE) "🖼️ " else "🎤 ") + att.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // Input bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Image picker button
                IconButton(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = "Attach image",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                // Audio picker button
                IconButton(onClick = { audioPicker.launch("audio/*") }) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Attach voice",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                IconButton(onClick = { send() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary,
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

    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isUser)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isUser) 12.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 12.dp,
            ),
            color = color,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isPending) {
                    Text(
                        text = "\u23F3 Sending...",
                        color = MaterialTheme.colorScheme.primary,
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
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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
