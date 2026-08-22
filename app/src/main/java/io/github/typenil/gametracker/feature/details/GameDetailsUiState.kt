package io.github.typenil.gametracker.feature.details

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameDetails

/**
 * UI State for the Game Details screen.
 *
 * [game] may be a hydrated model or a catalog skeleton (see GameRepository):
 * a skeleton renders the header with the critic rating and hides empty sections.
 * [isHydrated] distinguishes the two without leaking cache internals into the
 * domain model and powers the eviction guard in the ViewModel.
 */
data class GameDetailsUiState(
    val game: GameDetails? = null,
    val isHydrated: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val userMessageRes: Int? = null
) {
    val isInitialLoading: Boolean
        get() = isLoading && game == null
}
