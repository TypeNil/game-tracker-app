package io.github.typenil.gametracker.feature.library.insights

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryInsightsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun empty_showsEmptyCopy() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryInsightsScreen(
                    uiState = LibraryInsightsUiState.Empty,
                    onBackClick = {},
                    onGameClick = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_INSIGHTS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.insights_empty_title),
        ).assertIsDisplayed()
    }

    @Test
    fun content_showsGameNameAndMostPlayedClick_emitsGameId() {
        var clickedId: Long? = null
        val insights = computeLibraryInsights(
            listOf(
                LibraryGame(
                    game = Game(id = 42L, name = "Hades"),
                    entry = LibraryEntry(
                        gameId = 42L,
                        status = LibraryStatus.PLAYING,
                        addedAtEpochSeconds = 1L,
                        updatedAtEpochSeconds = 1L,
                        hoursPlayed = 12,
                    ),
                ),
            ),
        )

        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryInsightsScreen(
                    uiState = LibraryInsightsUiState.Content(insights),
                    onBackClick = {},
                    onGameClick = { clickedId = it },
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_INSIGHTS_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(mostPlayedRowTestTag(42L)))
        composeTestRule.onNodeWithTag(mostPlayedRowTestTag(42L)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(mostPlayedRowTestTag(42L)).performClick()
        assertEquals(42L, clickedId)
    }
}
