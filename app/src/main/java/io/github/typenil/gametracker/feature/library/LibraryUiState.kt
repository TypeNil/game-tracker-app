package io.github.typenil.gametracker.feature.library

import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus

data class LibraryUiState(
    val allGames: List<LibraryGame> = emptyList(),
    val filteredGames: List<LibraryGame> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.ALL,
    val tabCounts: Map<LibraryTab, Int> = emptyMap(),
    val filterFavoritesOnly: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOption: LibrarySortOption = LibrarySortOption.UPDATED_DESC,
    val isLoading: Boolean = false,
    val userMessageRes: Int? = null,
) {
    val isCatalogEmpty: Boolean
        get() = !isLoading && allGames.isEmpty()

    val isSearchOrFilterActive: Boolean
        get() = searchQuery.isNotBlank() || filterFavoritesOnly

    val isFilteredEmpty: Boolean
        get() = !isLoading && filteredGames.isEmpty()

    fun gamesFor(tab: LibraryTab): List<LibraryGame> {
        return filterLibraryGames(allGames, tab, filterFavoritesOnly, searchQuery, sortOption)
    }
}

internal fun filterLibraryGames(
    allGames: List<LibraryGame>,
    selectedTab: LibraryTab,
    favoritesOnly: Boolean,
    query: String,
    sortOption: LibrarySortOption,
): List<LibraryGame> {
    val tabFiltered = when (selectedTab) {
        LibraryTab.ALL -> allGames.filter { it.entry.status != LibraryStatus.NOT_INTERESTED }
        LibraryTab.PLAYING -> allGames.filter { it.entry.status == LibraryStatus.PLAYING }
        LibraryTab.WISHLIST -> allGames.filter { it.entry.status == LibraryStatus.WISHLIST }
        LibraryTab.COMPLETED -> allGames.filter { it.entry.status == LibraryStatus.COMPLETED }
        LibraryTab.DROPPED -> allGames.filter { it.entry.status == LibraryStatus.DROPPED }
        LibraryTab.NOT_INTERESTED -> allGames.filter { it.entry.status == LibraryStatus.NOT_INTERESTED }
    }
    val favFiltered = if (favoritesOnly) {
        tabFiltered.filter { it.entry.isFavorite }
    } else {
        tabFiltered
    }
    val searchFiltered = if (query.isNotBlank()) {
        val trimmed = query.trim()
        favFiltered.filter { it.game.name.contains(trimmed, ignoreCase = true) }
    } else {
        favFiltered
    }
    return when (sortOption) {
        LibrarySortOption.UPDATED_DESC ->
            searchFiltered.sortedByDescending { it.entry.updatedAtEpochSeconds }
        LibrarySortOption.USER_RATING_DESC ->
            searchFiltered.sortedWith(
                compareByDescending<LibraryGame> { it.entry.userRating ?: -1 }
                    .thenByDescending { it.entry.updatedAtEpochSeconds }
            )
        LibrarySortOption.TITLE_ASC ->
            searchFiltered.sortedBy { it.game.name.lowercase() }
        LibrarySortOption.HOURS_PLAYED_DESC ->
            searchFiltered.sortedWith(
                compareByDescending<LibraryGame> { it.entry.hoursPlayed }
                    .thenByDescending { it.entry.updatedAtEpochSeconds }
            )
    }
}

