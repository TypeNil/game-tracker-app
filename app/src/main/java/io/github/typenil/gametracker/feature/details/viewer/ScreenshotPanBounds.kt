package io.github.typenil.gametracker.feature.details.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import io.github.typenil.gametracker.feature.details.SCREENSHOT_ASPECT_RATIO

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 4f
internal const val DOUBLE_TAP_ZOOM = 2.5f
internal const val ZOOM_EPSILON = 0.01f

internal fun calculateScreenshotPanBounds(
    containerWidthPx: Float,
    containerHeightPx: Float,
    scale: Float,
    contentAspectRatio: Float = SCREENSHOT_ASPECT_RATIO,
): Offset {
    val fittedWidthPx = minOf(
        containerWidthPx,
        containerHeightPx * contentAspectRatio,
    )
    val fittedHeightPx = fittedWidthPx / contentAspectRatio

    return Offset(
        x = ((fittedWidthPx * scale - containerWidthPx) / 2f).coerceAtLeast(0f),
        y = ((fittedHeightPx * scale - containerHeightPx) / 2f).coerceAtLeast(0f),
    )
}

internal suspend fun PointerInputScope.detectOwnedZoomPanGestures(
    currentScale: () -> Float,
    onGesture: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var ownsGesture = false

        do {
            val event = awaitPointerEvent()
            val pressedPointers = event.changes.count { it.pressed }

            if (!ownsGesture) {
                ownsGesture =
                    currentScale() > MIN_ZOOM + ZOOM_EPSILON ||
                    pressedPointers >= 2
            }

            if (ownsGesture) {
                onGesture(
                    event.calculatePan(),
                    event.calculateZoom(),
                )
                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
