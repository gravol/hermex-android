package com.hermex.android.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermex.core.network.SessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).apply {
    timeZone = TimeZone.getDefault()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onSessionTap: (SessionSummary) -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: SessionsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pinnedIds by viewModel.pinnedIds.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }

    fun openSession(session: SessionSummary) {
        scope.launch { drawerState.close() }
        onSessionTap(session)
    }

    // Client-side search over the loaded list (mirrors desktop local filtering)
    val q = query.trim()
    val visible = remember(state.sessions, q) {
        if (q.isEmpty()) {
            state.sessions
        } else {
            state.sessions.filter { s ->
                (s.title ?: "").contains(q, ignoreCase = true) ||
                    (s.preview ?: "").contains(q, ignoreCase = true) ||
                    s.id.contains(q, ignoreCase = true)
            }
        }
    }
    val pinned = visible.filter { it.id in pinnedIds }
    val unpinned = visible.filter { it.id !in pinnedIds }
    val groups = remember(unpinned) {
        unpinned
            .groupBy { (it.source ?: "other").uppercase().ifEmpty { "OTHER" } }
            .entries
            .sortedByDescending { it.value.size }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Header
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(
                            text = "Hermex",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${state.sessions.size} sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // New session
                    ListItem(
                        headlineContent = { Text("New session") },
                        leadingContent = {
                            Icon(Icons.Default.Add, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            if (!state.isCreating) {
                                viewModel.createSession { sid ->
                                    if (sid != null) {
                                        openSession(SessionSummary(id = sid, title = "New session"))
                                    }
                                }
                            }
                        },
                    )

                    // Search
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search sessions…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.large,
                    )

                    HorizontalDivider()

                    // Pinned
                    if (pinned.isNotEmpty()) {
                        SectionHeader("PINNED (${pinned.size})")
                        pinned.forEach { session ->
                            DrawerSessionRow(
                                session = session,
                                pinned = true,
                                onClick = { openSession(session) },
                                onTogglePin = { viewModel.togglePin(session.id) },
                            )
                        }
                        HorizontalDivider()
                    }

                    // Source groups
                    groups.forEach { (source, sessions) ->
                        SectionHeader("$source (${sessions.size})")
                        sessions.forEach { session ->
                            DrawerSessionRow(
                                session = session,
                                pinned = false,
                                onClick = { openSession(session) },
                                onTogglePin = { viewModel.togglePin(session.id) },
                            )
                        }
                        HorizontalDivider()
                    }

                    if (visible.isEmpty() && q.isNotEmpty()) {
                        Text(
                            text = "No matches for \"$q\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Hermex") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = viewModel::loadSessions, enabled = !state.isLoading) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when {
                    state.error != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = viewModel::loadSessions) {
                                Text("Retry")
                            }
                        }
                    }
                    visible.isEmpty() && !state.isLoading && q.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No sessions yet", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    else -> {
                        LazyColumn {
                            if (pinned.isNotEmpty()) {
                                item { SectionHeader("PINNED (${pinned.size})") }
                                items(pinned, key = { it.id }) { session ->
                                    SessionRow(
                                        session = session,
                                        pinned = true,
                                        onClick = { onSessionTap(session) },
                                        onTogglePin = { viewModel.togglePin(session.id) },
                                    )
                                }
                            }
                            groups.forEach { (source, sessions) ->
                                item { SectionHeader("$source (${sessions.size})") }
                                items(sessions, key = { it.id }) { session ->
                                    SessionRow(
                                        session = session,
                                        pinned = false,
                                        onClick = { onSessionTap(session) },
                                        onTogglePin = { viewModel.togglePin(session.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = session.title ?: session.id.take(16),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    session.startedAt?.let { ts ->
                        Text(
                            text = dateFormat.format(Date((ts * 1000).toLong())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                session.preview?.let { p ->
                    if (p.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = p,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    session.model?.let { m ->
                        Text(
                            text = m,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "${session.messageCount} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    session.source?.let { src ->
                        Text(
                            text = src,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Unpin" else "Pin",
                    tint = if (pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DrawerSessionRow(
    session: SessionSummary,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title ?: session.id.take(16),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            session.preview?.let { p ->
                if (p.isNotBlank()) {
                    Text(
                        text = p,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onTogglePin) {
            Icon(
                imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (pinned) "Unpin" else "Pin",
                tint = if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
