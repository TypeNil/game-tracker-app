package io.github.typenil.gametracker.feature.library.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens

const val LIBRARY_SKELETON_TEST_TAG = "library_skeleton"
const val LIBRARY_SKELETON_CARD_TEST_TAG = "library_skeleton_card"
const val LIBRARY_SKELETON_HERO_TEST_TAG = "library_skeleton_hero"
const val LIBRARY_SKELETON_META_TEST_TAG = "library_skeleton_meta"

private const val HERO_ASPECT_RATIO = 16f / 9f
private const val SKELETON_CARDS = 3
private const val TITLE_BAR_FRACTION = 0.55f
private const val SUBTITLE_BAR_FRACTION = 0.32f
private val CardShape = RoundedCornerShape(16.dp)
private val HeroShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val BarCorner = RoundedCornerShape(4.dp)
private val TitleBarHeight = 18.dp
private val SubtitleBarHeight = 12.dp
private val StatusMinHeight = 48.dp
private val StatusBarWidth = 96.dp
private val DateBarWidth = 72.dp
private val FavoritePlaceholderSize = 34.dp
private val FavoriteHitSize = 48.dp
private val MetaReserve = 64.dp
private val CardSpacing = 12.dp
private const val STACKED_FONT_SCALE = 1.3f

/**
 * Loading placeholder that mirrors [LibraryGameCard]: full-width 16:9 hero plus a
 * status/date meta strip. Not [io.github.typenil.gametracker.core.designsystem.component.FeedSkeleton],
 * which matches Discover/Search poster rows.
 */
@Composable
fun LibraryCardSkeleton(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.library_loading)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(LIBRARY_SKELETON_TEST_TAG)
            .semantics { contentDescription = loadingDescription },
    ) {
        val contentWidth = (maxWidth - GtDimens.Gutter * 2).coerceAtLeast(0.dp)
        val cardHeight = contentWidth / HERO_ASPECT_RATIO + MetaReserve
        val rows = ((maxHeight - GtDimens.Gutter * 2 + CardSpacing) / (cardHeight + CardSpacing))
            .toInt()
            .coerceIn(1, SKELETON_CARDS)
        Column(
            modifier = Modifier.padding(GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            repeat(rows) {
                LibrarySkeletonCard()
            }
        }
    }
}

@Composable
private fun LibrarySkeletonCard() {
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LIBRARY_SKELETON_CARD_TEST_TAG),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(HERO_ASPECT_RATIO)
                    .clip(HeroShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag(LIBRARY_SKELETON_HERO_TEST_TAG),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(TITLE_BAR_FRACTION)
                            .height(TitleBarHeight)
                            .clip(BarCorner)
                            .background(barColor),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(SUBTITLE_BAR_FRACTION)
                            .height(SubtitleBarHeight)
                            .clip(BarCorner)
                            .background(barColor),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(FavoriteHitSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(FavoritePlaceholderSize)
                            .clip(CircleShape)
                            .background(barColor),
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            val useStackedMetadata = LocalDensity.current.fontScale >= STACKED_FONT_SCALE
            if (useStackedMetadata) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LIBRARY_SKELETON_META_TEST_TAG)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SkeletonBar(
                        color = barColor,
                        modifier = Modifier
                            .width(StatusBarWidth)
                            .height(StatusMinHeight),
                    )
                    SkeletonBar(
                        color = barColor,
                        modifier = Modifier
                            .width(DateBarWidth)
                            .height(SubtitleBarHeight),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LIBRARY_SKELETON_META_TEST_TAG)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBar(
                        color = barColor,
                        modifier = Modifier
                            .width(StatusBarWidth)
                            .height(StatusMinHeight),
                    )
                    SkeletonBar(
                        color = barColor,
                        modifier = Modifier
                            .width(DateBarWidth)
                            .height(SubtitleBarHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(BarCorner)
            .background(color),
    )
}
