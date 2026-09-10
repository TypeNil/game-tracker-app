package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.component.GAME_COVER_ASPECT_RATIO
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens

internal val detailsHeaderCoverWidth = 124.dp
internal val detailsHeaderCoverCorner = 12.dp

private const val SKELETON_TITLE_FRACTION = 0.7f
private const val SKELETON_SUBTITLE_FRACTION = 0.4f

/**
 * Header placeholder shown while no Room row exists yet (cold navigation to an
 * unseen game). Renders inside the shared LazyColumn so hydrated content
 * replaces it without a spinner swap or a scroll-state conflict.
 */
@Composable
internal fun DetailsHeaderSkeleton(
    contentTopPadding: Dp,
    titleTranslationRangePx: Float,
    modifier: Modifier = Modifier,
) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("details-skeleton")
            .padding(top = contentTopPadding, start = GtDimens.Gutter, end = GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(detailsHeaderCoverWidth)
                    .aspectRatio(GAME_COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(detailsHeaderCoverCorner))
                    .background(shimmerColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(SKELETON_TITLE_FRACTION)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerColor),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(SKELETON_SUBTITLE_FRACTION)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerColor),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerColor),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("details-skeleton-library-cta"),
        )
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}
