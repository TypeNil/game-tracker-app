package io.github.typenil.gametracker.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.typenil.gametracker.core.designsystem.component.contentColor
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN_CONTRAST_RATIO = 4.5f
private const val LUMINANCE_OFFSET = 0.05f
private val LightBackgroundColor = Color(0xFFF3EDF7)
private val DarkBackgroundColor = Color(0xFF131320)
private val LibraryCtaColor = Color(0xFF4E3DCA)

class LibraryStatusContrastTest {

    @Test
    fun statusColors_meetContrastInLightAndDarkThemes() {
        LibraryStatus.entries.forEach { status ->
            val lightForeground = status.contentColor(isDarkTheme = false)
            val lightContrast = contrastRatio(lightForeground, LightBackgroundColor)
            assertTrue(
                "Light contrast for $status was $lightContrast, expected >= $MIN_CONTRAST_RATIO",
                lightContrast >= MIN_CONTRAST_RATIO,
            )

            val darkForeground = status.contentColor(isDarkTheme = true)
            val darkContrast = contrastRatio(darkForeground, DarkBackgroundColor)
            assertTrue(
                "Dark contrast for $status was $darkContrast, expected >= $MIN_CONTRAST_RATIO",
                darkContrast >= MIN_CONTRAST_RATIO,
            )
        }

        val ctaContrast = contrastRatio(Color.White, LibraryCtaColor)
        assertTrue(
            "Library CTA contrast was $ctaContrast, expected >= $MIN_CONTRAST_RATIO",
            ctaContrast >= MIN_CONTRAST_RATIO,
        )
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }
}
