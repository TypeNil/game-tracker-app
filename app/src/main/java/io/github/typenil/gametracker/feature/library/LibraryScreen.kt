package io.github.typenil.gametracker.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.library.component.LibraryGameCard
import io.github.typenil.gametracker.feature.library.component.QuickHoursDialog

@Composable
fun LibraryRoute(
    onGameClick: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        onGameClick = onGameClick,
        onNavigateToDiscover = onNavigateToDiscover,
        onTabSelected = viewModel::onTabSelected,
        onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onToggleSearchActive = viewModel::onToggleSearchActive,
        onSortOptionSelected = viewModel::onSortOptionSelected,
        onClearSearch = viewModel::onClearSearch,
        onUserMessageShown = viewModel::onUserMessageShown,
        onToggleFavorite = viewModel::onToggleFavorite,
        onStatusSelected = viewModel::onStatusSelected,
        onHoursUpdated = viewModel::onHoursUpdated,
        onHoursSaveHandled = viewModel::onHoursSaveHandled,
        onCardVisible = viewModel::onCardVisible,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onGameClick: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearchActive: (Boolean) -> Unit,
    onSortOptionSelected: (LibrarySortOption) -> Unit,
    onClearSearch: () -> Unit,
    onToggleFavorite: (Long) -> Unit = {},
    onStatusSelected: (Long, LibraryStatus) -> Unit = { _, _ -> },
    onHoursUpdated: (Long, Int) -> Unit = { _, _ -> },
    onHoursSaveHandled: () -> Unit = {},
    onUserMessageShown: () -> Unit = {},
    onCardVisible: (LibraryGame) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var editingHoursGameId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab.ordinal,
        pageCount = { LibraryTab.entries.size },
    )
    val pagerScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage = uiState.userMessageRes?.let { stringResource(it) }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onUserMessageShown()
        }
    }
    LaunchedEffect(uiState.hoursSaveState) {
        when (val state = uiState.hoursSaveState) {
            is HoursSaveState.Saved -> {
                if (editingHoursGameId == state.gameId) {
                    editingHoursGameId = null
                }
                onHoursSaveHandled()
            }
            is HoursSaveState.Failed -> {
                onHoursSaveHandled()
            }
            else -> Unit
        }
    }

    LaunchedEffect(uiState.selectedTab) {
        val index = uiState.selectedTab.ordinal
        if (pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        val tab = LibraryTab.entries[pagerState.settledPage]
        if (tab != uiState.selectedTab) {
            onTabSelected(tab)
        }
    }


    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text(stringResource(R.string.library_search_hint)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = onClearSearch) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.search_clear_desc)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onToggleSearchActive(false) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_action_desc)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.library_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { onToggleSearchActive(true) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search_action_desc)
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.library_sort_menu_desc)
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                LibrarySortOption.entries.forEach { option ->
                                    val isSelected = uiState.sortOption == option
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes)) },
                                        onClick = {
                                            onSortOptionSelected(option)
                                            sortMenuExpanded = false
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_tab_row")
            ) {
                LibraryTab.entries.forEach { tab ->
                    val isSelected = pagerState.currentPage == tab.ordinal
                    val count = uiState.tabCounts[tab] ?: 0
                    Tab(
                        selected = isSelected,
                        onClick = {
                            pagerScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Badge(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ) {
                                    Text(text = "$count")
                                }
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.filterFavoritesOnly,
                    onClick = onToggleFavoritesOnly,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (uiState.filterFavoritesOnly) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.library_filter_favorites)) }
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.isCatalogEmpty -> {
                    LibraryEmptyState(
                        onNavigateToDiscover = onNavigateToDiscover,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_pager"),
                    ) { page ->
                        val tab = LibraryTab.entries[page]
                        val pageGames = uiState.gamesFor(tab)
                        if (pageGames.isEmpty()) {
                            if (uiState.isSearchOrFilterActive) {
                                LibrarySearchEmptyState(modifier = Modifier.fillMaxSize())
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
                                            editingHoursGameId = item.game.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    val targetGame = uiState.allGames.firstOrNull { it.game.id == editingHoursGameId }
    if (targetGame != null) {
        val isSaving = uiState.hoursSaveState is HoursSaveState.Saving &&
            uiState.hoursSaveState.gameId == targetGame.game.id
        QuickHoursDialog(
            gameName = targetGame.game.name,
            initialHours = targetGame.entry.hoursPlayed,
            onDismissRequest = {
                if (!isSaving) {
                    editingHoursGameId = null
                }
            },
            onConfirm = { hours ->
                onHoursUpdated(targetGame.game.id, hours)
            },
            isSaving = isSaving,
        )
    }
}

@Composable
private fun LibraryEmptyState(
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(GtDimens.Empty),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.library_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onNavigateToDiscover) {
                Text(stringResource(R.string.library_empty_cta))
            }
        }
    }
}

@Composable
private fun LibrarySearchEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(GtDimens.Empty),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.library_search_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.library_search_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LibraryTabEmptyState(
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(GtDimens.Empty),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.library_tab_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.library_tab_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onNavigateToDiscover) {
                Text(stringResource(R.string.library_empty_cta))
            }
        }
    }
}
