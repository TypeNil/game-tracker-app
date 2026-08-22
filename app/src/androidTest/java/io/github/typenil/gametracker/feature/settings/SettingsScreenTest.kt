package io.github.typenil.gametracker.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun setContent(onOpenIgdb: () -> Unit = {}) {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    onBackClick = {},
                    onOpenIgdb = onOpenIgdb
                )
            }
        }
    }

    @Test
    fun attributionCopy_isVisible() {
        setContent()

        composeTestRule.onNodeWithText("Game data provided by IGDB").assertIsDisplayed()
    }

    @Test
    fun igdbLink_invokesCallback() {
        var clicked = false
        setContent(onOpenIgdb = { clicked = true })

        composeTestRule.onNodeWithText("IGDB").performClick()

        composeTestRule.runOnIdle { assertTrue(clicked) }
    }
}
