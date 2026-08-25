package io.github.typenil.gametracker.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import io.github.typenil.gametracker.core.designsystem.component.GameCard
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.model.AppError
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
    onLoadMoreForYou: () -> Unit = {},
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
                onLoadMoreForYou = onLoadMoreForYou,
                scrollToTopTrigger = scrollToTopTrigger,
                modifier = Modifier.padding(innerPadding),
            )
        }
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
    onLoadMoreForYou: () -> Unit,
    scrollToTopTrigger: Long,
    modifier: Modifier,
) {
    val forYouListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val chartsListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val activeListState = if (uiState.selectedTab == DiscoverTab.FOR_YOU) forYouListState else chartsListState
    val pullState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { activeListState.firstVisibleItemIndex > 2 }
    }
    var lastHandledScrollToTopTrigger by rememberSaveable { mutableStateOf(scrollToTopTrigger) }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0L && scrollToTopTrigger != lastHandledScrollToTopTrigger) {
            lastHandledScrollToTopTrigger = scrollToTopTrigger
            activeListState.scrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        listState = chartsListState,
                        onGameClick = onGameClick,
                        onLoadMoreRail = onLoadMoreRail,
                        onLoadMoreTrending = onLoadMoreTrending,
                    )
                }
            }
        }

        ScrollToTopFab(
            visible = showScrollToTop,
            onClick = {
                coroutineScope.launch {
                    activeListState.scrollToItem(0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
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
private fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.scroll_to_top_desc),
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
            modifier = modifier.padding(16.dp),
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
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
                .padding(24.dp),
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
    listState: LazyListState,
    onGameClick: (Long) -> Unit,
    onLoadMoreRail: (DiscoverRail) -> Unit,
    onLoadMoreTrending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.rails.forEachIndexed { index, railState ->
            val canShowRail = index == 0 ||
                uiState.rails[index - 1].games.isNotEmpty() ||
                uiState.rails[index - 1].endReached
            if (canShowRail) {
                item(key = "header:${railState.rail.type}") { SectionHeader(railState.rail.titleRes) }
                if (railState.games.isEmpty() && railState.isLoading) {
                    item(key = "loading-initial:${railState.rail.type}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(railState.games, key = { "${railState.rail.type}:${it.id}" }) { game ->
                        GameCard(game = game, onClick = { onGameClick(game.id) })
                    }
                    if (railState.isLoading) {
                        item(key = "loading-append:${railState.rail.type}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (!railState.endReached) {
                        item(key = "load-more:${railState.rail.type}") {
                            LaunchedEffect(railState.rail, railState.games.size) {
                                onLoadMoreRail(railState.rail)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun DiscoverErrorState(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
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
