package io.github.typenil.gametracker.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.theme.topLevelBottomInset
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.feature.search.component.RecentQueriesList
import io.github.typenil.gametracker.feature.search.component.SearchEmptyState
import io.github.typenil.gametracker.feature.search.component.SearchErrorState
import io.github.typenil.gametracker.feature.search.component.SearchFilterBar
import io.github.typenil.gametracker.feature.search.component.SearchFilterSheet
import io.github.typenil.gametracker.feature.search.component.SearchLoadingState
import io.github.typenil.gametracker.feature.search.component.SearchReconnectEffect
import io.github.typenil.gametracker.feature.search.component.SearchResultsList
import io.github.typenil.gametracker.feature.search.component.SearchTopBar
import io.github.typenil.gametracker.feature.search.component.toPresentedAppError
import kotlinx.coroutines.flow.Flow

@Composable
fun SearchRoute(
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopTrigger: Long = 0L,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        networkStatus = networkStatus,
        searchResults = viewModel.searchResults,
        onQueryChange = viewModel::onQueryChanged,
        onClearQuery = viewModel::onClearQuery,
        onGameClick = onGameClick,
        onToggleGenre = viewModel::onGenreToggled,
        onTogglePlatform = viewModel::onPlatformToggled,
        onRemoveReleaseYear = { viewModel.onReleaseYearSelected(ReleaseYearFilter.ALL) },
        onToggleMinRating = { rating ->
            if (uiState.filters.minRating == rating) {
                viewModel.onMinRatingSelected(MinRatingFilter.ANY)
            } else {
                viewModel.onMinRatingSelected(rating)
            }
        },
        onResetFilters = viewModel::onResetFilters,
        onApplyFilters = viewModel::onApplyFilters,
        onSelectRecentQuery = viewModel::onSelectRecentQuery,
        onRemoveRecentQuery = viewModel::onRemoveRecentQuery,
        onClearAllRecentQueries = viewModel::onClearAllRecentQueries,
        onLibraryAction = viewModel::onLibraryCardAction,
        onSaveLibraryEntry = viewModel::onSaveLibraryEntry,
        onRemoveFromLibrary = viewModel::onRemoveFromLibrary,
        onDismissEditLibrary = viewModel::onDismissEditLibrary,
        onUserMessageShown = viewModel::onUserMessageShown,
        scrollToTopTrigger = scrollToTopTrigger,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    searchResults: Flow<PagingData<Game>>,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onGameClick: (Long) -> Unit,
    onToggleGenre: (String) -> Unit = {},
    onTogglePlatform: (PlatformFamily) -> Unit = {},
    onRemoveReleaseYear: () -> Unit = {},
    onToggleMinRating: (MinRatingFilter) -> Unit = {},
    onResetFilters: () -> Unit = {},
    onApplyFilters: (SearchFilters) -> Unit = {},
    onSelectRecentQuery: (String) -> Unit = {},
    onRemoveRecentQuery: (String) -> Unit = {},
    onClearAllRecentQueries: () -> Unit = {},
    onLibraryAction: (Game) -> Unit = {},
    onSaveLibraryEntry: (Long, LibraryStatus, Int?, Int, String?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRemoveFromLibrary: (Long) -> Unit = {},
    onDismissEditLibrary: () -> Unit = {},
    onUserMessageShown: () -> Unit = {},
    networkStatus: NetworkStatus = NetworkStatus.Unknown,
    scrollToTopTrigger: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isFilterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val lazyItems = searchResults.collectAsLazyPagingItems()

    SearchReconnectEffect(
        networkStatus = networkStatus,
        lazyItems = lazyItems,
    )

    val userMessage = uiState.userMessageRes?.let { stringResource(it) }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onUserMessageShown()
        }
    }
    val readyLibrary = uiState.librarySnapshot as? LibrarySnapshot.Ready
    val editingEntry = uiState.editingGameId?.let { readyLibrary?.entries?.get(it) }
    val focusRequester = remember { FocusRequester() }
    val resultsListState = rememberLazyListState()
    val recentListState = rememberLazyListState()
    var lastHandledScrollToTopTrigger by rememberSaveable {
        mutableLongStateOf(scrollToTopTrigger)
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0L && scrollToTopTrigger != lastHandledScrollToTopTrigger) {
            lastHandledScrollToTopTrigger = scrollToTopTrigger
            if (uiState.searchActive) {
                resultsListState.scrollToItem(0)
            } else {
                recentListState.scrollToItem(0)
            }
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = topLevelBottomInset()),
            )
        },
        topBar = {
            SearchTopBar(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                inputValidation = uiState.inputValidation,
                focusRequester = focusRequester,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = topLevelBottomInset(),
                ),
        ) {
            // Horizontal Filter & Sort Bar
            SearchFilterBar(
                filters = uiState.filters,
                onOpenFilterSheet = { isFilterSheetOpen = true },
                queryPresent = uiState.query.isNotBlank(),
                onToggleGenre = onToggleGenre,
                onTogglePlatform = onTogglePlatform,
                onRemoveReleaseYear = onRemoveReleaseYear,
                onToggleMinRating = onToggleMinRating,
                onResetFilters = onResetFilters,
            )

            // Result State Container. Content/Empty/Error are derived from the paged load
            // states; uiState.searchActive only gates the idle suggestions container.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val refreshState = lazyItems.loadState.refresh
                val appendState = lazyItems.loadState.append
                when {
                    !uiState.searchActive -> {
                        RecentQueriesList(
                            recentQueries = uiState.recentQueries,
                            onSelectRecentQuery = onSelectRecentQuery,
                            onRemoveRecentQuery = onRemoveRecentQuery,
                            onClearAllRecentQueries = onClearAllRecentQueries,
                            listState = recentListState,
                        )
                    }
                    refreshState is LoadState.Loading && lazyItems.itemCount == 0 -> {
                        SearchLoadingState()
                    }
                    lazyItems.itemCount == 0 && refreshState is LoadState.Error -> {
                        SearchErrorState(
                            error = refreshState.error.toPresentedAppError(),
                            onRetry = { lazyItems.retry() },
                        )
                    }
                    lazyItems.itemCount == 0 &&
                        refreshState is LoadState.NotLoading &&
                        appendState is LoadState.NotLoading -> {
                        SearchEmptyState(
                            query = uiState.query,
                            hasConstraints = uiState.filters.hasConstraints,
                            onClearQuery = onClearQuery,
                            onResetFilters = onResetFilters,
                        )
                    }
                    else -> {
                        SearchResultsList(
                            games = lazyItems,
                            librarySnapshot = uiState.librarySnapshot,
                            onGameClick = onGameClick,
                            onLibraryAction = onLibraryAction,
                            listState = resultsListState,
                        )
                    }
                }
            }
        }
    }

    if (isFilterSheetOpen) {
        SearchFilterSheet(
            initialFilters = uiState.filters,
            onDismiss = { isFilterSheetOpen = false },
            onApply = { appliedFilters ->
                onApplyFilters(appliedFilters)
            },
            queryPresent = uiState.query.isNotBlank(),
        )
    }

    if (editingEntry != null) {
        EditLibrarySheet(
            initialEntry = editingEntry,
            onDismiss = onDismissEditLibrary,
            onSave = { status, rating, hours, notes, favorite ->
                onSaveLibraryEntry(editingEntry.gameId, status, rating, hours, notes, favorite)
            },
            onRemove = { onRemoveFromLibrary(editingEntry.gameId) },
            actionsEnabled = !uiState.isLibrarySubmitting,
        )
    }
}

/** Pure reconnect decision backward-compatible delegator. */
internal fun shouldRetryOnReconnect(loadStates: CombinedLoadStates): Boolean =
    io.github.typenil.gametracker.feature.search.component.shouldRetryOnReconnect(loadStates)
