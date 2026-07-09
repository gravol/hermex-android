LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(visibleEntries, key = { it.path ?: it.name }) { entry ->
            // ...
        }
    }.refreshable {
        onRefresh()
    }