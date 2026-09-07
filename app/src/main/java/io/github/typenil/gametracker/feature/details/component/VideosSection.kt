package io.github.typenil.gametracker.feature.details.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.feature.details.ARROW_COLLAPSED_ROTATION
import io.github.typenil.gametracker.feature.details.ARROW_EXPANDED_ROTATION
import io.github.typenil.gametracker.feature.details.DetailsSection

/** Videos visible before the "Show all" toggle (BFF caps the list at five). */
private const val VIDEOS_COLLAPSED_COUNT = 2

/**
 * Videos list: at least [VIDEOS_COLLAPSED_COUNT] (when available); a toggle
 * reveals the rest because the BFF caps the payload at five videos.
 */
@Composable
internal fun VideosSection(
    videos: List<GameVideo>,
    onVideoClick: (GameVideo) -> Unit,
    modifier: Modifier = Modifier,
    imageReloadToken: Long = 0L,
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
                        imageReloadToken = imageReloadToken,
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
                            imageReloadToken = imageReloadToken,
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
