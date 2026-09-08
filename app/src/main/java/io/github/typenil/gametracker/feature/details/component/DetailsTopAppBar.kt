package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.feature.details.TITLE_DOCK_SCALE_DELTA
import io.github.typenil.gametracker.feature.details.TRANSFORM_ORIGIN_CENTER_Y

private const val TITLE_DOCK_SCALE_MIN = 0.92f
private const val APP_BAR_SCRIM_MAX_ALPHA = 0.45f
private const val TITLE_A11Y_MIN_ALPHA = 0.05f
private val TITLE_DOCK_TRANSLATION_RANGE = 12.dp
private val APP_BAR_BG_SCROLL_THRESHOLD = 120.dp
private val TITLE_HANDOFF_START_OFFSET = 90.dp
private val TITLE_HANDOFF_END_OFFSET = 150.dp

@Stable
internal class DetailsAppBarScrollState(
    val lazyListState: LazyListState,
    val titleTranslationRangePx: Float,
    val appBarBgAlpha: () -> Float,
    val titleHandoffProgress: () -> Float,
)

@Composable
internal fun rememberDetailsAppBarScrollState(
    lazyListState: LazyListState = rememberLazyListState(),
): DetailsAppBarScrollState {
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

    return remember(lazyListState, titleTranslationRangePx, appBarBgAlpha, titleHandoffProgress) {
        DetailsAppBarScrollState(
            lazyListState = lazyListState,
            titleTranslationRangePx = titleTranslationRangePx,
            appBarBgAlpha = appBarBgAlpha,
            titleHandoffProgress = titleHandoffProgress,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsTopAppBar(
    gameName: String?,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    titleHandoffProgress: () -> Float,
    appBarBgAlpha: () -> Float,
    titleTranslationRangePx: Float,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
    )
}
