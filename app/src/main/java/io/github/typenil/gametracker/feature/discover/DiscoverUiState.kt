package io.github.typenil.gametracker.feature.discover

import androidx.compose.runtime.Immutable
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game

/**
 * UI State for the Discover screen.
 */
@Immutable
sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState

    data class Success(
        val games: List<Game>,
        val isRefreshing: Boolean = false
    ) : DiscoverUiState

    data class Error(
        val error: AppError
    ) : DiscoverUiState
}
