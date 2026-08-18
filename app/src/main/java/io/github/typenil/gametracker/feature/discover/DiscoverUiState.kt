package io.github.typenil.gametracker.feature.discover

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game

/**
 * UI State for the Discover screen.
 */
data class DiscoverUiState(
    val games: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val userMessageRes: Int? = null
) {
    val isInitialLoading: Boolean
        get() = isLoading && games.isEmpty()
}
