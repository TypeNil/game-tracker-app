package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

private const val SKELETON_ROWS = 3
private const val TITLE_BAR_FRACTION = 0.7f
private const val SUBTITLE_BAR_FRACTION = 0.4f
private val CoverWidth = 100.dp
private val TitleBarHeight = 16.dp
private val SubtitleBarHeight = 12.dp
private val TextGap = 8.dp
private val BarCorner = 4.dp
private val CoverTextGap = 14.dp
private val LabelReserve = 24.dp

@Composable
fun FeedSkeleton(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val rowHeight = CoverWidth / GAME_COVER_ASPECT_RATIO + GtDimens.Card
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FEED_SKELETON_TEST_TAG),
    ) {
        val labelReserve = if (label.isNullOrBlank()) 0.dp else LabelReserve
        val rows = ((maxHeight - GtDimens.Gutter * 2 - labelReserve) / rowHeight)
            .toInt()
            .coerceIn(1, SKELETON_ROWS)
        Column(
            modifier = Modifier.padding(GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(GtDimens.Card),
        ) {
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            repeat(rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FEED_SKELETON_ROW_TEST_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(CoverWidth)
                            .aspectRatio(GAME_COVER_ASPECT_RATIO)
                            .clip(RoundedCornerShape(GtDimens.Card))
                            .background(barColor),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = CoverTextGap),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(TITLE_BAR_FRACTION)
                                .height(TitleBarHeight)
                                .clip(RoundedCornerShape(BarCorner))
                                .background(barColor),
                        )
                        Spacer(Modifier.height(TextGap))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(SUBTITLE_BAR_FRACTION)
                                .height(SubtitleBarHeight)
                                .clip(RoundedCornerShape(BarCorner))
                                .background(barColor),
                        )
                    }
                }
            }
        }
    }
}
