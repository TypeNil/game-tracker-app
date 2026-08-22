package io.github.typenil.gametracker.feature.details

import androidx.annotation.StringRes
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.LibraryEntry

/**
 * Internal state container combining UI flags for the Game Details screen.
 */
data class DetailsInternalFlags(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isEditingLibrary: Boolean = false,
    val message: Pair<AppError?, Int?>? = null
)

/**
 * UI State for the Game Details screen.
 *
 * [game] may be a hydrated model or a catalog skeleton (see GameRepository):
 * a skeleton renders the header with the critic rating and hides empty sections.
 * [isHydrated] distinguishes the two without leaking cache internals into the
 * domain model and powers the eviction guard in the ViewModel.
 * [libraryEntry] represents the user's personal tracking status, ratings, and notes for this game.
 */
data class GameDetailsUiState(
    val game: GameDetails? = null,
    val libraryEntry: LibraryEntry? = null,
    val isHydrated: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEditingLibrary: Boolean = false,
    val error: AppError? = null,
    @StringRes val userMessageRes: Int? = null
) {
    val isInitialLoading: Boolean
        get() = isLoading && game == null
}
