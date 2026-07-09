import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*
import java.net.URL

@Composable
fun MemoryView(
    server: URL,
    onApiError: (Error) -> Unit = {},
    viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.Factory(server, onApiError))
) {
    val state by viewModel.state.collectAsState()
    var editingSection by remember { mutableStateOf<MemorySection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadMemory() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        MemoryContent(
            state = state,
            modifier = Modifier.padding(padding),
            onRefresh = { viewModel.loadMemory() },
            onEdit = { section -> editingSection = section },
            onDismiss = { editingSection = null }
        )

        if (editingSection != null) {
            MemoryEditSheet(
                section = editingSection,
                initialContent = state.sections[editingSection] ?: "",
                isSaving = state.isSaving,
                errorMessage = state.actionErrorMessage,
                onDismiss = { editingSection = null },
                onSave = { content ->
                    editingSection?.let { viewModel.save(it, content) }
                    // We rely on the state flow to detect success/failure, 
                    // but for UI flow we just dismiss on save attempt
                }
            )
        }
    }
}

@Composable
private fun MemoryContent(
    state: MemoryState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onEdit: (MemorySection) -> Unit
) {
    if (state.isLoading && !state.hasLoaded) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading memory...")
        }
    } else if (state.errorMessage != null && !state.hasLoaded) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Could Not Load Memory",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRefresh) {
                Text("Try Again")
            }
        }
    } else if (!state.hasLoaded) {
        // Fallback loading state
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading memory...")
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(MemorySection.values()) { section ->
                MemorySectionCard(
                    section = section,
                    content = state.sections[section] ?: "",
                    modifiedAt = state.modifiedAts[section],
                    isSaving = state.isSaving,
                    onEdit = {
                        state.actionErrorMessage?.let { _ -> } // Clear error if needed
                        onEdit(section)
                    }
                )
            }
        }
        
        // Refreshable wrapper
        SwipeToRefresh(
            state = rememberSwipeToRefreshState(),
            onRefresh = onRefresh,
            modifier = modifier
        ) {
            // Note: SwipeToRefresh usually wraps the LazyColumn directly in the parent
            // To keep it simple, we rely on the LazyColumn inside, 
            // but for strict refreshable behavior, we'd wrap the LazyColumn.
            // For this port, we assume the LazyColumn is the content.
            // To properly enable refreshable, we move the LazyColumn inside SwipeToRefresh
            // but since we are in a Scaffold, we can just wrap the content here.
            // However, to match the code structure above, we'll trust the LazyColumn 
            // or wrap the whole block if needed. 
            // Correct approach for SwipeToRefresh in Compose:
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(MemorySection.values()) { section ->
                    MemorySectionCard(
                        section = section,
                        content = state.sections[section] ?: "",
                        modifiedAt = state.modifiedAts[section],
                        isSaving = state.isSaving,
                        onEdit = { onEdit(section) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySectionCard(
    section: MemorySection,
    content: String,
    modifiedAt: Date?,
    isSaving: Boolean,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Note, // Or use section specific icon
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (modifiedAt != null) {
                        Text(
                            text = "Modified ${formatRelativeDate(modifiedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onEdit,
                    enabled = !isSaving
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit ${section.title}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            if (content.trim().isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace, // Monospace for code-like memory
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            } else {
                Text(
                    text = section.emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemoryEditSheet(
    section: MemorySection?,
    initialContent: String,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val content = remember { mutableStateOf(initialContent) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Edit ${section?.title}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "Content",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = content.value,
                onValueChange = { content.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(bottom = 16.dp),
                placeholder = { Text("Enter content...") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                enabled = !isSaving,
                singleLine = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(content.value) },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Save")
                }
            }
        }
    }
}

private fun formatRelativeDate(date: Date): String {
    val now = Date()
    val diff = (now.time - date.time) / 1000
    
    val seconds = diff
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
        else -> "Just now"
    }
}