package com.hermex.android.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermex.android.feature.chat.ui.ChatViewModel
import com.hermex.android.feature.chat.ui.MessageCard
import com.hermex.android.feature.chat.ui.MarkdownRenderer
import com.hermex.android.feature.chat.ui.ReasoningBlock
import com.hermex.android.feature.chat.ui.ToolCallCard
import com.hermex.android.feature.chat.ui.Composer

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
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
