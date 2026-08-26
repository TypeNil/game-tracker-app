package io.github.typenil.gametracker.core.designsystem.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens

private const val SKELETON_ROWS = 3
private const val TITLE_BAR_FRACTION = 0.7f
private const val SUBTITLE_BAR_FRACTION = 0.4f
private val CoverWidth = 96.dp
private val TitleBarHeight = 16.dp
private val SubtitleBarHeight = 12.dp
private val TextGap = 8.dp
private val BarCorner = 4.dp
private val CoverTextGap = 14.dp

@Composable
fun FeedSkeleton(
    label: String,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("feed_skeleton")
            .padding(GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(GtDimens.Card),
    ) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(CoverWidth)
                        .aspectRatio(GAME_COVER_ASPECT_RATIO)
                        .clip(RoundedCornerShape(GtDimens.Card))
                        .background(barColor)
                        .clearAndSetSemantics {},
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
                            .background(barColor)
                            .clearAndSetSemantics {},
                    )
                    Spacer(Modifier.height(TextGap))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(SUBTITLE_BAR_FRACTION)
                            .height(SubtitleBarHeight)
                            .clip(RoundedCornerShape(BarCorner))
                            .background(barColor)
                            .clearAndSetSemantics {},
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
