package io.github.typenil.gametracker.feature.library

import io.github.typenil.gametracker.core.model.LibraryGame

/**
 * UI State for the Library Screen.
 */
data class LibraryUiState(
    val allGames: List<LibraryGame> = emptyList(),
    val filteredGames: List<LibraryGame> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.ALL,
    val tabCounts: Map<LibraryTab, Int> = emptyMap(),
    val filterFavoritesOnly: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOption: LibrarySortOption = LibrarySortOption.UPDATED_DESC,
    val isLoading: Boolean = false
) {
    val isCatalogEmpty: Boolean
        get() = !isLoading && allGames.isEmpty()

    val isSearchOrFilterActive: Boolean
        get() = searchQuery.isNotBlank() || filterFavoritesOnly

    val isFilteredEmpty: Boolean
        get() = !isLoading && filteredGames.isEmpty()
}
