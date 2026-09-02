package io.github.typenil.gametracker.feature.search

import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.SearchInputValidation

/**
 * UI State for the Search screen.
 *
 * @property query The raw text currently in the search input field.
 * @property searchActive Whether a valid, searchable query+filter combination is currently in
 * effect. Content/Empty/Error rendering is derived from the paged `LazyPagingItems` load states —
 * this flag only gates the whole container (idle suggestions vs paged result area).
 * @property inputValidation Validates the raw query against the search contract; invalid input is
 * never dispatched to the backend and is reported inline next to the text field.
 */
@androidx.compose.runtime.Immutable
data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val recentQueries: List<String> = emptyList(),
    val searchActive: Boolean = false,
    val inputValidation: SearchInputValidation = SearchInputValidation.Valid(""),
    val librarySnapshot: LibrarySnapshot = LibrarySnapshot.Ready(emptyMap()),
    val editingGameId: Long? = null,
    val isLibrarySubmitting: Boolean = false,
    val userMessageRes: Int? = null,
)
