package io.github.typenil.gametracker.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_ADDED_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_FAVORITE_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LibraryGameCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryGameCardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun card_showsTitleDeveloperHoursDateFavoriteTags() {
        val game = libraryGame(
            name = "Hades",
            status = LibraryStatus.PLAYING,
            hoursPlayed = 12,
            isFavorite = true,
            rating = 93.0,
            genres = listOf("Action"),
            developerName = "Supergiant Games",
        )
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(libraryGame = game, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supergiant Games").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_format, 12),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_played),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("93.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_added),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_favorite_remove),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_status_playing),
        ).assertDoesNotExist()

        val hoursLeft = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_played),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot().left
        val addedBounds = composeTestRule.onNodeWithTag(
            LIBRARY_CARD_ADDED_TEST_TAG,
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val favoriteBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_FAVORITE_TEST_TAG)
            .getUnclippedBoundsInRoot()
        assertTrue(addedBounds.left > hoursLeft)
        assertTrue(favoriteBounds.right - addedBounds.right < 24.dp)
    }

    @Test
    fun card_keepsAddedDateOnTheRight_whenHoursMissing() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Celeste",
                        status = LibraryStatus.WISHLIST,
                        hoursPlayed = 0,
                        isFavorite = false,
                    ),
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Celeste").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_played),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_favorite_add),
        ).assertIsDisplayed()
        val addedBounds = composeTestRule.onNodeWithTag(
            LIBRARY_CARD_ADDED_TEST_TAG,
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val favoriteBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_FAVORITE_TEST_TAG)
            .getUnclippedBoundsInRoot()
        assertTrue(favoriteBounds.right - addedBounds.right < 24.dp)
    }

    @Test
    fun card_click_andFavorite_areIndependent() {
        var clickCount = 0
        var favoriteCount = 0
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(name = "Hades"),
                    onClick = { clickCount++ },
                    onFavoriteClick = { favoriteCount++ },
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_FAVORITE_TEST_TAG).performClick()
        assertEquals(0, clickCount)
        assertEquals(1, favoriteCount)
        composeTestRule.onNodeWithText("Hades").performClick()
        assertEquals(1, clickCount)
        assertEquals(1, favoriteCount)
    }

    private fun libraryGame(
        name: String,
        status: LibraryStatus = LibraryStatus.PLAYING,
        hoursPlayed: Int = 0,
        isFavorite: Boolean = false,
        rating: Double? = null,
        genres: List<String> = emptyList(),
        developerName: String? = null,
    ): LibraryGame = LibraryGame(
        game = Game(id = 1L, name = name, rating = rating, genres = genres),
        entry = LibraryEntry(
            gameId = 1L,
            status = status,
            isFavorite = isFavorite,
            addedAtEpochSeconds = 1_700_000_000L,
            updatedAtEpochSeconds = 1_700_000_000L,
            hoursPlayed = hoursPlayed,
        ),
        developerName = developerName,
    )
}
