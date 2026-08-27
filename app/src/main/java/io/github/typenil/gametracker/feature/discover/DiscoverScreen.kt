package io.github.typenil.gametracker.feature.discover

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.designsystem.component.FeedSkeleton
import io.github.typenil.gametracker.core.designsystem.component.GameCard
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.core.model.LibrarySnapshot


import io.github.typenil.gametracker.core.model.RecommendationReason
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    onGameClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onAboutClick: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onUserMessageShown: () -> Unit,
    onLoadMoreTrending: () -> Unit,
    onLoadMoreRail: (DiscoverRail) -> Unit = {},
    onSelectTab: (DiscoverTab) -> Unit = {},
    onSelectRail: (DiscoverRail) -> Unit = {},
    onLoadMoreForYou: () -> Unit = {},
    onLibraryAction: (Game) -> Unit = {},
    onSaveLibraryEntry: (Long, LibraryStatus, Int?, Int, String?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRemoveFromLibrary: (Long) -> Unit = {},
    onDismissEditLibrary: () -> Unit = {},

    scrollToTopTrigger: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, stringResource(R.string.search_action_desc))
                    }
                    IconButton(onClick = onAboutClick) {
                        Icon(Icons.Default.Info, stringResource(R.string.settings_action_desc))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isInitialLoading -> DiscoverLoadingState(Modifier.padding(innerPadding))
            uiState.error != null && !uiState.hasContent -> DiscoverErrorState(uiState.error, onRetry, Modifier.padding(innerPadding))
            else -> DiscoverContent(
                uiState = uiState,
                onGameClick = onGameClick,
                onRefresh = onRefresh,
                onLoadMoreTrending = onLoadMoreTrending,
                onLoadMoreRail = onLoadMoreRail,
                onSelectTab = onSelectTab,
                onSelectRail = onSelectRail,
                onLoadMoreForYou = onLoadMoreForYou,
                onLibraryAction = onLibraryAction,
                scrollToTopTrigger = scrollToTopTrigger,
                modifier = Modifier.padding(innerPadding),
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverContent(
    uiState: DiscoverUiState,
    onGameClick: (Long) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreTrending: () -> Unit,
    onLoadMoreRail: (DiscoverRail) -> Unit,
    onSelectTab: (DiscoverTab) -> Unit,
    onSelectRail: (DiscoverRail) -> Unit,
    onLoadMoreForYou: () -> Unit,
    onLibraryAction: (Game) -> Unit,
    scrollToTopTrigger: Long,
    modifier: Modifier,
) {
    val forYouListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val chartsListState = rememberSaveable(uiState.selectedRail, saver = LazyListState.Saver) {
        LazyListState()
    }
    val activeListState = if (uiState.selectedTab == DiscoverTab.FOR_YOU) forYouListState else chartsListState
    val pullState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    var lastHandledScrollToTopTrigger by rememberSaveable { mutableStateOf(scrollToTopTrigger) }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0L && scrollToTopTrigger != lastHandledScrollToTopTrigger) {
            lastHandledScrollToTopTrigger = scrollToTopTrigger
            activeListState.scrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        DiscoverTabHeader(
            selectedTab = uiState.selectedTab,
            onSelectTab = onSelectTab,
            onTabReselected = {
                coroutineScope.launch {
                    activeListState.scrollToItem(0)
                }
            },
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (uiState.selectedTab) {
                DiscoverTab.FOR_YOU -> ForYouFeed(
                    uiState = uiState,
                    listState = forYouListState,
                    onGameClick = onGameClick,
                    onBrowseChartsClick = { onSelectTab(DiscoverTab.CHARTS) },
                    onLoadMoreForYou = onLoadMoreForYou,
                )
                DiscoverTab.CHARTS -> ChartsFeed(
                    uiState = uiState,
                    onGameClick = onGameClick,
                    onLoadMoreRail = onLoadMoreRail,
                    onSelectRail = onSelectRail,
                    onLibraryAction = onLibraryAction,
                )
            }
        }
    }
}
@Composable
private fun DiscoverTabHeader(
    selectedTab: DiscoverTab,
    onSelectTab: (DiscoverTab) -> Unit,
    onTabReselected: (DiscoverTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
    ) {
        DiscoverTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = {
                    if (selectedTab == tab) {
                        onTabReselected(tab)
                    } else {
                        onSelectTab(tab)
                    }
                },
                text = {
                    Text(
                        text = stringResource(tab.titleRes),
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun ForYouFeed(
    uiState: DiscoverUiState,
    listState: LazyListState,
    onGameClick: (Long) -> Unit,
    onBrowseChartsClick: () -> Unit,
    onLoadMoreForYou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.forYouLoading, uiState.forYouEndReached) {
        if (shouldLoadMore && !uiState.forYouLoading && !uiState.forYouEndReached) {
            onLoadMoreForYou()
        }
    }

    if (uiState.isColdStart && uiState.recommendations.isEmpty()) {
        ColdStartCard(
            onBrowseChartsClick = onBrowseChartsClick,
            modifier = modifier.padding(GtDimens.Gutter),
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = GtDimens.Gutter, vertical = GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.recommendations, key = { "for-you:${it.game.id}" }) { recommendation ->
                RecommendationCard(recommendation, onGameClick)
            }
            if (uiState.forYouLoading) {
                item(key = "loading-append:for-you") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ColdStartCard(
    onBrowseChartsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GtDimens.Empty),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.discover_cold_start_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_cold_start_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBrowseChartsClick) {
                Text(stringResource(R.string.discover_cold_start_action))
            }
        }
    }
}

@Composable
private fun ChartsFeed(
    uiState: DiscoverUiState,
    onGameClick: (Long) -> Unit,
    onLoadMoreRail: (DiscoverRail) -> Unit,
    onSelectRail: (DiscoverRail) -> Unit,
    onLibraryAction: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberSaveable(uiState.selectedRail, saver = LazyListState.Saver) {
        LazyListState()
    }
    val currentRailState = uiState.rails.firstOrNull { it.rail == uiState.selectedRail }
        ?: DiscoverRailState(uiState.selectedRail)
    val shouldLoadMore by remember(currentRailState.games.size) {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
        }
    }

    LaunchedEffect(uiState.selectedRail) {
        if (currentRailState.games.isEmpty() && !currentRailState.isLoading && !currentRailState.endReached) {
            onLoadMoreRail(uiState.selectedRail)
        }
    }

    LaunchedEffect(shouldLoadMore, currentRailState.isLoading, currentRailState.endReached) {
        if (shouldLoadMore && !currentRailState.isLoading && !currentRailState.endReached) {
            onLoadMoreRail(uiState.selectedRail)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = GtDimens.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscoverRail.entries.forEach { rail ->
                FilterChip(
                    selected = uiState.selectedRail == rail,
                    onClick = { onSelectRail(rail) },
                    label = { Text(stringResource(rail.titleRes)) },
                )
            }
        }

        if (currentRailState.games.isEmpty() && currentRailState.isLoading) {
            FeedSkeleton(
                label = stringResource(R.string.discover_charts_loading),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = GtDimens.Gutter, vertical = GtDimens.Gutter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(currentRailState.games, key = { "${currentRailState.rail.type}:${it.id}" }) { game ->
                    GameCard(
                        game = game,
                        onClick = { onGameClick(game.id) },
                        libraryStatus = (uiState.librarySnapshot as? LibrarySnapshot.Ready)
                            ?.entries?.get(game.id)?.status,
                        onLibraryAction = if (uiState.librarySnapshot is LibrarySnapshot.Ready) {
                            onLibraryAction
                        } else {
                            null
                        },
                    )
                }
                if (currentRailState.isLoading) {
                    item(key = "loading-append:${currentRailState.rail.type}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun RecommendationCard(recommendation: DiscoverRecommendation, onGameClick: (Long) -> Unit) {
    GameCard(
        game = recommendation.game,
        onClick = { onGameClick(recommendation.game.id) },
        supportingLines = recommendation.reasons.map { reasonLabel(it) },
    )
}

@Composable
private fun reasonLabel(reason: RecommendationReason): String = when (reason) {
    is RecommendationReason.GenreOverlap -> stringResource(R.string.reason_genre, reason.tags.joinToString())
    is RecommendationReason.ThemeOverlap -> stringResource(R.string.reason_theme, reason.tags.joinToString())
    is RecommendationReason.PlatformOverlap -> stringResource(R.string.reason_platform, reason.tags.joinToString())
    RecommendationReason.SimilarGame -> stringResource(R.string.reason_similar)
    RecommendationReason.HighRating -> stringResource(R.string.reason_rating)
    RecommendationReason.RecentRelease -> stringResource(R.string.reason_recency)
}
@Composable
private fun DiscoverLoadingState(modifier: Modifier = Modifier) {
    FeedSkeleton(
        label = stringResource(R.string.discover_loading),
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun DiscoverErrorState(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(GtDimens.Empty),
        ) {
            Text(
                text = error.errorMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.retry_button))
            }
        }
    }
}
