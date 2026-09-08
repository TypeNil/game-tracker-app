package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.GamePosterCard
import io.github.typenil.gametracker.core.designsystem.component.rememberImageModel
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.feature.details.DetailsSection
import io.github.typenil.gametracker.feature.details.SCREENSHOT_ASPECT_RATIO
import io.github.typenil.gametracker.feature.details.viewer.ScreenshotViewerDialog

private val DETAILS_GUTTER = GtDimens.Gutter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsContent(
    game: GameDetails?,
    libraryEntry: LibraryEntry?,
    libraryLoadError: AppError?,
    isLibraryLoading: Boolean,
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
    imageReloadToken: Long = 0L,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var selectedScreenshotIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 0.dp, bottom = DETAILS_GUTTER),
            verticalArrangement = Arrangement.spacedBy(DETAILS_GUTTER),
            modifier = Modifier.fillMaxSize(),
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
                        imageReloadToken = imageReloadToken,
                    )
                }
            }
            // Empty sections stay hidden so a catalog skeleton renders as a lean
            // but complete page rather than a wall of empty headers.
            if (game != null) {
                item(key = "library-status") {
                    if (libraryLoadError != null) {
                        LibraryUnavailableCard(
                            modifier = Modifier.padding(horizontal = DETAILS_GUTTER),
                        )
                    } else {
                        LibraryStatusCard(
                            libraryEntry = libraryEntry,
                            isLibraryLoading = isLibraryLoading,
                            onEditClicked = onEditLibraryClicked,
                            modifier = Modifier.padding(horizontal = DETAILS_GUTTER),
                        )
                    }
                }

                if (!game.summary.isNullOrBlank()) {
                    item(key = "about") {
                        AboutCard(
                            summary = game.summary,
                            modifier = Modifier.padding(horizontal = DETAILS_GUTTER),
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (game.screenshots.isNotEmpty()) {
                    item(key = "screenshots") {
                        DetailsSection(
                            title = stringResource(R.string.details_section_screenshots),
                            modifier = Modifier.fillMaxWidth(),
                            titleModifier = Modifier.padding(horizontal = DETAILS_GUTTER),
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = DETAILS_GUTTER),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(
                                    items = game.screenshots,
                                    key = { _, url -> url },
                                ) { index, screenshot ->
                                    val screenshotDesc = stringResource(R.string.details_screenshot_desc)
                                    AsyncImage(
                                        model = rememberImageModel(screenshot, imageReloadToken),
                                        contentDescription = screenshotDesc,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(260.dp)
                                            .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = screenshotDesc,
                                            ) { selectedScreenshotIndex = index },
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
                            imageReloadToken = imageReloadToken,
                            modifier = Modifier.padding(horizontal = DETAILS_GUTTER),
                        )
                    }
                }

                if (game.similarGames.isNotEmpty()) {
                    item(key = "similar") {
                        DetailsSection(
                            title = stringResource(R.string.details_section_similar),
                            modifier = Modifier.fillMaxWidth(),
                            titleModifier = Modifier.padding(horizontal = DETAILS_GUTTER),
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
                                        imageReloadToken = imageReloadToken,
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
                    onPageChanged = { selectedScreenshotIndex = it },
                    imageReloadToken = imageReloadToken,
                )
            }
        }
    }
}
