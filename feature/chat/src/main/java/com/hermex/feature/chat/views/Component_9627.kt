// src/main/java/com/chatapp/ui/theme/ChatScreen.kt

package com.chatapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.data.*
import com.chatapp.viewmodel.ChatViewModel
import com.chatapp.viewmodel.ActiveStreamRecoveryState
import com.chatapp.viewmodel.TranscriptMessage
import com.chatapp.ui.components.*
import com.chatapp.ui.theme.ChatTheme
import io.noties.markwon.Markwon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCancellingStream by viewModel.isCancellingStream.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val streamingScrollTrigger by viewModel.streamingScrollTrigger.collectAsState()
    val displayedTranscriptMessages by viewModel.displayedTranscriptMessages.collectAsState()
    val liveToolCalls by viewModel.liveToolCalls.collectAsState()
    val liveReasoningText by viewModel.liveReasoningText.collectAsState()
    val completedReasoningGroups by viewModel.completedReasoningGroups.collectAsState()
    val displayedReasoningGroups by viewModel.displayedReasoningGroups.collectAsState()
    val isViewingCachedData by viewModel.isViewingCachedData.collectAsState()
    val activeStreamRecoveryState by viewModel.activeStreamRecoveryState.collectAsState()
    val approvalPrompt by viewModel.approvalPrompt.collectAsState()
    val clarificationPrompt by viewModel.clarificationPrompt.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val isUploadingAttachment by viewModel.isUploadingAttachment.collectAsState()
    val uploadAttachmentErrorMessage by viewModel.uploadAttachmentErrorMessage.collectAsState()
    val compressionReferenceCard by viewModel.compressionReferenceCard.collectAsState()
    val displayTitle by viewModel.displayTitle.collectAsState()
    val selectedProfileName by viewModel.selectedProfileName.collectAsState()
    val selectedReasoningEffort by viewModel.selectedReasoningEffort.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(streamingScrollTrigger) {
        if (displayedTranscriptMessages.size > 0) {
            val lastItemIndex = displayedTranscriptMessages.size - 1
            listState.animateScrollToItem(lastItemIndex)
        }
    }

    // Handle cache reconcile scroll token
    LaunchedEffect(isViewingCachedData) {
        if (isViewingCachedData) {
            val lastItemIndex = displayedTranscriptMessages.size - 1
            listState.scrollToItem(lastItemIndex)
        }
    }

    ChatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Bar
                ChatTopBar(
                    title = displayTitle,
                    isLoading = isLoading,
                    isViewingCachedData = isViewingCachedData,
                    selectedProfileName = selectedProfileName,
                    onProfileChange = { viewModel.setProfileName(it) },
                    onClear = { viewModel.clearConversation() },
                    streamingRecoveryState = activeStreamRecoveryState
                )

                // Messages List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Compression Reference Card
                    if (compressionReferenceCard != null) {
                        item {
                            CompressionReferenceCard(
                                card = compressionReferenceCard!!,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Messages
                    items(displayedTranscriptMessages.size) { index ->
                        val message = displayedTranscriptMessages[index]
                        MessageItem(
                            message = message,
                            toolCalls = if (message is TranscriptMessage.Assistant) 
                                viewModel.completedToolCallGroupsForAnchor(message.message.id)
                            else emptyList(),
                            isLive = message is TranscriptMessage.Assistant && 
                                viewModel.completedToolCallGroupsForAnchor(message.message.id).isNotEmpty(),
                            reasoningGroups = if (message is TranscriptMessage.Assistant) {
                                completedReasoningGroups
                            } else emptyList(),
                            onToggleReasoning = { group ->
                                viewModel.toggleReasoningGroup(group)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Assistant Message (during streaming)
                    if (liveReasoningText.isNotEmpty()) {
                        item {
                            LiveAssistantMessage(
                                text = liveReasoningText,
                                toolCalls = liveToolCalls,
                                isCancelling = isCancellingStream,
                                onCancel = { viewModel.cancelStream() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Loading Indicator
                    if (isLoading) {
                        item {
                            LoadingIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Composer
                ChatComposer(
                    onSendMessage = { text, attachments ->
                        viewModel.sendMessage(text, attachments)
                    },
                    pendingAttachments = pendingAttachments,
                    onAddAttachment = { viewModel.addAttachment(it) },
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    isUploading = isUploadingAttachment,
                    uploadError = uploadAttachmentErrorMessage,
                    isCancelling = isCancellingStream,
                    onToggleReasoning = { group -> viewModel.toggleReasoningGroup(group) },
                    selectedReasoningEffort = selectedReasoningEffort,
                    onReasoningEffortChange = { viewModel.setReasoningEffort(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Error Dialog
            errorMessage?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.errorMessage.value = null },
                    title = Text("Error"),
                    text = Text(error),
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.errorMessage.value = null }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }

            // Approval Prompt
            approvalPrompt?.let { prompt ->
                ApprovalPromptDialog(
                    prompt = prompt,
                    onApprove = { viewModel.approveAction(prompt.id, "Approve") },
                    onCancel = { viewModel.approveAction(prompt.id, "Cancel") },
                    onDismiss = { viewModel.approveAction(prompt.id, "Cancel") }
                )
            }

            // Clarification Prompt
            clarificationPrompt?.let { prompt ->
                ClarificationPromptDialog(
                    prompt = prompt,
                    onClarify = { viewModel.clarifyQuestion(prompt.id, "Yes") },
                    onCancel = { viewModel.clarifyQuestion(prompt.id, "No") },
                    onDismiss = { viewModel.clarifyQuestion(prompt.id, "No") }
                )
            }
        }
    }
}