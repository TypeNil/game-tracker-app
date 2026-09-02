package io.github.typenil.gametracker.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import io.github.typenil.gametracker.core.designsystem.component.FeedSkeleton
import io.github.typenil.gametracker.core.designsystem.component.GameCard
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppErrorException
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.SearchInputValidation
import io.github.typenil.gametracker.core.model.SearchInputViolation
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.feature.search.component.SearchFilterBar
import io.github.typenil.gametracker.feature.search.component.SearchFilterSheet
import kotlinx.coroutines.flow.Flow

private fun SearchInputViolation.messageRes(): Int = when (this) {
    SearchInputViolation.TOO_LONG -> R.string.search_input_error_too_long
    SearchInputViolation.QUOTE_OR_BACKSLASH -> R.string.search_input_error_unsupported_chars
    SearchInputViolation.CONTROL_CHAR, SearchInputViolation.INVISIBLE_FORMAT -> R.string.search_input_error_invisible_chars
}

/**
 * Paging only carries [Throwable]; the data boundary (GamesRemoteMediator) already classified it
 * into an [AppError] via AppErrorException. Presentation must not import transport-specific
 * mapping, so anything unrecognized degrades to UnknownError.
 */
private fun Throwable.toPresentedAppError(): AppError =
    (this as? AppErrorException)?.error ?: AppError.UnknownError(this)

@Composable
fun SearchRoute(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        onBackClick = onBackClick,
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
    onBackClick: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isFilterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val lazyItems = searchResults.collectAsLazyPagingItems()

    // Device-network recovery is an event, not data: only a genuine Unavailable ->
    // Available transition may retry a currently failed Paging load. Both the baseline
    // and the one pending recovery intent must survive configuration recreation: the
    // ViewModel-scope cachedIn generation outlives it, and a restored composition can
    // replay the failed LoadStates a frame after its first effect pass (Loading first).
    // The first composition (Unknown baseline) is never treated as recovery.
    var previousNetworkStatus by rememberSaveable { mutableStateOf(NetworkStatus.Unknown) }
    var pendingRecoveryRetry by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(networkStatus, lazyItems.loadState.refresh, lazyItems.loadState.append) {
        val loadStates = lazyItems.loadState
        val recoveredNow = previousNetworkStatus == NetworkStatus.Unavailable &&
            networkStatus == NetworkStatus.Available
        previousNetworkStatus = networkStatus
        if (recoveredNow) {
            pendingRecoveryRetry = true
        }
        when {
            networkStatus != NetworkStatus.Available -> pendingRecoveryRetry = false
            pendingRecoveryRetry && shouldRetryOnReconnect(loadStates) -> {
                lazyItems.retry()
                pendingRecoveryRetry = false
            }
            // Settled healthy without ever needing the retry: drop the intent. While any
            // load is still in flight the intent stays pending until it resolves.
            pendingRecoveryRetry && !loadStates.isLoading -> pendingRecoveryRetry = false
        }
    }

    val userMessage = uiState.userMessageRes?.let { stringResource(it) }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onUserMessageShown()
        }
    }
    val readyLibrary = uiState.librarySnapshot as? LibrarySnapshot.Ready
    val editingEntry = uiState.editingGameId?.let { readyLibrary?.entries?.get(it) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // A dedicated header row instead of TopAppBar(title = TextField): M3 TextField reserves
            // its supporting-text area even when empty, which grew the bar to ~73dp and left the
            // field misaligned with the back button plus dead space before the filter row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_action_desc),
                    )
                }
                TextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    isError = uiState.inputValidation is SearchInputValidation.Invalid,
                    supportingText = (uiState.inputValidation as? SearchInputValidation.Invalid)?.let { invalid ->
                        {
                            Text(
                                text = stringResource(invalid.violation.messageRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = onClearQuery) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.search_clear_desc),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                        SearchIdleState(
                            recentQueries = uiState.recentQueries,
                            onSelectRecentQuery = onSelectRecentQuery,
                            onRemoveRecentQuery = onRemoveRecentQuery,
                            onClearAllRecentQueries = onClearAllRecentQueries,
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
                        SearchContentState(
                            games = lazyItems,
                            librarySnapshot = uiState.librarySnapshot,
                            onGameClick = onGameClick,
                            onLibraryAction = onLibraryAction,
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
                isFilterSheetOpen = false
            },
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

@Composable
private fun SearchIdleState(
    recentQueries: List<String>,
    onSelectRecentQuery: (String) -> Unit,
    onRemoveRecentQuery: (String) -> Unit,
    onClearAllRecentQueries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentQueries.isEmpty()) return

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {

        // 1. Recent Searches Section
        if (recentQueries.isNotEmpty()) {
            item(key = "recent_section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onClearAllRecentQueries) {
                        Text(stringResource(R.string.search_recent_clear_all))
                    }
                }
            }

            items(
                items = recentQueries,
                key = { "recent_$it" },
            ) { query ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClick = { onSelectRecentQuery(query) },
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = query,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onRemoveRecentQuery(query) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_recent_remove_desc, query),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLoadingState(modifier: Modifier = Modifier) {
    FeedSkeleton(
        label = stringResource(R.string.search_loading_games),
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun SearchContentState(
    games: LazyPagingItems<Game>,
    librarySnapshot: LibrarySnapshot,
    onGameClick: (Long) -> Unit,
    onLibraryAction: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = librarySnapshot as? LibrarySnapshot.Ready
    val refreshError = games.loadState.refresh as? LoadState.Error
    val appendState = games.loadState.append
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (refreshError != null) {
            // Cached rows remain the SSOT while the background refresh failed: banner, not
            // full-screen replacement. games.retry() re-runs the failed mediator refresh and
            // always hits the network (initialize/TTL is not re-consulted on retry).
            item(key = "refresh_error_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.error_refresh_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { games.retry() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.retry_button),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        item(key = "results_count_header") {
            val count = games.itemCount
            val finished = appendState is LoadState.NotLoading &&
                appendState.endOfPaginationReached &&
                games.loadState.refresh !is LoadState.Loading
            Text(
                text = if (!finished) {
                    stringResource(R.string.search_results_count_partial_format, count)
                } else {
                    // A terminal state can mean a truly exhausted result set or the BFF
                    // offset ceiling (1000): LazyPagingItems cannot tell the two apart, so
                    // the copy stays neutral about how many matches the server knows.
                    pluralStringResource(R.plurals.search_results_loaded_count, count, count)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        items(
            count = games.itemCount,
            key = games.itemKey { it.id },
        ) { index ->
            // enablePlaceholders = false: a null slot only exists transitively between
            // invalidation and reload; rendering nothing is correct, access via games[index]
            // still submits the paging hint.
            val game = games[index]
            if (game != null) {
                GameCard(
                    game = game,
                    onClick = { onGameClick(game.id) },
                    libraryStatus = ready?.entries?.get(game.id)?.status,
                    onLibraryAction = if (ready != null) onLibraryAction else null,
                )
            }
        }

        when (appendState) {
            is LoadState.Loading -> {
                item(key = "load_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
            is LoadState.Error -> {
                item(key = "load_more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.error_load_more_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { games.retry() }) {
                            Text(stringResource(R.string.retry_button))
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SearchEmptyState(
    query: String,
    hasConstraints: Boolean,
    onClearQuery: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(GtDimens.Empty),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (hasConstraints && query.isBlank()) {
                Text(
                    text = stringResource(R.string.search_empty_filters_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.search_empty_filters_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onResetFilters) {
                    Text(stringResource(R.string.search_empty_filters_action))
                }
            } else {
                Text(
                    text = stringResource(R.string.search_empty_results_format, query),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = if (hasConstraints) onResetFilters else onClearQuery) {
                    Text(
                        if (hasConstraints) {
                            stringResource(R.string.search_empty_filters_action)
                        } else {
                            stringResource(R.string.search_empty_action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(GtDimens.Empty),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error.errorMessage(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.retry_button))
            }
        }
    }
}

/**
 * Pure reconnect decision: a network-recovery edge must only retry the failed Paging
 * load. Idle, Loading and NotLoading are never re-triggered by connectivity events.
 */
internal fun shouldRetryOnReconnect(loadStates: CombinedLoadStates): Boolean =
    loadStates.refresh is LoadState.Error || loadStates.append is LoadState.Error

/** True while a refresh or append transition is still in flight. */
private val CombinedLoadStates.isLoading: Boolean
    get() = refresh is LoadState.Loading || append is LoadState.Loading
