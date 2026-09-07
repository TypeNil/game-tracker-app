package io.github.typenil.gametracker.feature.details.viewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.core.designsystem.component.rememberImageModel
import io.github.typenil.gametracker.feature.details.SCREENSHOT_ASPECT_RATIO

@Composable
internal fun ZoomableScreenshotImage(
    model: String,
    contentDescription: String,
    isCurrentPage: Boolean,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    imageReloadToken: Long = 0L,
) {
    var scale by rememberSaveable(model) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(model) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(model) { mutableFloatStateOf(0f) }
    var contentAspectRatio by rememberSaveable(model) { mutableFloatStateOf(SCREENSHOT_ASPECT_RATIO) }
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            onScaleChanged(1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        AsyncImage(
            model = rememberImageModel(model, imageReloadToken),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) {
                    contentAspectRatio = size.width / size.height
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(model) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > MIN_ZOOM + ZOOM_EPSILON) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = DOUBLE_TAP_ZOOM
                                offsetX = 0f
                                offsetY = 0f
                            }
                            onScaleChanged(scale)
                        },
                    )
                }
                .pointerInput(model, widthPx, heightPx) {
                    detectOwnedZoomPanGestures(
                        currentScale = { scale },
                    ) { pan, zoom ->
                        val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val bounds = calculateScreenshotPanBounds(
                            containerWidthPx = widthPx,
                            containerHeightPx = heightPx,
                            scale = newScale,
                            contentAspectRatio = contentAspectRatio,
                        )

                        scale = newScale
                        offsetX = if (newScale > MIN_ZOOM + ZOOM_EPSILON) {
                            (offsetX + pan.x).coerceIn(-bounds.x, bounds.x)
                        } else {
                            0f
                        }
                        offsetY = if (newScale > MIN_ZOOM + ZOOM_EPSILON) {
                            (offsetY + pan.y).coerceIn(-bounds.y, bounds.y)
                        } else {
                            0f
                        }
                        onScaleChanged(newScale)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}
