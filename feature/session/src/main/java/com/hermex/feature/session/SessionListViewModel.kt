@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel
) {
    val uiState by viewModel.sessions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshState = rememberPullToRefreshState()

    // Group sessions by date
    val groupedSessions = remember(uiState) {
        uiState.groupBy { session ->
            // Group by day (Date object)
            java.time.LocalDate.ofInstant(
                java.time.Instant.ofEpochMilli(session.date),
                java.time.ZoneId.systemDefault()
            )
        }.toSortedMap().toList().map { (date, sessions) ->
            DateHeader(date = date) to sessions
        }
    }

    PullToRefreshContainer(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        refreshState = refreshState,
        onRefresh = { viewModel.loadSessions() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            groupedSessions.forEach { (dateHeader, sessions) ->
                item { DateHeader(date = dateHeader) }
                items(sessions) { session ->
                    SessionRow(
                        session = session,
                        query = searchQuery,
                        onSwipeDelete = { viewModel.confirmDelete(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PullToRefreshContainer(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    refreshState: PullToRefreshState,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        
        PullToRefreshIndicator(
            refreshing = isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        DisposableEffect(refreshState) {
            val listener = remember {
                object : OnRefreshListener {
                    override fun onRefresh() {
                        onRefresh()
                    }
                }
            }
            refreshState.addOnRefreshListener(listener)
            onDispose {
                refreshState.removeOnRefreshListener(listener)
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    query: String,
    onSwipeDelete: () -> Unit
) {
    var swiped by remember { mutableStateOf(false) }
    val swipeableState = rememberSwipeableState(
        anchorMap = mapOf(
            SwipeableDefaults.Anchor(1f) to SwipeableDefaults.Action.Right,
            SwipeableDefaults.Anchor(0f) to SwipeableDefaults.Action.None
        )
    )

    SwipeToDismiss(
        state = swipeableState,
        background = {
            SwipeBackground(swipeDirection = it)
        },
        dismissContent = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatSessionDate(session.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun SwipeBackground(direction: SwipeDirection) {
    val color = when (direction) {
        SwipeDirection.Start -> MaterialTheme.colorScheme.error
        SwipeDirection.End -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp),
        contentAlignment = when (direction) {
            SwipeDirection.Start -> Alignment.CenterStart
            SwipeDirection.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Text(
            text = "Delete",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun DateHeader(date: java.time.LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDate(date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// Helper functions
private fun formatDate(date: java.time.LocalDate): String {
    return if (date == java.time.LocalDate.now()) "Today" else date.toString()
}
private fun formatSessionDate(date: Long): String {
    return java.time.Instant.ofEpochMilli(date).toString().substring(0, 10)
}