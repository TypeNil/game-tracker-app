package io.github.typenil.gametracker.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.MainActivity
import io.github.typenil.gametracker.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationViewportTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private fun viewportBounds() = composeTestRule
        .onNodeWithTag("app-nav-viewport")
        .getUnclippedBoundsInRoot()

    @Test
    fun navViewport_keepsBoundsWhileNavigatingToDetailsAndBack() {
        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(BuildConfig.APPLICATION_ID)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val sampleGameTitle = "Elden Ring"
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(sampleGameTitle).fetchSemanticsNodes().isNotEmpty()
            }

            val viewportBefore = viewportBounds()
            // Pause animation clock to sample active transition frames
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onAllNodesWithText(sampleGameTitle)
                .onFirst()
                .performClick()
            fun advanceToElapsed(startMillis: Long, targetMillis: Long) {
                val remaining = targetMillis - (composeTestRule.mainClock.currentTime - startMillis)
                if (remaining > 0) {
                    composeTestRule.mainClock.advanceTimeBy(remaining)
                }
            }

            // Sample forward transition: immediately, midway (150ms), and near end (300ms)
            val forwardStart = composeTestRule.mainClock.currentTime
            composeTestRule.mainClock.advanceTimeByFrame()
            assertEquals(viewportBefore, viewportBounds())

            advanceToElapsed(forwardStart, 150)
            assertEquals(viewportBefore, viewportBounds())

            advanceToElapsed(forwardStart, 300)
            assertEquals(viewportBefore, viewportBounds())

            // Settle forward animation and verify details screen content
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(sampleGameTitle).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(viewportBefore, viewportBounds())

            // Pause clock for pop transition
            composeTestRule.mainClock.autoAdvance = false
            val popStart = composeTestRule.mainClock.currentTime
            scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }

            // Sample pop transition: immediately, midway (150ms), and near end (300ms)
            composeTestRule.mainClock.advanceTimeByFrame()
            assertEquals(viewportBefore, viewportBounds())

            advanceToElapsed(popStart, 150)
            assertEquals(viewportBefore, viewportBounds())

            advanceToElapsed(popStart, 300)
            assertEquals(viewportBefore, viewportBounds())
            // Settle return to discover
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(sampleGameTitle).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(viewportBefore, viewportBounds())
        }
    }
}
