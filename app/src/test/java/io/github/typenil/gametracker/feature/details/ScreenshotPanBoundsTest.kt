package io.github.typenil.gametracker.feature.details

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotPanBoundsTest {

    @Test
    fun calculateScreenshotPanBounds_portraitViewport_doesNotAllowBlankVerticalPan() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val scale = 2.5f

        val bounds = calculateScreenshotPanBounds(
            containerWidthPx = containerWidth,
            containerHeightPx = containerHeight,
            scale = scale,
        )

        // At 2.5x zoom on a 16:9 image in a 1080x2400 screen:
        // fittedWidth = 1080, scaledWidth = 2700 -> horizontal pan = (2700 - 1080) / 2 = 810
        // fittedHeight = 607.5, scaledHeight = 1518.75 < 2400 -> vertical pan must be strictly 0
        assertEquals(810f, bounds.x, 0.01f)
        assertEquals(0f, bounds.y, 0.01f)
    }

    @Test
    fun calculateScreenshotPanBounds_atOneX_hasZeroBounds() {
        val bounds = calculateScreenshotPanBounds(
            containerWidthPx = 1080f,
            containerHeightPx = 2400f,
            scale = 1f,
        )
        assertEquals(0f, bounds.x, 0.01f)
        assertEquals(0f, bounds.y, 0.01f)
    }

    @Test
    fun calculateScreenshotPanBounds_fourByThreeContent_landscapeViewport_clampsToRealBounds() {
        val containerWidth = 2400f
        val containerHeight = 1080f
        val scale = 2f
        val fourByThreeAspect = 4f / 3f

        val bounds = calculateScreenshotPanBounds(
            containerWidthPx = containerWidth,
            containerHeightPx = containerHeight,
            scale = scale,
            contentAspectRatio = fourByThreeAspect,
        )

        // In 2400x1080 with 4:3 content:
        // fittedWidth = minOf(2400, 1080 * 4/3 = 1440) = 1440
        // fittedHeight = 1440 / (4/3) = 1080
        // At 2x zoom:
        // scaledWidth = 1440 * 2 = 2880 -> horizontal pan = (2880 - 2400) / 2 = 240
        // scaledHeight = 1080 * 2 = 2160 -> vertical pan = (2160 - 1080) / 2 = 540
        assertEquals(240f, bounds.x, 0.01f)
        assertEquals(540f, bounds.y, 0.01f)
    }
}
