package io.github.typenil.gametracker.devtools.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Binds [DevToolsViewModel] to [DevToolsScreen]. Kept separate so the screen itself stays a pure
 * function of its state and callbacks.
 */
@Composable
internal fun DevToolsRoute(
    viewModel: DevToolsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DevToolsScreen(
        uiState = uiState,
        onSeed = viewModel::seed,
        onWipe = viewModel::wipe,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
