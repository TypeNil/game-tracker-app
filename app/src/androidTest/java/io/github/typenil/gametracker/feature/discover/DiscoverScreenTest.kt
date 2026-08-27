package io.github.typenil.gametracker.feature.discover

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.FEED_SKELETON_TEST_TAG
import io.github.typenil.gametracker.core.designsystem.component.GAME_CARD_LIBRARY_ACTION_TEST_TAG
import io.github.typenil.gametracker.core.model.LibrarySnapshot

import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(uiState: DiscoverUiState) {
        composeTestRule.setContent {
            GameTrackerTheme {
                DiscoverScreen(
                    uiState = uiState,
                    onGameClick = {},
                    onSearchClick = {},
                    onAboutClick = {},
                    onRefresh = {},
                    onRetry = {},
                    onUserMessageShown = {},
                    onLoadMoreTrending = {},
                )
            }
        }
    }

    @Test
    fun initialLoading_showsFeedSkeleton() {
        setContent(DiscoverUiState(isLoading = true))
        composeTestRule.onNodeWithTag(FEED_SKELETON_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun emptyRailLoading_showsFeedSkeleton() {
        setContent(
            DiscoverUiState(
                selectedTab = DiscoverTab.CHARTS,
                rails = listOf(
                    DiscoverRailState(
                        rail = DiscoverRail.POPULAR_NOW,
                        games = emptyList(),
                        isLoading = true,
                    ),
                ),
            ),
        )
        composeTestRule.onNodeWithTag(FEED_SKELETON_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.discover_charts_loading),
        ).assertIsDisplayed()
    }

    @Test
    fun chartsCard_libraryAction_doesNotCallOnGameClick() {
        var gameClicks = 0
        var libraryActions = 0
        val game = Game(id = 42L, name = "Elden Ring")
        composeTestRule.setContent {
            GameTrackerTheme {
                DiscoverScreen(
                    uiState = DiscoverUiState(
                        selectedTab = DiscoverTab.CHARTS,
                        rails = listOf(
                            DiscoverRailState(
                                rail = DiscoverRail.POPULAR_NOW,
                                games = listOf(game),
                            ),
                        ),
                        librarySnapshot = LibrarySnapshot.Ready(emptyMap()),
                    ),
                    onGameClick = { gameClicks++ },
                    onSearchClick = {},
                    onAboutClick = {},
                    onRefresh = {},
                    onRetry = {},
                    onUserMessageShown = {},
                    onLoadMoreTrending = {},
                    onLibraryAction = { libraryActions++ },
                )
            }
        }
        composeTestRule.onNodeWithTag(GAME_CARD_LIBRARY_ACTION_TEST_TAG).performClick()
        assertEquals(1, libraryActions)
        assertEquals(0, gameClicks)
    }

    @Test
    fun chartsCard_existingEntry_opensEditSheet_notWishlist() {
        var libraryActions = 0
        val game = Game(id = 42L, name = "Elden Ring")
        val entry = LibraryEntry(
            gameId = 42L,
            status = LibraryStatus.PLAYING,
            addedAtEpochSeconds = 1L,
            updatedAtEpochSeconds = 1L,
        )
        composeTestRule.setContent {
            GameTrackerTheme {
                DiscoverScreen(
                    uiState = DiscoverUiState(
                        selectedTab = DiscoverTab.CHARTS,
                        rails = listOf(
                            DiscoverRailState(
                                rail = DiscoverRail.POPULAR_NOW,
                                games = listOf(game),
                            ),
                        ),
                        librarySnapshot = LibrarySnapshot.Ready(mapOf(42L to entry)),
                        editingGameId = 42L,
                    ),
                    onGameClick = {},
                    onSearchClick = {},
                    onAboutClick = {},
                    onRefresh = {},
                    onRetry = {},
                    onUserMessageShown = {},
                    onLoadMoreTrending = {},
                    onLibraryAction = { libraryActions++ },
                )
            }
        }
        assertEquals(0, libraryActions)
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_edit_entry_title),
        ).assertIsDisplayed()
    }

    @Test
    fun chartsCard_loadingLibrary_hidesAction() {
        val game = Game(id = 42L, name = "Elden Ring")
        composeTestRule.setContent {
            GameTrackerTheme {
                DiscoverScreen(
                    uiState = DiscoverUiState(
                        selectedTab = DiscoverTab.CHARTS,
                        rails = listOf(
                            DiscoverRailState(
                                rail = DiscoverRail.POPULAR_NOW,
                                games = listOf(game),
                            ),
                        ),
                        librarySnapshot = LibrarySnapshot.Loading,
                    ),
                    onGameClick = {},
                    onSearchClick = {},
                    onAboutClick = {},
                    onRefresh = {},
                    onRetry = {},
                    onUserMessageShown = {},
                    onLoadMoreTrending = {},
                    onLibraryAction = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(GAME_CARD_LIBRARY_ACTION_TEST_TAG).assertDoesNotExist()
    }
}
