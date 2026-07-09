// InsightsView.kt
@Composable
fun InsightsView(
    server: URL,
    onAPIError: (Error) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: InsightsViewModel = viewModel(
        factory = InsightsViewModelFactory(server, onAPIError)
    )
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(state.selectedTimeframe) {
        if (state.hasLoadedAnalytics && state.isLoading) {
            viewModel.loadInsights()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage Analytics") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshInsights() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading State
            item {
                if (state.isLoading && !state.hasLoadedAnalytics) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading analytics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Error State
            item {
                if (state.errorMessage != null && !state.hasLoadedAnalytics) {
                    ErrorStateCard(
                        message = state.errorMessage,
                        onRetry = { viewModel.refreshInsights() }
                    )
                }
            }
            
            // No Data State
            item {
                if (!state.hasLoadedAnalytics && !state.isLoading && state.errorMessage == null) {
                    NoDataStateCard()
                }
            }
            
            // Loaded Content
            if (state.hasLoadedAnalytics) {
                item {
                    // Timeframe Picker
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Timeframe",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            HorizontalPager(
                                count = AnalyticsTimeframe.values().size,
                                state = rememberHorizontalPagerState(
                                    initialPage = AnalyticsTimeframe.values().indexOf(viewModel.selectedTimeframe)
                                ),
                                modifier = Modifier.height(52.dp)
                            ) { page ->
                                val timeframe = AnalyticsTimeframe.values()[page]
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = timeframe.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (viewModel.selectedTimeframe == timeframe) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable { viewModel.selectTimeframe(timeframe) }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                    }
                }
                
                item {
                    Text(
                        text = state.periodTitle.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Analytics Cards
                item {
                    AnalyticsCardsSection(
                        sessionCount = state.sessionCount,
                        totalMessages = state.totalMessages,
                        totalInputTokens = state.totalInputTokens,
                        totalOutputTokens = state.totalOutputTokens,
                        totalTokens = state.totalTokens,
                        estimatedCost = state.estimatedCost,
                        totalCacheHitPercent = state.totalCacheHitPercent,
                        totalCacheReadTokens = state.totalCacheReadTokens
                    )
                }
                
                // Model Breakdown
                if (state.modelBreakdowns.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Models")
                        state.modelBreakdowns.take(10).forEach { model ->
                            ModelBreakdownRow(model = model)
                        }
                    }
                }
                
                // Recent Daily Tokens
                if (state.recentDailyTokens.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recent Daily Tokens")
                        state.recentDailyTokens.forEach { day ->
                            DailyTokenRow(day = day)
                        }
                    }
                }
                
                // Activity Summary
                if (state.peakDay != null || state.peakHour != null) {
                    item {
                        SectionHeader(title = "Activity")
                        
                        if (state.peakDay != null) {
                            ActivitySummaryRow(
                                icon = Icons.Default.CalendarToday,
                                title = "Peak Day",
                                value = state.peakDay.day ?: "Unknown",
                                detail = "${state.peakDay.sessionCount} sessions"
                            )
                        }
                        
                        if (state.peakHour != null) {
                            ActivitySummaryRow(
                                icon = Icons.Default.AccessTime,
                                title = "Peak Hour",
                                value = formatHour(state.peakHour.hour),
                                detail = "${state.peakHour.sessionCount} sessions"
                            )
                        }
                    }
                }
                
                // Top Sessions
                if (state.topSessions.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Top Sessions")
                        state.topSessions.take(10).forEach { session ->
                            SessionRow(session = session)
                        }
                    }
                }
            }
        }
    }
}