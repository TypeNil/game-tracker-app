package io.github.typenil.gametracker.feature.details

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** Standard horizontal gutter for details sections. */
private val DETAILS_GUTTER = 16.dp

/** Fixed portrait cover width in the details header. */
private val HEADER_COVER_WIDTH = 120.dp

/** Landscape 16:9 aspect ratio for screenshot thumbnails. */
private val SCREENSHOT_ASPECT_RATIO = 16f / 9f

/**
 * Host composable wiring the [GameDetailsViewModel] into the stateless screen.
 * The gameId arrives via SavedStateHandle from the type-safe route argument,
 * not through composition parameters.
 */
@Composable
fun GameDetailsRoute(
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    var selectedScreenshotIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = DETAILS_GUTTER),
            verticalArrangement = Arrangement.spacedBy(DETAILS_GUTTER),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                GameDetailsHeader(
                    game = game,
                    modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                )
            }

            if (!game.summary.isNullOrBlank()) {
                item(key = "about") {
                    DetailsSection(
                        title = stringResource(R.string.details_section_about),
                        modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                    ) {
                        Text(
                            text = game.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "tags") {
                Column(
                    modifier = Modifier.padding(horizontal = DETAILS_GUTTER),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                    tags = game.platforms,
                    modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                )
            }

            if (game.releaseDates.isNotEmpty()) {
                item(key = "release-dates") {
                    DetailsSection(
                        title = stringResource(R.string.details_section_release_dates),
                        modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                    ) {
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
                    DetailsSection(
                        title = stringResource(R.string.details_section_screenshots),
                        modifier = Modifier.fillMaxWidth(),
                        titleModifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = DETAILS_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(items = game.screenshots, key = { _, url -> url }) { index, screenshot ->
                                AsyncImage(
                                    model = screenshot,
                                    contentDescription = stringResource(R.string.details_screenshot_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(260.dp)
                                        .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { selectedScreenshotIndex = index }
                                )
                            }
                        }
                    }
                }
            }

            if (game.videos.isNotEmpty()) {
                item(key = "videos") {
                    DetailsSection(
                        title = stringResource(R.string.details_section_videos),
                        modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                    ) {
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
                    DetailsSection(
                        title = stringResource(R.string.details_section_similar),
                        modifier = Modifier.fillMaxWidth(),
                        titleModifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = DETAILS_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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

        selectedScreenshotIndex?.let { initialIndex ->
            ScreenshotViewerDialog(
                screenshots = game.screenshots,
                initialIndex = initialIndex,
                onDismissRequest = { selectedScreenshotIndex = null },
                onPageChanged = { selectedScreenshotIndex = it }
            )
        }
    }
}

@Composable
private fun GameDetailsHeader(
    game: GameDetails,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(HEADER_COVER_WIDTH)
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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
    titleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = titleModifier
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        // Porting/supporting studios (both flags false) are still credited,
        // appended as plain names so they never hide behind dev/pub lines.
        if (others.isNotEmpty()) {
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

@Composable
private fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismissRequest: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    if (screenshots.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.lastIndex),
        pageCount = { screenshots.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = screenshots[page],
                    contentDescription = stringResource(
                        R.string.details_viewer_page_format,
                        page + 1,
                        screenshots.size
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.details_viewer_page_format,
                        pagerState.currentPage + 1,
                        screenshots.size
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.details_viewer_close_desc),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
