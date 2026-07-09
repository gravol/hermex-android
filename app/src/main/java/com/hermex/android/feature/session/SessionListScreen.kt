package com.hermex.android.feature.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    modifier: Modifier = Modifier,
    onSessionClick: (String) -> Unit = {}
) {
    SessionListScreenContent(
        viewModel = viewModel,
        modifier = modifier,
        onSessionClick = onSessionClick
    )
}
