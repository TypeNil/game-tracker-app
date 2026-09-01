package io.github.typenil.gametracker.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
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
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.FeedSkeleton
import io.github.typenil.gametracker.core.designsystem.component.GameCard
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.feature.search.component.SearchFilterBar
import io.github.typenil.gametracker.feature.search.component.SearchFilterSheet

@Composable
fun SearchRoute(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChanged,
        onClearQuery = viewModel::onClearQuery,
        onRetry = viewModel::retry,
        onGameClick = onGameClick,
        onBackClick = onBackClick,
        onRemoveGenre = viewModel::onGenreToggled,
        onRemovePlatform = viewModel::onPlatformToggled,
        onRemoveReleaseYear = { viewModel.onReleaseYearSelected(ReleaseYearFilter.ALL) },
        onRemoveMinRating = { viewModel.onMinRatingSelected(MinRatingFilter.ANY) },
        onResetFilters = viewModel::onResetFilters,
        onApplyFilters = viewModel::onApplyFilters,
        onSelectRecentQuery = viewModel::onSelectRecentQuery,
        onRemoveRecentQuery = viewModel::onRemoveRecentQuery,
        onClearAllRecentQueries = viewModel::onClearAllRecentQueries,
        onQuickPresetSelected = viewModel::onQuickPresetSelected,
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
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRetry: () -> Unit,
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onRemoveGenre: (String) -> Unit = {},
    onRemovePlatform: (PlatformFamily) -> Unit = {},
    onRemoveReleaseYear: () -> Unit = {},
    onRemoveMinRating: () -> Unit = {},
    onResetFilters: () -> Unit = {},
    onApplyFilters: (SearchFilters) -> Unit = {},
    onSelectRecentQuery: (String) -> Unit = {},
    onRemoveRecentQuery: (String) -> Unit = {},
    onClearAllRecentQueries: () -> Unit = {},
    onQuickPresetSelected: (QuickSearchPreset) -> Unit = {},
    onLibraryAction: (Game) -> Unit = {},
    onSaveLibraryEntry: (Long, LibraryStatus, Int?, Int, String?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRemoveFromLibrary: (Long) -> Unit = {},
    onDismissEditLibrary: () -> Unit = {},
    onUserMessageShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isFilterSheetOpen by rememberSaveable { mutableStateOf(false) }

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
            TopAppBar(
                title = {
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
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action_desc),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
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
                onRemoveGenre = onRemoveGenre,
                onRemovePlatform = onRemovePlatform,
                onRemoveReleaseYear = onRemoveReleaseYear,
                onRemoveMinRating = onRemoveMinRating,
                onResetFilters = onResetFilters,
            )

            // Result State Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (val result = uiState.result) {
                    is SearchResultUiState.Idle -> {
                        SearchIdleState(
                            recentQueries = uiState.recentQueries,
                            onSelectRecentQuery = onSelectRecentQuery,
                            onRemoveRecentQuery = onRemoveRecentQuery,
                            onClearAllRecentQueries = onClearAllRecentQueries,
                            onQuickPresetSelected = onQuickPresetSelected,
                        )
                    }
                    is SearchResultUiState.Loading -> {
                        SearchLoadingState()
                    }
                    is SearchResultUiState.Content -> {
                        SearchContentState(
                            games = result.games,
                            librarySnapshot = uiState.librarySnapshot,
                            onGameClick = onGameClick,
                            onLibraryAction = onLibraryAction,
                        )
                    }
                    is SearchResultUiState.Empty -> {
                        SearchEmptyState(
                            query = result.query,
                            hasConstraints = result.hasConstraints,
                            onClearQuery = onClearQuery,
                            onResetFilters = onResetFilters,
                        )
                    }
                    is SearchResultUiState.Error -> {
                        SearchErrorState(
                            error = result.error,
                            onRetry = onRetry,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdleState(
    recentQueries: List<String>,
    onSelectRecentQuery: (String) -> Unit,
    onRemoveRecentQuery: (String) -> Unit,
    onClearAllRecentQueries: () -> Unit,
    onQuickPresetSelected: (QuickSearchPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        // 0. Guidance Header
        item(key = "guidance_header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.search_idle_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.search_idle_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 1. Quick Presets Section
        item(key = "presets_section") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.search_presets_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val presets = listOf<Pair<QuickSearchPreset, @Composable () -> Unit>>(
                        QuickSearchPreset.Genre("Role-playing (RPG)") to { Text(formatGenreTag("Role-playing (RPG)")) },
                        QuickSearchPreset.Genre("Action") to { Text(formatGenreTag("Action")) },
                        QuickSearchPreset.Genre("Adventure") to { Text(formatGenreTag("Adventure")) },
                        QuickSearchPreset.Genre("Shooter") to { Text(formatGenreTag("Shooter")) },
                        QuickSearchPreset.Genre("Indie") to { Text(formatGenreTag("Indie")) },
                        QuickSearchPreset.Platform(PlatformFamily.PC) to {
                            Text(stringResource(PlatformFamily.PC.labelRes))
                        },
                        QuickSearchPreset.Platform(PlatformFamily.PLAYSTATION) to {
                            Text(stringResource(PlatformFamily.PLAYSTATION.labelRes))
                        },
                        QuickSearchPreset.Platform(PlatformFamily.XBOX) to {
                            Text(stringResource(PlatformFamily.XBOX.labelRes))
                        },
                        QuickSearchPreset.Platform(PlatformFamily.NINTENDO) to {
                            Text(stringResource(PlatformFamily.NINTENDO.labelRes))
                        },
                        QuickSearchPreset.Rating80 to { Text(stringResource(R.string.search_filter_rating_80)) },
                    )
                    for ((preset, labelComposable) in presets) {
                        val platform = (preset as? QuickSearchPreset.Platform)?.family
                        FilterChip(
                            selected = false,
                            onClick = { onQuickPresetSelected(preset) },
                            label = labelComposable,
                            leadingIcon = if (platform != null) {
                                {
                                    Icon(
                                        painter = painterResource(platform.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        )
                    }
                }
            }
        }

        // 2. Recent Searches Section
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
    games: List<Game>,
    librarySnapshot: LibrarySnapshot,
    onGameClick: (Long) -> Unit,
    onLibraryAction: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = librarySnapshot as? LibrarySnapshot.Ready
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "results_count_header") {
            Text(
                text = if (games.size == 1) {
                    stringResource(R.string.search_results_count_single)
                } else {
                    stringResource(R.string.search_results_count_format, games.size)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        items(
            items = games,
            key = { it.id },
        ) { game ->
            GameCard(
                game = game,
                onClick = { onGameClick(game.id) },
                libraryStatus = ready?.entries?.get(game.id)?.status,
                onLibraryAction = if (ready != null) onLibraryAction else null,
            )
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
