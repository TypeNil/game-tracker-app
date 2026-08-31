package io.github.typenil.gametracker.feature.details

import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import io.github.typenil.gametracker.core.designsystem.component.TagChip
import io.github.typenil.gametracker.core.designsystem.component.OverflowTagChip
import io.github.typenil.gametracker.core.designsystem.component.displayNameRes
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.designsystem.component.formatPlatformDisplayName
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.feature.details.component.GameModesBottomSheet
import io.github.typenil.gametracker.feature.details.component.GameVideoCard
import io.github.typenil.gametracker.feature.details.component.PlatformsBottomSheet
import io.github.typenil.gametracker.feature.details.component.TagsBottomSheet
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.IntrinsicSize
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Standard horizontal gutter for details sections. */
private val DETAILS_GUTTER = GtDimens.Gutter

/** Enlarged portrait cover width in the details header. */
private val HEADER_COVER_WIDTH = 124.dp

/** Landscape 16:9 aspect ratio for screenshot thumbnails. */
private val SCREENSHOT_ASPECT_RATIO = 16f / 9f

private const val ARTWORK_ALPHA = 0.72f
private const val ARTWORK_SCRIM_ALPHA = 0.15f
private const val TITLE_DOCK_SCALE_MIN = 0.92f
private const val TITLE_DOCK_SCALE_DELTA = 0.08f
private const val APP_BAR_SCRIM_MAX_ALPHA = 0.45f
private const val TRANSFORM_ORIGIN_CENTER_Y = 0.5f
private const val TITLE_A11Y_MIN_ALPHA = 0.05f
private const val TITLE_A11Y_MAX_ALPHA = 0.95f
private val TITLE_DOCK_TRANSLATION_RANGE = 12.dp
private val APP_BAR_BG_SCROLL_THRESHOLD = 120.dp
private val TITLE_HANDOFF_START_OFFSET = 90.dp

private val TITLE_HANDOFF_END_OFFSET = 150.dp

/** Collapsed About summary line count before the arrow toggle reveals the rest. */
private const val ABOUT_COLLAPSED_LINES = 2

/** Videos visible before the "Show all" toggle (BFF caps the list at five). */
private const val VIDEOS_COLLAPSED_COUNT = 2

/** Reference CTA fill for Add to Library. */
private val LibraryCta = Color(0xFF4E3DCA)
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
        onEditLibraryClicked = viewModel::onEditLibraryClicked,
        onDismissEditLibrary = viewModel::onDismissEditLibrary,
        onSaveLibraryEntry = viewModel::onSaveLibraryEntry,
        onRemoveFromLibrary = viewModel::onRemoveFromLibrary,
        modifier = modifier
    )
}
private enum class DetailsOverflowSheet {
    None,
    Platforms,
    Tags,
    GameModes,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    uiState: GameDetailsUiState,
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onUserMessageShown: () -> Unit,
    onEditLibraryClicked: () -> Unit = {},
    onDismissEditLibrary: () -> Unit = {},
    onSaveLibraryEntry: (
        status: LibraryStatus,
        rating: Int?,
        hours: Int,
        notes: String?,
        isFavorite: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    onRemoveFromLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowSheet by rememberSaveable { mutableStateOf(DetailsOverflowSheet.None) }
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
    val onShareClick = remember(context, game, shareError) {
        {
            game?.let { current ->
                try {
                    context.startActivity(DetailsIntents.shareIntent(current.name, current.url))
                } catch (_: ActivityNotFoundException) {
                    scope.launch { snackbarHostState.showSnackbar(shareError) }
                }
            }
            Unit
        }
    }
    val onVideoClick = remember(context, videoError) {
        { video: GameVideo ->
            try {
                context.startActivity(DetailsIntents.videoIntent(video.videoId))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbarHostState.showSnackbar(videoError) }
            }
            Unit
        }
    }
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val bgScrollThresholdPx = with(density) { APP_BAR_BG_SCROLL_THRESHOLD.toPx() }
    val titleTranslationRangePx = with(density) { TITLE_DOCK_TRANSLATION_RANGE.toPx() }
    val handoffStartPx = with(density) { TITLE_HANDOFF_START_OFFSET.toPx() }
    val handoffEndPx = with(density) { TITLE_HANDOFF_END_OFFSET.toPx() }

    val appBarBgAlpha = remember(lazyListState, bgScrollThresholdPx) {
        {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / bgScrollThresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    val titleHandoffProgress = remember(lazyListState, handoffStartPx, handoffEndPx) {
        {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offset = lazyListState.firstVisibleItemScrollOffset.toFloat()
                ((offset - handoffStartPx) / (handoffEndPx - handoffStartPx)).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailsTopAppBar(
                gameName = game?.name,
                onBackClick = onBackClick,
                onShareClick = onShareClick,
                titleHandoffProgress = titleHandoffProgress,
                appBarBgAlpha = appBarBgAlpha,
                titleTranslationRangePx = titleTranslationRangePx,
            )
        }
    ) { innerPadding ->
        when {
            // Full-screen skeleton renders inside the same LazyColumn as content
            // (single list state), so the first hydrated frame replaces
            // placeholders without a spinner swap or scroll-state conflict.
            uiState.error != null && game == null && !uiState.isLoading -> GameDetailsErrorState(
                error = uiState.error,
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding)
            )

            else -> GameDetailsContent(
                game = game,
                libraryEntry = uiState.libraryEntry,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                onGameClick = onGameClick,
                onVideoClick = onVideoClick,
                onEditLibraryClicked = onEditLibraryClicked,
                onPlatformsClick = { overflowSheet = DetailsOverflowSheet.Platforms },
                onTagsOverflowClick = { overflowSheet = DetailsOverflowSheet.Tags },
                onGameModesClick = { overflowSheet = DetailsOverflowSheet.GameModes },
                contentTopPadding = innerPadding.calculateTopPadding(),
                lazyListState = lazyListState,
                titleHandoffProgress = titleHandoffProgress,
                titleTranslationRangePx = titleTranslationRangePx,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
        }

        when (overflowSheet) {
            DetailsOverflowSheet.None -> Unit
            DetailsOverflowSheet.Platforms -> {
                game?.let { currentGame ->
                    PlatformsBottomSheet(
                        platforms = currentGame.platforms,
                        releaseDates = currentGame.releaseDates,
                        onDismiss = { overflowSheet = DetailsOverflowSheet.None },
                    )
                }
            }
            DetailsOverflowSheet.Tags -> {
                game?.let { currentGame ->
                    TagsBottomSheet(
                        genres = currentGame.genres,
                        themes = currentGame.themes,
                        onDismiss = { overflowSheet = DetailsOverflowSheet.None },
                    )
                }
            }
            DetailsOverflowSheet.GameModes -> {
                game?.let { currentGame ->
                    GameModesBottomSheet(
                        gameModes = currentGame.gameModes,
                        onDismiss = { overflowSheet = DetailsOverflowSheet.None },
                    )
                }
            }
        }
        if (uiState.isEditingLibrary) {
            EditLibrarySheet(
                initialEntry = uiState.libraryEntry,
                onDismiss = onDismissEditLibrary,
                onSave = onSaveLibraryEntry,
                onRemove = onRemoveFromLibrary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsTopAppBar(
    gameName: String?,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    titleHandoffProgress: () -> Float,
    appBarBgAlpha: () -> Float,
    titleTranslationRangePx: Float,
) {
    val bgAlpha = appBarBgAlpha()
    TopAppBar(
        title = {
            val progress = titleHandoffProgress()
            Text(
                text = gameName ?: stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = progress
                        translationY = titleTranslationRangePx * (1f - progress)
                        scaleX = TITLE_DOCK_SCALE_MIN + TITLE_DOCK_SCALE_DELTA * progress
                        scaleY = TITLE_DOCK_SCALE_MIN + TITLE_DOCK_SCALE_DELTA * progress
                        transformOrigin = TransformOrigin(0f, TRANSFORM_ORIGIN_CENTER_Y)
                    }
                    .semantics {
                        if (progress < TITLE_A11Y_MIN_ALPHA) {
                            hideFromAccessibility()
                        }
                    },
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.surface.copy(
                        alpha = (1f - bgAlpha) * APP_BAR_SCRIM_MAX_ALPHA,
                    ),
                    shape = CircleShape,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_action_desc),
                )
            }
        },
        actions = {
            IconButton(
                onClick = onShareClick,
                enabled = gameName != null,
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.surface.copy(
                        alpha = (1f - bgAlpha) * APP_BAR_SCRIM_MAX_ALPHA,
                    ),
                    shape = CircleShape,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.details_share_desc),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = bgAlpha,
            ),
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = bgAlpha,
            ),
        ),
    )
}

@Composable
private fun GameDetailsContent(
    game: GameDetails?,
    libraryEntry: LibraryEntry?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onGameClick: (Long) -> Unit,
    onVideoClick: (GameVideo) -> Unit,
    onEditLibraryClicked: () -> Unit,
    onPlatformsClick: () -> Unit,
    onTagsOverflowClick: () -> Unit,
    onGameModesClick: () -> Unit,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    titleHandoffProgress: () -> Float = { 0f },
    titleTranslationRangePx: Float = 0f,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var selectedScreenshotIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 0.dp, bottom = DETAILS_GUTTER),
            verticalArrangement = Arrangement.spacedBy(DETAILS_GUTTER),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                if (game == null) {
                    DetailsHeaderSkeleton(
                        contentTopPadding = contentTopPadding,
                        titleTranslationRangePx = titleTranslationRangePx,
                    )
                } else {
                    GameDetailsHeader(
                        game = game,
                        contentTopPadding = contentTopPadding,
                        onTagsOverflowClick = onTagsOverflowClick,
                        titleHandoffProgress = titleHandoffProgress,
                        titleTranslationRangePx = titleTranslationRangePx,
                    )
                }
            }
            // Empty sections stay hidden so a catalog skeleton renders as a lean
            // but complete page rather than a wall of empty headers.
            if (game != null) {
                item(key = "library-status") {
                    LibraryStatusCard(
                        libraryEntry = libraryEntry,
                        onEditClicked = onEditLibraryClicked,
                        modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                    )
                }

                if (!game.summary.isNullOrBlank()) {
                    item(key = "about") {
                        AboutCard(
                            summary = game.summary,
                            modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                        )
                    }
                }

                val hasFacts = game.releaseDates.isNotEmpty() ||
                    game.releaseDateEpochSeconds != null ||
                    game.gameModes.isNotEmpty() ||
                    game.platforms.isNotEmpty() ||
                    game.timeToBeatMainSeconds != null
                if (hasFacts) {
                    item(key = "facts") {
                        GameDetailsFactsRow(
                            game = game,
                            onPlatformsClick = onPlatformsClick,
                            onGameModesClick = onGameModesClick,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                itemsIndexed(
                                    items = game.screenshots,
                                    key = { _, url -> url }
                                ) { index, screenshot ->
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
                        VideosSection(
                            videos = game.videos,
                            onVideoClick = onVideoClick,
                            modifier = Modifier.padding(horizontal = DETAILS_GUTTER)
                        )
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(items = game.similarGames, key = { it.id }) { similar ->
                                    GamePosterCard(
                                        game = similar,
                                        onClick = { onGameClick(similar.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedScreenshotIndex?.let { initialIndex ->
            game?.let { currentGame ->
                ScreenshotViewerDialog(
                    screenshots = currentGame.screenshots,
                    initialIndex = initialIndex,
                    onDismissRequest = { selectedScreenshotIndex = null },
                    onPageChanged = { selectedScreenshotIndex = it }
                )
            }
        }
    }
}

@Composable
private fun GameDetailsHeader(
    game: GameDetails,
    contentTopPadding: Dp,
    onTagsOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleHandoffProgress: () -> Float = { 0f },
    titleTranslationRangePx: Float = 0f,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (!game.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = game.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = ARTWORK_ALPHA,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = ARTWORK_SCRIM_ALPHA),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier.padding(
                top = contentTopPadding,
                start = DETAILS_GUTTER,
                end = DETAILS_GUTTER,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(HEADER_COVER_WIDTH)
                        .aspectRatio(GAME_COVER_ASPECT_RATIO)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                    if (!game.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = game.coverUrl,
                            contentDescription = null,
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer {
                                val progress = titleHandoffProgress()
                                alpha = (1f - progress).coerceIn(0f, 1f)
                                translationY = -titleTranslationRangePx * progress
                                scaleX = 1f - TITLE_DOCK_SCALE_DELTA * progress
                                scaleY = 1f - TITLE_DOCK_SCALE_DELTA * progress
                                transformOrigin = TransformOrigin(0f, TRANSFORM_ORIGIN_CENTER_Y)
                            }
                            .semantics {
                                if (titleHandoffProgress() > TITLE_A11Y_MAX_ALPHA) {
                                    hideFromAccessibility()
                                }
                            },
                    )
                    // Aggregate rating with vote count; falls back to the critic rating the
                    // catalog already carries while the details row is still hydrating.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingBadge(rating = game.totalRating ?: game.rating)
                        if (game.totalRatingCount != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.details_votes_count_format,
                                    game.totalRatingCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    game.companiesLine()?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val tagPreview = remember(game.genres, game.themes) {
                        formatHeaderTagPreview(game.genres, game.themes)
                    }
                    if (tagPreview.previewTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tagPreview.previewTags.forEach { tag ->
                                TagChip(
                                    text = tag,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (tagPreview.overflowCount > 0) {
                                val totalCount = tagPreview.previewTags.size + tagPreview.overflowCount
                                val overflowDesc = stringResource(R.string.details_more_tags_desc, totalCount)
                                OverflowTagChip(
                                    text = stringResource(R.string.details_more_count, tagPreview.overflowCount),
                                    onClick = onTagsOverflowClick,
                                    contentDescription = overflowDesc,
                                    modifier = Modifier.weight(1f, fill = false),
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

/** Two-column summary cards; a lone leftover card spans the full row. */
@Composable
private fun GameDetailsFactsRow(
    game: GameDetails,
    onPlatformsClick: () -> Unit,
    onGameModesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unknownDate = stringResource(R.string.details_date_unknown)
    val firstRelease = game.releaseDates.firstOrNull()
    val datedRelease = firstRelease?.takeIf {
        it.dateEpochSeconds != null || it.year != null
    }
    val releaseText = datedRelease?.displayDate(unknownDate)
        ?: game.releaseDateEpochSeconds?.let { epoch ->
            GameReleaseDate(
                platform = "",
                dateEpochSeconds = epoch,
            ).displayDate(unknownDate)
        }
        ?: unknownDate.takeIf { firstRelease != null }
    val mainHours = game.timeToBeatMainSeconds?.toDisplayHours()
    val topology = remember(releaseText, game.gameModes, game.platforms, mainHours) {
        resolveFactsTopology(
            hasRelease = releaseText != null,
            hasModes = game.gameModes.isNotEmpty(),
            hasPlatforms = game.platforms.isNotEmpty(),
            hasTime = mainHours != null,
        )
    }
    val cards = buildList {
        if (releaseText != null) {
            add(
                FactCardData(
                    testTag = "release",
                    icon = Icons.Filled.Event,
                    title = stringResource(R.string.details_card_release),
                    value = releaseText,
                    sub = firstRelease
                        ?.platform
                        ?.let(::formatPlatformDisplayName)
                        ?.takeIf(String::isNotBlank),
                )
            )
        }
        if (game.gameModes.isNotEmpty()) {
            val modesPreview = formatGameModesPreview(game.gameModes)
            val isClickable = game.gameModes.size > 1
            val subText = if (modesPreview.overflowCount > 0) {
                stringResource(R.string.details_more_count, modesPreview.overflowCount)
            } else {
                null
            }
            val a11yDesc = if (isClickable) {
                stringResource(R.string.details_modes_more_desc, game.gameModes.size)
            } else {
                null
            }
            add(
                FactCardData(
                    testTag = "modes",
                    icon = Icons.Filled.VideogameAsset,
                    title = stringResource(R.string.details_section_modes),
                    value = modesPreview.previewText,
                    sub = subText,
                    isClickable = isClickable,
                    onClick = if (isClickable) onGameModesClick else null,
                    contentDescription = a11yDesc,
                )
            )
        }
        if (game.platforms.isNotEmpty()) {
            val platformsLimit = if (topology.platformsFullWidth) {
                PLATFORMS_PREVIEW_LIMIT_FULL
            } else {
                PLATFORMS_PREVIEW_LIMIT_HALF
            }
            val platformsPreview = formatPlatformsPreview(
                platforms = game.platforms,
                limit = platformsLimit,
            )
            val isClickable = game.platforms.size > 1 || game.releaseDates.size > 1
            val subText = if (platformsPreview.overflowCount > 0) {
                stringResource(R.string.details_more_count, platformsPreview.overflowCount)
            } else {
                null
            }
            val a11yDesc = if (isClickable) {
                stringResource(R.string.details_platforms_more_desc, game.platforms.size)
            } else {
                null
            }
            add(
                FactCardData(
                    testTag = "platforms",
                    icon = Icons.Filled.Devices,
                    title = stringResource(R.string.details_section_platforms),
                    value = platformsPreview.previewText,
                    sub = subText,
                    isClickable = isClickable,
                    onClick = if (isClickable) onPlatformsClick else null,
                    contentDescription = a11yDesc,
                    valueMaxLines = if (topology.platformsFullWidth) 2 else 1,
                )
            )
        }
        if (mainHours != null) {
            add(
                FactCardData(
                    testTag = "time",
                    icon = Icons.Filled.Schedule,
                    title = stringResource(R.string.details_card_time_to_beat),
                    value = stringResource(R.string.details_time_hours_format, mainHours),
                    sub = game.timeToBeatCompleteSeconds?.let { complete ->
                        stringResource(
                            R.string.details_time_complete_format,
                            complete.toDisplayHours()
                        )
                    },
                )
            )
        }
    }

    Column(
        modifier = modifier.padding(horizontal = DETAILS_GUTTER),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cards.chunked(2).forEach { row ->
            if (row.size == 1) {
                // A lone leftover card spans the row so the grid never shows a
                // visually empty half (e.g. [Release][Modes] / [Platforms....]).
                val card = row.first()
                FactCard(
                    icon = card.icon,
                    title = card.title,
                    value = card.value,
                    sub = card.sub,
                    isClickable = card.isClickable,
                    onClick = card.onClick,
                    contentDescription = card.contentDescription,
                    valueMaxLines = card.valueMaxLines,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("details-fact-card-${card.testTag}"),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { card ->
                        FactCard(
                            icon = card.icon,
                            title = card.title,
                            value = card.value,
                            sub = card.sub,
                            isClickable = card.isClickable,
                            onClick = card.onClick,
                            contentDescription = card.contentDescription,
                            valueMaxLines = card.valueMaxLines,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("details-fact-card-${card.testTag}"),
                        )
                    }
                }
            }
        }
    }
}

private data class FactCardData(
    val testTag: String,
    val icon: ImageVector,
    val title: String,
    val value: String,
    val sub: String? = null,
    val isClickable: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val contentDescription: String? = null,
    val valueMaxLines: Int = 1,
)

/** Seconds in half an hour and in an hour, for rounding beats to whole hours. */
private const val HALF_HOUR_SECONDS = 1_800L
private const val HOUR_SECONDS = 3_600L

/** Rounds epoch seconds to whole display hours. */
private fun Long.toDisplayHours(): Long = (this + HALF_HOUR_SECONDS) / HOUR_SECONDS

@Composable
private fun FactCard(
    icon: ImageVector,
    title: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    valueMaxLines: Int = 1,
) {
    val clickModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick, role = Role.Button)
    } else {
        modifier
    }
    val surfaceModifier = if (contentDescription != null) {
        clickModifier.semantics { this.contentDescription = contentDescription }
    } else {
        clickModifier
    }
    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isClickable) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isClickable) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Collapsed About card: 2-line summary with an in-card header and arrow toggle. */
@Composable
private fun AboutCard(
    summary: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(summary) { mutableStateOf(false) }
    var hasVisualOverflow by remember(summary) { mutableStateOf(false) }
    val showMoreDesc = stringResource(R.string.details_about_show_more)
    val showLessDesc = stringResource(R.string.details_about_show_less)
    val actionDescription = if (expanded) showLessDesc else showMoreDesc
    val headerInteractionSource = remember { MutableInteractionSource() }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) ARROW_EXPANDED_ROTATION else ARROW_COLLAPSED_ROTATION,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "aboutArrowRotation",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .then(
                        if (hasVisualOverflow || expanded) {
                            Modifier
                                .clickable(
                                    interactionSource = headerInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { expanded = !expanded },
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = actionDescription
                                }
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.details_section_about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasVisualOverflow || expanded) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = arrowRotation },
                        )
                    }
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else ABOUT_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
                onTextLayout = { layoutResult ->
                    if (!expanded) {
                        hasVisualOverflow = layoutResult.hasVisualOverflow ||
                            layoutResult.lineCount > ABOUT_COLLAPSED_LINES
                    }
                },
            )
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

internal fun GameReleaseDate.displayDate(
    unknown: String,
    locale: Locale = Locale.getDefault(),
): String = when {
    dateEpochSeconds != null ->
        Instant.ofEpochSecond(dateEpochSeconds)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    year != null -> year.toString()
    else -> unknown
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

@Composable
private fun LibraryStatusCard(
    libraryEntry: LibraryEntry?,
    onEditClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = libraryEntry,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "LibraryStatusCard",
        modifier = modifier.fillMaxWidth(),
    ) { entry ->
        if (entry == null) {
            AddToLibraryButton(onClick = onEditClicked)
        } else {
            InLibraryCard(
                status = stringResource(entry.status.displayNameRes()),
                onClick = onEditClicked,
            )
        }
    }
}

@Composable
private fun AddToLibraryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = LibraryCta,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.library_add_to_library),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun InLibraryCard(
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = 1.5.dp,
                        color = LibraryCta,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = LibraryCta,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_in_library),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    // Status only: no platform is auto-picked from the catalog list,
                    // since the user does not choose one at add time.
                    text = status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.library_edit_action_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Header placeholder shown while no Room row exists yet (cold navigation to an
 * unseen game). Renders inside the shared LazyColumn so hydrated content
 * replaces it without a spinner swap or a scroll-state conflict.
 */
@Composable
private fun DetailsHeaderSkeleton(
    contentTopPadding: Dp,
    titleTranslationRangePx: Float,
    modifier: Modifier = Modifier,
) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("details-skeleton")
            .padding(top = contentTopPadding, start = DETAILS_GUTTER, end = DETAILS_GUTTER),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(HEADER_COVER_WIDTH)
                    .aspectRatio(GAME_COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerColor)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(SKELETON_TITLE_FRACTION)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerColor)
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(SKELETON_SUBTITLE_FRACTION)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerColor)
                )
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

/** Placeholder bar proportions for the details header skeleton. */
private const val SKELETON_TITLE_FRACTION = 0.7f
private const val SKELETON_SUBTITLE_FRACTION = 0.4f
private const val ARROW_EXPANDED_ROTATION = 180f
private const val ARROW_COLLAPSED_ROTATION = 0f

/**
 * Videos list: at least [VIDEOS_COLLAPSED_COUNT] (when available); a toggle
 * reveals the rest because the BFF caps the payload at five videos.
 */
@Composable
private fun VideosSection(
    videos: List<GameVideo>,
    onVideoClick: (GameVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(videos.map(GameVideo::videoId)) { mutableStateOf(false) }
    val hasToggle = videos.size > VIDEOS_COLLAPSED_COUNT
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) ARROW_EXPANDED_ROTATION else ARROW_COLLAPSED_ROTATION,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "videosArrowRotation",
    )

    DetailsSection(
        title = stringResource(R.string.details_section_videos),
        modifier = modifier,
    ) {
        Column {
            videos.forEachIndexed { index, video ->
                if (!hasToggle || index < VIDEOS_COLLAPSED_COUNT) {
                    GameVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeIn(),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeOut(),
                    ) {
                        GameVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            if (hasToggle) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.details_videos_show_less)
                        } else {
                            stringResource(R.string.details_videos_show_all, videos.size)
                        },
                    )
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            }
        }
    }
}
