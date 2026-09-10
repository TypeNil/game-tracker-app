package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens

const val FEED_SKELETON_TEST_TAG = "feed_skeleton"
const val FEED_SKELETON_ROW_TEST_TAG = "feed_skeleton_row"
const val FEED_SKELETON_ACTION_TEST_TAG = "feed_skeleton_action"

private const val SKELETON_ROWS = 3
private const val TITLE_BAR_FRACTION = 0.7f
private val TitleBarHeight = 16.dp
private val ChipBarHeight = 22.dp
private val ChipBarWidth = 64.dp
private val TextGap = 8.dp
private val BarCorner = 4.dp
private val CoverTextGap = 14.dp
private val LabelReserve = 24.dp
private val HeaderReserve = 48.dp
private val LibraryActionSize = 48.dp

@Composable
fun FeedSkeleton(
    modifier: Modifier = Modifier,
    label: String? = null,
    showLibraryAction: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val coverHeight = GAME_CARD_COVER_WIDTH_DP.dp / GAME_COVER_ASPECT_RATIO
    val rowHeight = coverHeight + GtDimens.Card
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FEED_SKELETON_TEST_TAG),
    ) {
        val labelReserve = if (label.isNullOrBlank()) 0.dp else LabelReserve
        val headerReserve = if (header == null) 0.dp else HeaderReserve + GtDimens.Card
        val rows = ((maxHeight - GtDimens.Gutter * 2 - labelReserve - headerReserve) / rowHeight)
            .toInt()
            .coerceIn(1, SKELETON_ROWS)
        Column(
            modifier = Modifier.padding(GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(GtDimens.Card),
        ) {
            header?.invoke()
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            repeat(rows) {
                SkeletonCardRow(showLibraryAction = showLibraryAction)
            }
        }
    }
}

/**
 * Single GameCard-shaped skeleton row: shared 100dp start-rounded cover, Surface chrome,
 * title/chip bars, and an optional trailing 48.dp library-action slot.
 */
@Composable
fun SkeletonCardRow(
    modifier: Modifier = Modifier,
    showLibraryAction: Boolean = false,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FEED_SKELETON_ROW_TEST_TAG),
        shape = GameCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(GAME_CARD_COVER_WIDTH_DP.dp)
                    .aspectRatio(GAME_COVER_ASPECT_RATIO)
                    .clip(GameCardCoverShape)
                    .background(barColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = CoverTextGap, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(TextGap),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(TITLE_BAR_FRACTION)
                        .height(TitleBarHeight)
                        .clip(RoundedCornerShape(BarCorner))
                        .background(barColor),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(ChipBarWidth)
                            .height(ChipBarHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(barColor),
                    )
                    Box(
                        modifier = Modifier
                            .width(ChipBarWidth)
                            .height(ChipBarHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(barColor),
                    )
                }
            }
            if (showLibraryAction) {
                Box(
                    modifier = Modifier
                        .size(LibraryActionSize)
                        .testTag(FEED_SKELETON_ACTION_TEST_TAG),
                )
            }
        }
    }
}
