package io.github.typenil.gametracker.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.FeedSkeleton
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.library.LibraryTab
import io.github.typenil.gametracker.feature.library.LibraryUiState

@Composable
fun LibraryTabBody(
    uiState: LibraryUiState,
    pagerState: PagerState,
    onGameClick: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onClearSearch: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onStatusSelected: (Long, LibraryStatus) -> Unit,
    onHoursClick: (Long) -> Unit,
    onNotesClick: (Long) -> Unit,
    onCardVisible: (LibraryGame) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> {
            FeedSkeleton(
                label = stringResource(R.string.library_loading),
                modifier = modifier.fillMaxSize(),
            )
        }

        uiState.error != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error.errorMessage(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(GtDimens.Gutter),
                )
            }
        }

        uiState.isCatalogEmpty -> {
            LibraryEmptyState(
                onNavigateToDiscover = onNavigateToDiscover,
                modifier = modifier.fillMaxSize()
            )
        }

        else -> {
            LibraryPager(
                pagerState = pagerState,
                uiState = uiState,
                onGameClick = onGameClick,
                onNavigateToDiscover = onNavigateToDiscover,
                onClearSearch = onClearSearch,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
                onToggleFavorite = onToggleFavorite,
                onStatusSelected = onStatusSelected,
                onHoursClick = onHoursClick,
                onNotesClick = onNotesClick,
                onCardVisible = onCardVisible,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun LibraryPager(
    pagerState: PagerState,
    uiState: LibraryUiState,
    onGameClick: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onClearSearch: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onStatusSelected: (Long, LibraryStatus) -> Unit,
    onHoursClick: (Long) -> Unit,
    onNotesClick: (Long) -> Unit,
    onCardVisible: (LibraryGame) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .testTag("library_pager"),
    ) { page ->
        val tab = LibraryTab.entries[page]
        val pageGames = uiState.gamesFor(tab)
        if (pageGames.isEmpty()) {
            if (uiState.isSearchOrFilterActive) {
                LibrarySearchEmptyState(
                    onResetSearchAndFilters = {
                        onClearSearch()
                        if (uiState.filterFavoritesOnly) {
                            onToggleFavoritesOnly()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LibraryTabEmptyState(
                    onNavigateToDiscover = onNavigateToDiscover,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(GtDimens.Gutter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = pageGames, key = { it.game.id }) { item ->
                    LaunchedEffect(item.game.id) {
                        onCardVisible(item)
                    }
                    LibraryGameCard(
                        libraryGame = item,
                        onClick = { onGameClick(item.game.id) },
                        onFavoriteClick = { onToggleFavorite(item.game.id) },
                        onStatusSelected = { status ->
                            onStatusSelected(item.game.id, status)
                        },
                        onHoursClick = {
                            onHoursClick(item.game.id)
                        },
                        onNotesClick = {
                            onNotesClick(item.game.id)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
