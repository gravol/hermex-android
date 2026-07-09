// MessageBubble.kt
@Composable
fun MessageBubble(
    message: ChatMessage,
    onMessageClick: (String) -> Unit,
    onAttachmentClick: (Uri) -> Unit
) {
    val isUser = message.role == ChatMessage.Role.USER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        // Avatar
        if (!isUser) {
            Avatar(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Message content
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(
                    if (isUser) {
                        Color(0xFF007AFF)
                    } else {
                        Color(0xFFE5E5EA)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onMessageClick(message.id) },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Attachments
            if (message.attachments.isNotEmpty()) {
                AttachmentList(
                    attachments = message.attachments,
                    onAttachmentClick = onAttachmentClick
                )
            }

            // Markdown content
            MarkdownText(
                content = message.content,
                modifier = Modifier.fillMaxWidth()
            )

            // Tool calls
            if (message.toolCalls.isNotEmpty()) {
                ToolCallCards(
                    toolCalls = message.toolCalls,
                    onToggle = { /* handle */ }
                )
            }

            // Reasoning blocks
            if (message.reasoningBlocks.isNotEmpty()) {
                ReasoningBlocks(
                    blocks = message.reasoningBlocks,
                    onToggle = onToggleReasoningBlock
                )
            }

            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isUser) Color.White else Color(0xFF666666)
            )
        }
    }
}

@Composable
fun AttachmentList(
    attachments: List<Attachment>,
    onAttachmentClick: (Uri) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentThumbnail(
                uri = attachment.uri,
                isUploading = attachment.isUploading,
                progress = attachment.progress,
                onClick = { onAttachmentClick(attachment.uri) }
            )
        }
    }
}

@Composable
fun AttachmentThumbnail(
    uri: Uri,
    isUploading: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    Box {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
        )

        if (isUploading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    // Use Markwon for markdown rendering
    // This would integrate with a markdown library
    MarkdownView(
        content = content,
        modifier = modifier
    )
}

@Composable
fun MarkdownView(
    content: String,
    modifier: Modifier
) {
    // Integration with Markwon library
    // For now, basic implementation
    Text(
        text = content,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ToolCallCards(
    toolCalls: List<ToolCall>,
    onToggle: (String) -> Unit
) {
    LazyColumn {
        items(toolCalls) { toolCall ->
            ToolCallCard(
                toolCall = toolCall,
                onToggle = onToggle
            )
        }
    }
}

@Composable
fun ToolCallCard(
    toolCall: ToolCall,
    onToggle: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isExpanded = !isExpanded
                onToggle(toolCall.id)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterForResource(R.drawable.baseline_tools_24),
                        contentDescription = toolCall.name,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = toolCall.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    painter = if (isExpanded) {
                        painterForResource(R.drawable.baseline_keyboard_arrow_up_24)
                    } else {
                        painterForResource(R.drawable.baseline_keyboard_arrow_down_24)
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = toolCall.arguments.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ReasoningBlocks(
    blocks: List<ReasoningBlock>,
    onToggle: (String) -> Unit
) {
    LazyColumn {
        items(blocks) { block ->
            ReasoningBlockItem(
                block = block,
                onToggle = onToggle
            )
        }
    }
}

@Composable
fun ReasoningBlockItem(
    block: ReasoningBlock,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onToggle(block.id)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Icon(
                    painter = if (block.isExpanded) {
                        painterForResource(R.drawable.baseline_keyboard_arrow_up_24)
                    } else {
                        painterForResource(R.drawable.baseline_keyboard_arrow_down_24)
                    },
                    contentDescription = if (block.isExpanded) "Collapse" else "Expand"
                )
            }

            if (block.isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = block.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}