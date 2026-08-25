package io.github.typenil.gametracker.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.designsystem.component.GameCard
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.RecommendationReason

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
            else -> DiscoverContent(uiState, onGameClick, onRefresh, onLoadMoreTrending, onLoadMoreRail, Modifier.padding(innerPadding))
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
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.recommendations.isNotEmpty()) {
                item(key = "header:for-you") { SectionHeader(R.string.discover_for_you) }
                items(uiState.recommendations, key = { "for-you:${it.game.id}" }) { recommendation ->
                    RecommendationCard(recommendation, onGameClick)
                }
            }
            uiState.rails.forEach { railState ->
                item(key = "header:${railState.rail.type}") { SectionHeader(railState.rail.titleRes) }
                if (railState.isLoading) {
                    item(key = "loading:${railState.rail.type}") {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(railState.games, key = { "${railState.rail.type}:${it.id}" }) { game ->
                        GameCard(game = game, onClick = { onGameClick(game.id) })
                    }
                    if (!railState.endReached) {
                        item(key = "load-more:${railState.rail.type}") {
                            LaunchedEffect(railState.rail, railState.games.size) {
                                onLoadMoreRail(railState.rail)
                            }
                        }
                    }
                }
            }
            if (uiState.trending.isNotEmpty()) {
                item(key = "header:trending") { SectionHeader(R.string.discover_trending) }
                items(uiState.trending, key = { "trending:${it.id}" }) { game ->
                    GameCard(game = game, onClick = { onGameClick(game.id) })
                }
                item(key = "load-more:trending") {
                    LaunchedEffect(uiState.trending.size) { onLoadMoreTrending() }
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
    is RecommendationReason.GenreOverlap -> reason.tags.joinToString()
    is RecommendationReason.ThemeOverlap -> reason.tags.joinToString()
    is RecommendationReason.PlatformOverlap -> reason.tags.joinToString()
    RecommendationReason.SimilarGame -> "Similar game"
    RecommendationReason.HighRating -> "High rating"
    RecommendationReason.RecentRelease -> "Recent release"
}

@Composable
private fun DiscoverLoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun DiscoverErrorState(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(error.errorMessage(), color = MaterialTheme.colorScheme.error)
    }
}
