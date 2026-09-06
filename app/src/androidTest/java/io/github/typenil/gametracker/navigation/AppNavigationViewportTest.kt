package io.github.typenil.gametracker.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    @Test
    fun navViewport_keepsBoundsWhileNavigatingToDetailsAndBack() {
        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(BuildConfig.APPLICATION_ID)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val discoverNavLabel = context.getString(R.string.nav_discover)
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(discoverNavLabel).fetchSemanticsNodes().isNotEmpty()
            }

            // Capture unclipped viewport bounds in root
            val viewportBefore = composeTestRule
                .onNodeWithTag("app-nav-viewport")
                .getUnclippedBoundsInRoot()

            // Wait for catalog games to be visible
            val sampleGameTitle = "The Witcher 3: Wild Hunt"
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(sampleGameTitle).fetchSemanticsNodes().isNotEmpty()
            }

            // Click game card to trigger navigation to GameDetailsKey
            composeTestRule.onAllNodesWithText(sampleGameTitle)
                .onFirst()
                .performClick()

            // Verify viewport bounds remain stable through transition and after settling
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                val currentBounds = composeTestRule
                    .onNodeWithTag("app-nav-viewport")
                    .getUnclippedBoundsInRoot()
                currentBounds == viewportBefore
            }

            val viewportSettled = composeTestRule
                .onNodeWithTag("app-nav-viewport")
                .getUnclippedBoundsInRoot()
            assertEquals(viewportBefore, viewportSettled)

            // Navigate back
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                val currentBounds = composeTestRule
                    .onNodeWithTag("app-nav-viewport")
                    .getUnclippedBoundsInRoot()
                currentBounds == viewportBefore
            }

            val viewportReturned = composeTestRule
                .onNodeWithTag("app-nav-viewport")
                .getUnclippedBoundsInRoot()
            assertEquals(viewportBefore, viewportReturned)
        }
    }
}
