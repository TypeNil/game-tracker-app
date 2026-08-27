package io.github.typenil.gametracker.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_HOURS_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.QUICK_HOURS_DIALOG_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.QUICK_HOURS_INPUT_TEST_TAG
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import io.github.typenil.gametracker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val hades = LibraryGame(
        game = Game(id = 1L, name = "Hades", rating = 93.0),
        entry = LibraryEntry(
            gameId = 1L,
            status = LibraryStatus.PLAYING,
            isFavorite = true,
            addedAtEpochSeconds = 1700000000L,
            updatedAtEpochSeconds = 1700000000L,
            hoursPlayed = 20,
        )
    )

    private val eldenRing = LibraryGame(
        game = Game(id = 2L, name = "Elden Ring", rating = 96.0),
        entry = LibraryEntry(
            gameId = 2L,
            status = LibraryStatus.WISHLIST,
            isFavorite = false,
            addedAtEpochSeconds = 1700000000L,
            updatedAtEpochSeconds = 1700000000L
        )
    )

    private val sampleGames = listOf(hades, eldenRing)

    @Test
    fun libraryScreen_rendersTabsAndSelectedTabGames() {
        var currentTab by mutableStateOf(LibraryTab.ALL)

        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = sampleGames,
                        filteredGames = sampleGames,
                        selectedTab = currentTab,
                        tabCounts = mapOf(
                            LibraryTab.ALL to 2,
                            LibraryTab.PLAYING to 1,
                            LibraryTab.WISHLIST to 1,
                            LibraryTab.COMPLETED to 0,
                            LibraryTab.DROPPED to 0,
                            LibraryTab.NOT_INTERESTED to 0
                        ),
                        isLoading = false
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onTabSelected = { currentTab = it },
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {}
                )
            }
        }

        // Verify initial ALL tab displays games
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // Click Wishlist tab
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("library_tab_row")) and hasText("Wishlist")).performClick()
        composeTestRule.waitForIdle()
        assertEquals(LibraryTab.WISHLIST, currentTab)
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()
    }

    @Test
    fun libraryScreen_swipingPager_switchesToNextTab() {
        var currentTab by mutableStateOf(LibraryTab.ALL)

        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = sampleGames,
                        filteredGames = sampleGames,
                        selectedTab = currentTab,
                        tabCounts = mapOf(
                            LibraryTab.ALL to 2,
                            LibraryTab.PLAYING to 1,
                            LibraryTab.WISHLIST to 1,
                            LibraryTab.COMPLETED to 0,
                            LibraryTab.DROPPED to 0,
                            LibraryTab.NOT_INTERESTED to 0
                        ),
                        isLoading = false
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onTabSelected = { currentTab = it },
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {}
                )
            }
        }

        // Swipe left on the pager to move from ALL to PLAYING
        composeTestRule.onNodeWithTag("library_pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        assertEquals(LibraryTab.PLAYING, currentTab)
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
    }

    @Test
    fun libraryScreen_emptyTab_showsDiscoverCta() {
        var navigated = false
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = listOf(hades),
                        filteredGames = emptyList(),
                        selectedTab = LibraryTab.WISHLIST,
                        tabCounts = mapOf(
                            LibraryTab.ALL to 1,
                            LibraryTab.PLAYING to 1,
                            LibraryTab.WISHLIST to 0,
                            LibraryTab.COMPLETED to 0,
                            LibraryTab.DROPPED to 0,
                            LibraryTab.NOT_INTERESTED to 0,
                        ),
                        isLoading = false,
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = { navigated = true },
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_tab_empty_title)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_empty_cta)
        ).performClick()
        assertEquals(true, navigated)
    }

    @Test
    fun libraryScreen_hoursDialogState_restoresAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = listOf(hades),
                        filteredGames = listOf(hades),
                        selectedTab = LibraryTab.ALL,
                        tabCounts = mapOf(LibraryTab.ALL to 1),
                        isLoading = false,
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(QUICK_HOURS_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(QUICK_HOURS_INPUT_TEST_TAG).performTextClearance()
        composeTestRule.onNodeWithTag(QUICK_HOURS_INPUT_TEST_TAG).performTextInput("77")

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag(QUICK_HOURS_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("77").assertIsDisplayed()
    }

    @Test
    fun libraryScreen_failedHoursUpdate_keepsDialogAndDraft() {
        var currentUiState by mutableStateOf(
            LibraryUiState(
                allGames = listOf(hades),
                filteredGames = listOf(hades),
                selectedTab = LibraryTab.ALL,
                tabCounts = mapOf(LibraryTab.ALL to 1),
                isLoading = false,
            ),
        )
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = currentUiState,
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(QUICK_HOURS_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(QUICK_HOURS_INPUT_TEST_TAG).performTextClearance()
        composeTestRule.onNodeWithTag(QUICK_HOURS_INPUT_TEST_TAG).performTextInput("99")

        currentUiState = currentUiState.copy(
            hoursSaveState = HoursSaveState.Failed(hades.game.id),
            userMessageRes = R.string.error_library_update_failed,
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(QUICK_HOURS_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("99").assertIsDisplayed()
    }

    @Test
    fun libraryScreen_successfulHoursUpdate_closesDialog() {
        var currentUiState by mutableStateOf(
            LibraryUiState(
                allGames = listOf(hades),
                filteredGames = listOf(hades),
                selectedTab = LibraryTab.ALL,
                tabCounts = mapOf(LibraryTab.ALL to 1),
                isLoading = false,
            ),
        )
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = currentUiState,
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_HOURS_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(QUICK_HOURS_DIALOG_TEST_TAG).assertIsDisplayed()

        currentUiState = currentUiState.copy(
            hoursSaveState = HoursSaveState.Saved(hades.game.id),
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(QUICK_HOURS_DIALOG_TEST_TAG).assertDoesNotExist()
    }

}
