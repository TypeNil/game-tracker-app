package io.github.typenil.gametracker.feature.library.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickHoursDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dialog_rendersTitleGameNameAndInitialHours() {
        composeTestRule.setContent {
            GameTrackerTheme {
                QuickHoursDialog(
                    gameName = "Elden Ring",
                    initialHours = 120,
                    onDismissRequest = {},
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_log_hours),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()
        composeTestRule.onNodeWithText("120").assertIsDisplayed()
    }

    @Test
    fun dialog_stepperButtons_incrementAndDecrement() {
        var confirmedHours: Int? = null
        composeTestRule.setContent {
            GameTrackerTheme {
                QuickHoursDialog(
                    gameName = "Hades",
                    initialHours = 5,
                    onDismissRequest = {},
                    onConfirm = { confirmedHours = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_hours_increment_desc),
        ).performClick()
        composeTestRule.onNodeWithText("6").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_hours_decrement_desc),
        ).performClick()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()

        composeTestRule.onNodeWithTag(QUICK_HOURS_SAVE_TEST_TAG).performClick()
        assertEquals(5, confirmedHours)
    }

    @Test
    fun dialog_quickChips_addHours() {
        var confirmedHours: Int? = null
        composeTestRule.setContent {
            GameTrackerTheme {
                QuickHoursDialog(
                    gameName = "Hades",
                    initialHours = 10,
                    onDismissRequest = {},
                    onConfirm = { confirmedHours = it },
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_quick_add_1h),
        ).performClick()
        composeTestRule.onNodeWithText("11").assertIsDisplayed()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_quick_add_5h),
        ).performClick()
        composeTestRule.onNodeWithText("16").assertIsDisplayed()

        composeTestRule.onNodeWithTag(QUICK_HOURS_SAVE_TEST_TAG).performClick()
        assertEquals(16, confirmedHours)
    }

    @Test
    fun dialog_dismiss_triggersCallback() {
        var dismissed = false
        composeTestRule.setContent {
            GameTrackerTheme {
                QuickHoursDialog(
                    gameName = "Hades",
                    initialHours = 10,
                    onDismissRequest = { dismissed = true },
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_cancel),
        ).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun dialog_emptyInput_disablesSaveAndDoesNotConfirm() {
        var confirmedHours: Int? = null
        composeTestRule.setContent {
            GameTrackerTheme {
                QuickHoursDialog(
                    gameName = "Hades",
                    initialHours = 120,
                    onDismissRequest = {},
                    onConfirm = { confirmedHours = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(QUICK_HOURS_INPUT_TEST_TAG).performTextClearance()
        composeTestRule.onNodeWithTag(QUICK_HOURS_SAVE_TEST_TAG).assertIsNotEnabled()
        assertEquals(null, confirmedHours)
    }
}
