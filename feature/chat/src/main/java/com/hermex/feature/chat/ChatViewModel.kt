// ChatScreen.kt
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            val isStreaming = viewModel.messages.any { it.isStreaming }
            Composer(
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopStreaming,
                onAttach = {},
                isStreaming = isStreaming,
                colorScheme = colorScheme
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            reverseLayout = false,
            state = rememberLazyListState().apply {
                // Auto-scroll to bottom
                if (viewModel.messages.isNotEmpty()) {
                    scrollToItem(viewModel.messages.lastIndex)
                }
            }
        ) {
            items(viewModel.messages) { message ->
                MessageCard(
                    message = message,
                    colorScheme = colorScheme,
                    onToggleReasoning = { id ->
                        viewModel.toggleReasoning(id)
                    },
                    onExecuteTool = { /* Handle tool execution */ },
                    onDismissTool = { /* Handle tool dismissal */ }
                )
            }
        }
    }
}

@Composable
fun MessageCard(
    message: Message,
    colorScheme: ColorScheme,
    onToggleReasoning: (String) -> Unit,
    onExecuteTool: (ToolCall) -> Unit,
    onDismissTool: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == Role.USER
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (isUser) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 300.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colorScheme.primary
                    )
                ) {
                    MarkdownRenderer(
                        content = message.content,
                        isStreaming = message.isStreaming,
                        colorScheme = colorScheme,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 300.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Reasoning Block
                        message.reasoning?.let { reasoning ->
                            ReasoningBlock(
                                reasoning = reasoning,
                                onToggle = { onToggleReasoning(reasoning.id) },
                                colorScheme = colorScheme
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Main Content
                        MarkdownRenderer(
                            content = message.content,
                            isStreaming = message.isStreaming,
                            colorScheme = colorScheme,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Tool Calls
                        message.toolCalls.forEach { toolCall ->
                            Spacer(modifier = Modifier.height(8.dp))
                            ToolCallCard(
                                toolCall = toolCall,
                                onExecute = { onExecuteTool(toolCall) },
                                onDismiss = { onDismissTool(toolCall.id) },
                                colorScheme = colorScheme
                            )
                        }
                    }
                }
            }
        }
    }
}