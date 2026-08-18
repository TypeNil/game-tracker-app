package io.github.typenil.gametracker.feature.discover.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.github.typenil.gametracker.feature.discover.DiscoverScreen
import io.github.typenil.gametracker.feature.discover.DiscoverViewModel

/**
 * Extension for navigating to the Discover screen.
 */
fun NavController.navigateToDiscover(navOptions: NavOptions? = null) {
    navigate(route = DiscoverKey, navOptions = navOptions)
}

/**
 * Registers the Discover destination in the type-safe [NavGraphBuilder].
 */
fun NavGraphBuilder.discoverEntry(
    onGameClick: (Long) -> Unit,
    onSearchClick: () -> Unit
) {
    composable<DiscoverKey> {
        val viewModel: DiscoverViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        DiscoverScreen(
            uiState = uiState,
            onGameClick = onGameClick,
            onSearchClick = onSearchClick,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onUserMessageShown = viewModel::onUserMessageShown
        )
    }
}
