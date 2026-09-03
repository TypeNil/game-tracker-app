package io.github.typenil.gametracker.feature.details.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
        val rating8Desc = composeTestRule.activity.getString(R.string.library_rating_out_of_ten, 8)
        composeTestRule.onNodeWithContentDescription(rating8Desc).performClick()
        composeTestRule.onNodeWithText("8/10").assertIsDisplayed()

        // Re-tap rating "8" toggles to null
        composeTestRule.onNodeWithContentDescription(rating8Desc).performClick()
        composeTestRule.onNodeWithText("8/10").assertDoesNotExist()

        // Tap rating "5"
        val rating5Desc = composeTestRule.activity.getString(R.string.library_rating_out_of_ten, 5)
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

        // Swipe right across the rating scrubber bar using node bounds
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_RATING_BAR_TEST_TAG).performTouchInput {
            swipeRight()
        }
        // Rating 10 was selected on the far right and clear button appeared
        composeTestRule.onNodeWithText("10/10").assertIsDisplayed()
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

        val closeDesc = composeTestRule.activity.getString(R.string.library_close)
        composeTestRule.onNodeWithContentDescription(closeDesc).performClick()
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

    @Test
    fun hoursSection_hiddenForWishlist_butHoursRetainedOnSave() {
        var savedHours = -1
        var savedStatus: LibraryStatus? = null

        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = null,
                        onDismiss = {},
                        onSave = { status, _, hours, _, _ ->
                            savedStatus = status
                            savedHours = hours
                        },
                        onDeleteClick = null,
                    )
                }
            }
        }

        // Initially PLAYING -> hours section is visible. Click "+5h"
        val quickAdd5h = composeTestRule.activity.getString(R.string.library_quick_add_5h)
        composeTestRule.onNodeWithText(quickAdd5h).performClick()

        // Switch to Wishlist -> hours section hides, retained progress notice appears
        val wishlistText = composeTestRule.activity.getString(R.string.library_status_wishlist)
        composeTestRule.onNodeWithText(wishlistText).performClick()
        composeTestRule.onNodeWithText(quickAdd5h).assertDoesNotExist()

        // Retained progress notice is displayed with 5 hours
        val retainedNotice = composeTestRule.activity.getString(R.string.library_retained_hours, 5)
        composeTestRule.onNodeWithText(retainedNotice).assertIsDisplayed()

        // Save and assert hours are retained in database/save callback
        val saveText = composeTestRule.activity.getString(R.string.library_add_to_library)
        composeTestRule.onNode(hasText(saveText) and hasClickAction()).performClick()
        assertEquals(LibraryStatus.WISHLIST, savedStatus)
        assertEquals(5, savedHours)
    }

    @Test
    fun favoriteToggle_stateChangesAndSaved() {
        var savedFavorite = false

        composeTestRule.setContent {
            GameTrackerTheme {
                Surface {
                    EditLibrarySheetContent(
                        initialEntry = null,
                        onDismiss = {},
                        onSave = { _, _, _, _, isFavorite ->
                            savedFavorite = isFavorite
                        },
                        onDeleteClick = null,
                    )
                }
            }
        }

        // Click Favorite toggle
        val favoriteText = composeTestRule.activity.getString(R.string.library_favorite)
        composeTestRule.onNodeWithText(favoriteText).performClick()

        // Save and verify favorite is true
        val saveText = composeTestRule.activity.getString(R.string.library_add_to_library)
        composeTestRule.onNode(hasText(saveText) and hasClickAction()).performClick()
        assertTrue(savedFavorite)
    }
}
