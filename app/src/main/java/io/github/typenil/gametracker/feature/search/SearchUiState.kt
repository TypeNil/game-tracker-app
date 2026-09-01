package io.github.typenil.gametracker.feature.search

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibrarySnapshot


/**
 * UI State for the Search screen.
 *
 * @property query The raw text currently in the search input field.
 * @property result The distinct lifecycle result of the active search operation.
 */
@androidx.compose.runtime.Immutable
data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val recentQueries: List<String> = emptyList(),
    val result: SearchResultUiState = SearchResultUiState.Idle,
    val librarySnapshot: LibrarySnapshot = LibrarySnapshot.Ready(emptyMap()),
    val editingGameId: Long? = null,
    val isLibrarySubmitting: Boolean = false,
    val userMessageRes: Int? = null,
)

/**
 * Single source of truth for search result lifecycle states, preventing contradictory states.
 */
sealed interface SearchResultUiState {
    data object Idle : SearchResultUiState
    data object Loading : SearchResultUiState
    data class Content(
        val games: List<Game>,
        val refreshError: AppError? = null,
    ) : SearchResultUiState
    data class Empty(val query: String, val hasConstraints: Boolean = false) : SearchResultUiState
    data class Error(val error: AppError) : SearchResultUiState
}
