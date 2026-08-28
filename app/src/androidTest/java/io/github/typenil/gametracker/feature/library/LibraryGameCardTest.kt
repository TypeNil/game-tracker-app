package io.github.typenil.gametracker.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_ADDED_TEXT_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_ADDED_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_BANNER_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_CLICK_TARGET_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_FAVORITE_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_HOURS_TEXT_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_HOURS_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_STATUS_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LibraryGameCard
import io.github.typenil.gametracker.feature.library.component.resolveLibraryBannerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            composeTestRule.activity.getString(R.string.library_status_playing),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 12),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_played),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_hours_played),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("93.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_added),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_added),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_favorite_remove),
        ).assertIsDisplayed()

        val statusBounds = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_status_playing),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val hoursBounds = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 12),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val addedBounds = composeTestRule.onNodeWithTag(
            LIBRARY_CARD_ADDED_TEST_TAG,
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val favoriteBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_FAVORITE_TEST_TAG)
            .getUnclippedBoundsInRoot()
        assertTrue(hoursBounds.left > statusBounds.left)
        assertTrue(addedBounds.left > hoursBounds.left)
        assertTrue(addedBounds.top >= statusBounds.top - 8.dp)
        assertTrue(addedBounds.bottom <= statusBounds.bottom + 8.dp)
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
            composeTestRule.activity.getString(R.string.library_status_wishlist),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 0),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_played),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
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
    fun card_hidesHours_forWishlistEvenWhenPlayed() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Celeste",
                        status = LibraryStatus.WISHLIST,
                        hoursPlayed = 12,
                    ),
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 12),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_hours_played),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_added),
        ).assertIsDisplayed()
    }

    @Test
    fun card_click_favorite_andStatus_areIndependent() {
        var clickCount = 0
        var favoriteCount = 0
        var selectedStatus: LibraryStatus? = null
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(name = "Hades"),
                    onClick = { clickCount++ },
                    onFavoriteClick = { favoriteCount++ },
                    onStatusSelected = { selectedStatus = it },
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_FAVORITE_TEST_TAG).performClick()
        assertEquals(0, clickCount)
        assertEquals(1, favoriteCount)
        composeTestRule.onNodeWithTag(LIBRARY_CARD_STATUS_TEST_TAG).performClick()
        assertEquals(0, clickCount)
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_status_completed),
        ).performClick()
        assertEquals(LibraryStatus.COMPLETED, selectedStatus)
        assertEquals(0, clickCount)
        composeTestRule.onNodeWithText("Hades").performClick()
        assertEquals(1, clickCount)
        assertEquals(1, favoriteCount)
    }

    @Test
    fun statusControl_hasMinimumTouchTarget() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(name = "Hades"),
                    onClick = {},
                )
            }
        }
        val bounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_STATUS_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertTrue("Width $width should be >= 48.dp", width >= 47.9.dp)
        assertTrue("Height $height should be >= 48.dp", height >= 47.9.dp)
    }

    @Test
    fun card_withMaxHours_dateAndHoursAreFullyDisplayedWithoutTruncation() {
        val expectedDate = io.github.typenil.gametracker.feature.library.component.formatLibraryAddedDate(1_700_000_000L)
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "The Witcher 3: Wild Hunt",
                        status = LibraryStatus.PLAYING,
                        hoursPlayed = 999_999,
                    ),
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 999_999),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.library_added),
        ).assertIsDisplayed()

        val hoursResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithTag(LIBRARY_CARD_HOURS_TEXT_TEST_TAG, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(hoursResults)
            }
        val res = hoursResults.single()
        val hoursText = composeTestRule.activity.getString(R.string.library_hours_short, 999_999)
        assertFalse(res.isLineEllipsized(0))
        assertEquals(hoursText.length, res.getLineEnd(0, visibleEnd = true))

        val dateResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithTag(LIBRARY_CARD_ADDED_TEXT_TEST_TAG, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(dateResults)
            }
        val dateRes = dateResults.single()
        assertFalse(dateRes.isLineEllipsized(0))
        assertEquals(expectedDate.length, dateRes.getLineEnd(0, visibleEnd = true))

        val hoursBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val addedBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_ADDED_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Gap between hours and date should be >= 12dp, was ${addedBounds.left - hoursBounds.right}",
            addedBounds.left - hoursBounds.right >= 12.dp,
        )
    }

    @Test
    fun card_withZeroHours_playingStatus_showsZeroHoursAndAllowsEditing() {
        var hoursClicks = 0
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.PLAYING,
                        hoursPlayed = 0,
                    ),
                    onClick = {},
                    onHoursClick = { hoursClicks++ },
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 0),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).performClick()
        assertEquals(1, hoursClicks)
    }

    @Test
    fun card_withZeroHours_completedStatus_hidesHours() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.COMPLETED,
                        hoursPlayed = 0,
                    ),
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 0),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun card_clickHours_triggersOnHoursClick_andDoesNotTriggerCardClick() {
        var cardClicks = 0
        var hoursClicks = 0
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.PLAYING,
                        hoursPlayed = 60,
                    ),
                    onClick = { cardClicks++ },
                    onHoursClick = { hoursClicks++ },
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).performClick()
        assertEquals(0, cardClicks)
        assertEquals(1, hoursClicks)
        composeTestRule.onNodeWithText("Hades").performClick()
        assertEquals(1, cardClicks)
        assertEquals(1, hoursClicks)
    }

    @Test
    fun hoursControl_hasMinimumTouchTarget() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.PLAYING,
                        hoursPlayed = 60,
                    ),
                    onClick = {},
                )
            }
        }
        val bounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertTrue("Width $width should be >= 48.dp", width >= 47.9.dp)
        assertTrue("Height $height should be >= 48.dp", height >= 47.9.dp)
    }

    @Test
    fun card_changingStatusToWishlist_hidesHoursAndDividers() {
        var currentStatus by mutableStateOf(LibraryStatus.PLAYING)
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = currentStatus,
                        hoursPlayed = 20,
                    ),
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertIsDisplayed()

        currentStatus = LibraryStatus.WISHLIST
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun card_updatingHoursToZero_completedStatus_animatesOutHoursAndDividers() {
        var currentHours by mutableStateOf(20)
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.COMPLETED,
                        hoursPlayed = currentHours,
                    ),
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertIsDisplayed()

        currentHours = 0
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun card_updatingHoursFromZero_completedStatus_animatesInHoursAndDividers() {
        var currentHours by mutableStateOf(0)
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.COMPLETED,
                        hoursPlayed = currentHours,
                    ),
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertDoesNotExist()

        currentHours = 15
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_hours_short, 15),
        ).assertIsDisplayed()
    }

    @Test
    fun card_hours_isCenteredBetweenDividers() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(
                        name = "Hades",
                        status = LibraryStatus.PLAYING,
                        hoursPlayed = 0,
                    ),
                    onClick = {},
                )
            }
        }
        val statusBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_STATUS_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val hoursBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val dateBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_ADDED_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        val leftGap = hoursBounds.left - statusBounds.right
        val rightGap = dateBounds.left - hoursBounds.right
        assertTrue("Left gap $leftGap should be positive", leftGap > 0.dp)
        assertTrue("Right gap $rightGap should be positive", rightGap > 0.dp)
        val difference = if (leftGap > rightGap) leftGap - rightGap else rightGap - leftGap
        assertTrue("Hours should be centered between status and date, diff was $difference", difference < 16.dp)
    }
    @Test
    fun card_bannerContainer_is16By9() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(name = "Hades"),
                    onClick = {},
                )
            }
        }
        val bounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_BANNER_TEST_TAG)
            .getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val ratio = width.value / height.value
        val expected = 16f / 9f
        assertEquals(expected, ratio, 0.05f)
    }

    @Test
    fun card_usesCoverWhenBannerIsBlank() {
        val game = LibraryGame(
            game = Game(id = 1L, name = "Hades", coverUrl = "https://example.com/cover.jpg"),
            entry = LibraryEntry(
                gameId = 1L,
                status = LibraryStatus.PLAYING,
                addedAtEpochSeconds = 1_700_000_000L,
                updatedAtEpochSeconds = 1_700_000_000L,
            ),
            bannerUrl = "",
        )
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = game,
                    onClick = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_CARD_BANNER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun resolveLibraryBannerUrl_prefersNonBlankBannerOverCover() {
        assertEquals(
            "https://example.com/banner.jpg",
            resolveLibraryBannerUrl("https://example.com/banner.jpg", "https://example.com/cover.jpg"),
        )
    }

    @Test
    fun resolveLibraryBannerUrl_fallsBackToCoverWhenBannerIsBlankOrNull() {
        assertEquals(
            "https://example.com/cover.jpg",
            resolveLibraryBannerUrl("", "https://example.com/cover.jpg"),
        )
        assertEquals(
            "https://example.com/cover.jpg",
            resolveLibraryBannerUrl("   ", "https://example.com/cover.jpg"),
        )
        assertEquals(
            "https://example.com/cover.jpg",
            resolveLibraryBannerUrl(null, "https://example.com/cover.jpg"),
        )
    }

    @Test
    fun resolveLibraryBannerUrl_returnsNullWhenBothAreBlankOrNull() {
        assertNull(resolveLibraryBannerUrl(null, null))
        assertNull(resolveLibraryBannerUrl("", "   "))
    }

    @Test
    fun card_compactWidthAndLargeFont_keepsInteractiveContentInBounds() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.0f, fontScale = 1.5f),
            ) {
                GameTrackerTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        LibraryGameCard(
                            libraryGame = libraryGame(
                                name = "The Legend of Zelda: Tears of the Kingdom",
                                status = LibraryStatus.PLAYING,
                                hoursPlayed = 120,
                                isFavorite = true,
                                developerName = "Nintendo EPD",
                                genres = listOf("Action", "Adventure"),
                            ),
                            onClick = {},
                        )
                    }
                }
            }
        }
        val favoriteBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_FAVORITE_TEST_TAG).getUnclippedBoundsInRoot()
        val statusBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_STATUS_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hoursBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bannerBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_BANNER_TEST_TAG).getUnclippedBoundsInRoot()

        val titleBounds = composeTestRule.onNodeWithText("The Legend of Zelda: Tears of the Kingdom", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val bannerHeight = bannerBounds.bottom - bannerBounds.top
        assertTrue(bannerHeight > 0.dp)
        assertTrue(favoriteBounds.top >= bannerBounds.top)
        assertTrue(favoriteBounds.bottom <= bannerBounds.bottom)
        assertTrue(
            "Title overlaps favorite: title=$titleBounds favorite=$favoriteBounds",
            titleBounds.top >= favoriteBounds.bottom,
        )
        assertTrue(statusBounds.top >= bannerBounds.bottom)
        assertTrue(hoursBounds.top >= bannerBounds.bottom)
    }

    @Test
    fun card_shortContent_clickTargetFillsBanner() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryGameCard(
                    libraryGame = libraryGame(name = "Hades"),
                    onClick = {},
                )
            }
        }
        val bannerBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_BANNER_TEST_TAG).getUnclippedBoundsInRoot()
        val clickTargetBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_CLICK_TARGET_TEST_TAG).getUnclippedBoundsInRoot()
        assertEquals(bannerBounds.left.value, clickTargetBounds.left.value, 0.1f)
        assertEquals(bannerBounds.right.value, clickTargetBounds.right.value, 0.1f)
        assertEquals(bannerBounds.top.value, clickTargetBounds.top.value, 0.1f)
        assertEquals(bannerBounds.bottom.value, clickTargetBounds.bottom.value, 0.1f)
    }

    @Test
    fun card_compactWidthAndMaxFont_keepsAllMetadataInBounds() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.0f, fontScale = 2.0f),
            ) {
                GameTrackerTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        LibraryGameCard(
                            libraryGame = libraryGame(
                                name = "Hades",
                                status = LibraryStatus.PLAYING,
                                hoursPlayed = 999_999,
                            ),
                            onClick = {},
                        )
                    }
                }
            }
        }
        val statusBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_STATUS_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hoursBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val addedBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_ADDED_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val statusWidth = statusBounds.right - statusBounds.left
        val hoursWidth = hoursBounds.right - hoursBounds.left
        val addedWidth = addedBounds.right - addedBounds.left
        assertTrue("Status width should be positive", statusWidth > 0.dp)
        assertTrue("Hours width should be positive", hoursWidth > 0.dp)
        assertTrue("Added width should be positive", addedWidth > 0.dp)
        assertTrue("Hours should be below status when stacked", hoursBounds.top >= statusBounds.bottom)
        assertTrue("Added date should be below hours when stacked", addedBounds.top >= hoursBounds.bottom)
        assertTrue("Status left ${statusBounds.left} should be >= 0", statusBounds.left >= 0.dp)
        assertTrue("Hours left ${hoursBounds.left} should be >= 0", hoursBounds.left >= 0.dp)
        assertTrue("Added left ${addedBounds.left} should be >= 0", addedBounds.left >= 0.dp)
        assertTrue("Status right ${statusBounds.right} should be <= 320dp", statusBounds.right <= 320.dp)
        assertTrue("Hours right ${hoursBounds.right} should be <= 320dp", hoursBounds.right <= 320.dp)
        assertTrue("Added right ${addedBounds.right} should be <= 320dp", addedBounds.right <= 320.dp)
    }
    @Test
    fun card_compactWidthAndDefaultFont_keepsAllMetadataUntruncated() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.0f, fontScale = 1.0f),
            ) {
                GameTrackerTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        LibraryGameCard(
                            libraryGame = libraryGame(
                                name = "Hades",
                                status = LibraryStatus.PLAYING,
                                hoursPlayed = 999_999,
                            ),
                            onClick = {},
                        )
                    }
                }
            }
        }
        val statusBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_STATUS_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hoursBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val addedBounds = composeTestRule.onNodeWithTag(LIBRARY_CARD_ADDED_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val statusWidth = statusBounds.right - statusBounds.left
        val hoursWidth = hoursBounds.right - hoursBounds.left
        val addedWidth = addedBounds.right - addedBounds.left
        assertTrue("Status width should be positive", statusWidth > 0.dp)
        assertTrue("Hours width should be positive", hoursWidth > 0.dp)
        assertTrue("Added width should be positive", addedWidth > 0.dp)
        assertTrue("Hours should be to the right of status", hoursBounds.left > statusBounds.right)
        assertTrue("Added date should be to the right of hours", addedBounds.left > hoursBounds.right)
        assertTrue("Status right ${statusBounds.right} should be <= 320dp", statusBounds.right <= 320.dp)
        assertTrue("Hours right ${hoursBounds.right} should be <= 320dp", hoursBounds.right <= 320.dp)
        assertTrue("Added right ${addedBounds.right} should be <= 320dp", addedBounds.right <= 320.dp)

        val expectedStatus = composeTestRule.activity.getString(R.string.library_status_playing)
        assertNotEllipsized(
            composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEXT_TEST_TAG, useUnmergedTree = true),
        )
        assertNotEllipsized(
            composeTestRule.onNodeWithText(expectedStatus, useUnmergedTree = true),
        )
        assertNotEllipsized(
            composeTestRule.onNodeWithTag(LIBRARY_CARD_ADDED_TEXT_TEST_TAG, useUnmergedTree = true),
        )
    }

    private fun assertNotEllipsized(
        node: androidx.compose.ui.test.SemanticsNodeInteraction,
    ) {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(results)
        }
        val result = results.single()
        assertFalse(result.isLineEllipsized(0))
        assertEquals(
            result.layoutInput.text.length,
            result.getLineEnd(0, visibleEnd = true),
        )
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
