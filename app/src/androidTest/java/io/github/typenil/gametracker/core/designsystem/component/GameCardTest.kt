package io.github.typenil.gametracker.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GameCardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val game = Game(id = 7L, name = "Hades II")

    @Test
    fun libraryAction_doesNotInvokeCardOnClick() {
        var cardClicks = 0
        var actionId: Long? = null
        composeTestRule.setContent {
            GameTrackerTheme {
                GameCard(
                    game = game,
                    onClick = { cardClicks++ },
                    onLibraryAction = { actionId = it.id },
                )
            }
        }
        composeTestRule.onNodeWithTag(GAME_CARD_LIBRARY_ACTION_TEST_TAG).performClick()
        assertEquals(7L, actionId)
        assertEquals(0, cardClicks)
    }

    @Test
    fun titleClick_invokesCardOnClick_notLibraryAction() {
        var cardClicks = 0
        var actionClicks = 0
        composeTestRule.setContent {
            GameTrackerTheme {
                GameCard(
                    game = game,
                    onClick = { cardClicks++ },
                    libraryStatus = LibraryStatus.PLAYING,
                    onLibraryAction = { actionClicks++ },
                )
            }
        }
        composeTestRule.onNodeWithText("Hades II").performClick()
        assertEquals(1, cardClicks)
        assertEquals(0, actionClicks)
    }

    @Test
    fun missingAction_hidesButton() {
        composeTestRule.setContent {
            GameTrackerTheme {
                GameCard(game = game, onClick = {})
            }
        }
        composeTestRule.onNodeWithTag(GAME_CARD_LIBRARY_ACTION_TEST_TAG).assertDoesNotExist()
    }
}
