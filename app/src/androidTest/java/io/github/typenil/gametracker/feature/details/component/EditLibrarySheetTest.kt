package io.github.typenil.gametracker.feature.details.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditLibrarySheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statusSelection_updatesSelectedStatus() {
        var savedStatus: LibraryStatus? = null

        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = null,
                        onDismiss = {},
                        onSave = { status, _, _, _, _ -> savedStatus = status },
                        onDeleteClick = null,
                    )
                }
            }
        }

        val completedText = composeTestRule.activity.getString(R.string.library_status_completed)
        composeTestRule.onNodeWithText(completedText).performClick()
        composeTestRule.onNodeWithText(completedText).assertIsSelected()

        val saveText = composeTestRule.activity.getString(R.string.library_add_to_library)
        // Click the bottom save button (matched by text and clickable)
        composeTestRule.onNode(hasText(saveText) and hasClickAction()).performClick()
        assertEquals(LibraryStatus.COMPLETED, savedStatus)
    }

    @Test
    fun rating_tapSelects_reTapClears_andClearButtonResets() {
        var savedRating: Int? = -1

        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = null,
                        onDismiss = {},
                        onSave = { _, rating, _, _, _ -> savedRating = rating },
                        onDeleteClick = null,
                    )
                }
            }
        }

        // Tap on rating "8"
        val rating8Desc = composeTestRule.activity.getString(R.string.library_rating_format, 8)
        composeTestRule.onNodeWithContentDescription(rating8Desc).performClick()
        composeTestRule.onNodeWithText("8/10").assertIsDisplayed()

        // Re-tap rating "8" toggles to null
        composeTestRule.onNodeWithContentDescription(rating8Desc).performClick()
        composeTestRule.onNodeWithText("8/10").assertDoesNotExist()

        // Tap rating "5"
        val rating5Desc = composeTestRule.activity.getString(R.string.library_rating_format, 5)
        composeTestRule.onNodeWithContentDescription(rating5Desc).performClick()
        composeTestRule.onNodeWithText("5/10").assertIsDisplayed()

        // Tap "Clear" button
        val clearText = composeTestRule.activity.getString(R.string.library_clear_rating)
        composeTestRule.onNodeWithText(clearText).performClick()
        composeTestRule.onNodeWithText("5/10").assertDoesNotExist()
    }

    @Test
    fun rating_horizontalScrub_updatesRating() {
        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = null,
                        onDismiss = {},
                        onSave = { _, _, _, _, _ -> },
                        onDeleteClick = null,
                    )
                }
            }
        }

        // Initially no rating is selected
        val clearText = composeTestRule.activity.getString(R.string.library_clear_rating)
        composeTestRule.onNodeWithText(clearText).assertDoesNotExist()

        // Swipe right from "1" across the rating bar
        val rating1Desc = composeTestRule.activity.getString(R.string.library_rating_format, 1)
        composeTestRule.onNodeWithContentDescription(rating1Desc).performTouchInput {
            swipeRight(startX = 10f, endX = 800f, durationMillis = 200)
        }

        // A rating was selected and clear button appeared
        composeTestRule.onNodeWithText(clearText).assertIsDisplayed()
    }

    @Test
    fun headerCloseButton_invokesOnDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = null,
                        onDismiss = { dismissed = true },
                        onSave = { _, _, _, _, _ -> },
                        onDeleteClick = null,
                    )
                }
            }
        }

        val cancelDesc = composeTestRule.activity.getString(R.string.library_cancel)
        composeTestRule.onNodeWithContentDescription(cancelDesc).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun existingEntry_deleteClick_invokesOnDelete() {
        var deleteClicked = false
        val entry = LibraryEntry(
            gameId = 42L,
            status = LibraryStatus.PLAYING,
            userRating = 9,
            userNotes = "Good game",
            isFavorite = true,
            hoursPlayed = 15,
            addedAtEpochSeconds = 1000L,
            updatedAtEpochSeconds = 1000L,
        )

        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = entry,
                        onDismiss = {},
                        onSave = { _, _, _, _, _ -> },
                        onDeleteClick = { deleteClicked = true },
                    )
                }
            }
        }

        val removeDesc = composeTestRule.activity.getString(R.string.library_remove)
        composeTestRule.onNodeWithContentDescription(removeDesc).performClick()
        assertTrue(deleteClicked)
    }
}
