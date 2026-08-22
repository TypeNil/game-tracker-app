package io.github.typenil.gametracker.feature.details

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.GAME_COVER_ASPECT_RATIO
import io.github.typenil.gametracker.core.designsystem.component.GamePosterCard
import io.github.typenil.gametracker.core.designsystem.component.RatingBadge
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.core.model.GameVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Landscape 16:9 aspect ratio for screenshot thumbnails. */
private val SCREENSHOT_ASPECT_RATIO = 16f / 9f

/**
 * Host composable wiring the [GameDetailsViewModel] into the stateless screen.
 */
@Composable
fun GameDetailsRoute(
    gameId: Long,
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GameDetailsScreen(
        uiState = uiState,
        onGameClick = onGameClick,
        onBackClick = onBackClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onUserMessageShown = viewModel::onUserMessageShown,
        modifier = modifier
    )
}

/**
 * Stateless Game Details screen: cover/title/summary, genres/themes/modes,
 * platforms/release dates, ratings, companies, screenshots, videos (external
 * intent), similar games and share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    uiState: GameDetailsUiState,
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onUserMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userMessage = uiState.userMessageRes?.let { stringResource(it) }
    val shareError = stringResource(R.string.details_share_error)
    val videoError = stringResource(R.string.details_video_error)

    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            onUserMessageShown()
        }
    }

    val game = uiState.game
    val onShareClick: () -> Unit = {
        game?.let { current ->
            try {
                context.startActivity(DetailsIntents.shareIntent(current.name, current.url))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbarHostState.showSnackbar(shareError) }
            }
        }
    }
    val onVideoClick: (GameVideo) -> Unit = { video ->
        try {
            context.startActivity(DetailsIntents.videoIntent(video.videoId))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(videoError) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = game?.name ?: stringResource(R.string.details_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action_desc)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShareClick,
                        enabled = game != null
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.details_share_desc)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isInitialLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            uiState.error != null && game == null -> GameDetailsErrorState(
                error = uiState.error,
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding)
            )

            else -> GameDetailsContent(
                game = game,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                onGameClick = onGameClick,
                onVideoClick = onVideoClick,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun GameDetailsContent(
    game: GameDetails?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onGameClick: (Long) -> Unit,
    onVideoClick: (GameVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    // Empty sections stay hidden so a catalog skeleton renders as a lean but
    // complete page rather than a wall of empty headers.
    if (game == null) return
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") { GameDetailsHeader(game) }

            if (!game.summary.isNullOrBlank()) {
                item(key = "about") {
                    DetailsSection(title = stringResource(R.string.details_section_about)) {
                        Text(
                            text = game.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "tags") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TagSection(
                        title = stringResource(R.string.details_section_genres),
                        tags = game.genres
                    )
                    TagSection(
                        title = stringResource(R.string.details_section_themes),
                        tags = game.themes
                    )
                    TagSection(
                        title = stringResource(R.string.details_section_modes),
                        tags = game.gameModes
                    )
                }
            }

            item(key = "platforms") {
                TagSection(
                    title = stringResource(R.string.details_section_platforms),
                    tags = game.platforms
                )
            }

            if (game.releaseDates.isNotEmpty()) {
                item(key = "release-dates") {
                    DetailsSection(title = stringResource(R.string.details_section_release_dates)) {
                        game.releaseDates.forEach { releaseDate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = releaseDate.platform,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = releaseDate.displayDate(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (game.screenshots.isNotEmpty()) {
                item(key = "screenshots") {
                    DetailsSection(title = stringResource(R.string.details_section_screenshots)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items = game.screenshots, key = { it }) { screenshot ->
                                AsyncImage(
                                    model = screenshot,
                                    contentDescription = stringResource(R.string.details_screenshot_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(260.dp)
                                        .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }
                        }
                    }
                }
            }

            if (game.videos.isNotEmpty()) {
                item(key = "videos") {
                    DetailsSection(title = stringResource(R.string.details_section_videos)) {
                        game.videos.forEach { video ->
                            OutlinedButton(
                                onClick = { onVideoClick(video) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = video.name ?: stringResource(R.string.details_watch_trailer)
                                )
                            }
                        }
                    }
                }
            }

            if (game.similarGames.isNotEmpty()) {
                item(key = "similar") {
                    DetailsSection(title = stringResource(R.string.details_section_similar)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items = game.similarGames, key = { it.id }) { similar ->
                                GamePosterCard(
                                    game = similar,
                                    onClick = { onGameClick(similar.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetailsHeader(game: GameDetails) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(GAME_COVER_ASPECT_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (!game.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Aggregate rating with vote count; falls back to the critic rating the
            // catalog already carries while the details row is still hydrating.
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingBadge(rating = game.totalRating ?: game.rating)
                if (game.totalRatingCount != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.details_votes_count_format, game.totalRatingCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            game.companiesLine()?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

/** Non-interactive chips (Surface + Text): never clickable, list display only. */
@Composable
private fun TagSection(
    title: String,
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDetailsErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = error.errorMessage(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.retry_button))
            }
        }
    }
}

@Composable
private fun GameDetails.companiesLine(): String? {
    val developers = companies.filter { it.isDeveloper }.map { it.name }
    val publishers = companies.filter { it.isPublisher }.map { it.name }
    val others = companies
        .filterNot { it.isDeveloper || it.isPublisher }
        .map { it.name }

    val parts = buildList {
        when {
            developers.isNotEmpty() && developers == publishers ->
                add(stringResource(R.string.details_developed_and_published_format, developers.joinToString()))
            else -> {
                if (developers.isNotEmpty()) {
                    add(stringResource(R.string.details_developed_by_format, developers.joinToString()))
                }
                if (publishers.isNotEmpty()) {
                    add(stringResource(R.string.details_published_by_format, publishers.joinToString()))
                }
            }
        }
        if (others.isNotEmpty() && developers.isEmpty() && publishers.isEmpty()) {
            add(others.joinToString())
        }
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}

private fun GameReleaseDate.displayDate(): String {
    return when {
        dateEpochSeconds != null -> SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(dateEpochSeconds * 1000L))

        year != null -> year.toString()
        else -> ""
    }
}
