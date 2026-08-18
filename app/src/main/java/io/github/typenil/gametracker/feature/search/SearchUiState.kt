package io.github.typenil.gametracker.feature.search

import androidx.compose.runtime.Immutable
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game

/**
 * UI State for the Search screen.
 */
@Immutable
data class SearchUiState(
    val query: String = "",
    val games: List<Game> = emptyList(),
    val status: SearchStatus = SearchStatus.Idle
)

/**
 * Single source of truth for screen status, eliminating contradictory boolean states.
 */
sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object Loading : SearchStatus
    data object Content : SearchStatus
    data object Empty : SearchStatus
    data class Error(val error: AppError) : SearchStatus
}
