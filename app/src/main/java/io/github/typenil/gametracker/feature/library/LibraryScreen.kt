package io.github.typenil.gametracker.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.typenil.gametracker.core.designsystem.theme.topLevelBottomInset
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.library.component.LibraryEditSheetWiring
import io.github.typenil.gametracker.feature.library.component.LibraryFavoritesFilter
import io.github.typenil.gametracker.feature.library.component.LibraryHoursDialogWiring
import io.github.typenil.gametracker.feature.library.component.LibraryTabBody
import io.github.typenil.gametracker.feature.library.component.LibraryTabRow
import io.github.typenil.gametracker.feature.library.component.LibraryTopBar
import kotlinx.coroutines.launch

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
        onSaveLibraryEntry = viewModel::onSaveLibraryEntry,
        onRemoveFromLibrary = viewModel::onRemoveFromLibrary,
        onLibraryMutationHandled = viewModel::onLibraryMutationHandled,
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
    onSaveLibraryEntry: (
        gameId: Long,
        status: LibraryStatus,
        rating: Int?,
        hours: Int,
        notes: String?,
        isFavorite: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onRemoveFromLibrary: (Long) -> Unit = {},
    onLibraryMutationHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingHoursGameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingGameId by rememberSaveable { mutableStateOf<Long?>(null) }
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
    LaunchedEffect(uiState.libraryMutationState) {
        when (val state = uiState.libraryMutationState) {
            is LibraryMutationState.Saved -> {
                if (editingGameId == state.gameId) {
                    editingGameId = null
                }
                onLibraryMutationHandled()
            }
            is LibraryMutationState.Failed -> {
                onLibraryMutationHandled()
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
            LibraryTopBar(
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChanged = onSearchQueryChanged,
                onToggleSearchActive = onToggleSearchActive,
                onClearSearch = onClearSearch,
                sortOption = uiState.sortOption,
                onSortOptionSelected = onSortOptionSelected,
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = topLevelBottomInset(),
                )
        ) {
            LibraryTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabCounts = uiState.tabCounts,
                onTabClick = { tab ->
                    pagerScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                },
            )

            LibraryFavoritesFilter(
                filterFavoritesOnly = uiState.filterFavoritesOnly,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
            )

            LibraryTabBody(
                uiState = uiState,
                pagerState = pagerState,
                onGameClick = onGameClick,
                onNavigateToDiscover = onNavigateToDiscover,
                onClearSearch = onClearSearch,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
                onToggleFavorite = onToggleFavorite,
                onStatusSelected = onStatusSelected,
                onHoursClick = { gameId ->
                    editingHoursGameId = gameId
                },
                onNotesClick = { gameId ->
                    editingGameId = gameId
                },
                onCardVisible = onCardVisible,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    LibraryHoursDialogWiring(
        allGames = uiState.allGames,
        editingHoursGameId = editingHoursGameId,
        hoursSaveState = uiState.hoursSaveState,
        onDismiss = {
            editingHoursGameId = null
        },
        onHoursUpdated = onHoursUpdated,
    )

    LibraryEditSheetWiring(
        allGames = uiState.allGames,
        editingGameId = editingGameId,
        libraryMutationState = uiState.libraryMutationState,
        onDismiss = {
            editingGameId = null
        },
        onSaveLibraryEntry = onSaveLibraryEntry,
        onRemoveFromLibrary = onRemoveFromLibrary,
    )
}
