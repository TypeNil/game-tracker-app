package io.github.typenil.gametracker.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.feature.details.component.EDIT_LIBRARY_NOTES_INPUT_TEST_TAG
import io.github.typenil.gametracker.feature.details.component.EDIT_LIBRARY_SHEET_HEADER_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_CARD_NOTES_TEST_TAG
import io.github.typenil.gametracker.core.designsystem.component.FEED_SKELETON_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_SKELETON_CARD_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_SKELETON_TEST_TAG
import io.github.typenil.gametracker.feature.library.insights.LIBRARY_INSIGHTS_ACTION_TEST_TAG
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameSearchQuery
import io.github.typenil.gametracker.core.model.LibraryEntryDraft
import io.github.typenil.gametracker.core.model.PageContinuation
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationCandidatePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val hadesWithNotes = LibraryGame(
        game = Game(id = 1L, name = "Hades", rating = 93.0),
        entry = LibraryEntry(
            gameId = 1L,
            status = LibraryStatus.PLAYING,
            isFavorite = true,
            addedAtEpochSeconds = 1700000000L,
            updatedAtEpochSeconds = 1700000000L,
            hoursPlayed = 20,
            userNotes = "Initial note",
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
    fun insightsAction_click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onInsightsClick = { clicked = true },
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(LIBRARY_INSIGHTS_ACTION_TEST_TAG).performClick()
        assertTrue(clicked)
    }

    @Test
    fun loading_showsLibraryCardSkeletonNotFeedSkeleton() {
        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(isLoading = true),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onInsightsClick = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_SKELETON_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(LIBRARY_SKELETON_CARD_TEST_TAG)
            .onFirst()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(FEED_SKELETON_TEST_TAG).assertDoesNotExist()
    }

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
                    onInsightsClick = {},
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
        val wishlistTitle = composeTestRule.activity.getString(LibraryTab.WISHLIST.titleRes)
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("library_tab_row")) and hasText(wishlistTitle)).performClick()
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
                    onInsightsClick = {},
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
                    onInsightsClick = {},
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
                    onInsightsClick = {},
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
                    onInsightsClick = {},
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
                    onInsightsClick = {},
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

    @Test
    fun libraryScreen_failedLibrarySave_keepsSheetAndNotesDraft() {
        var currentUiState by mutableStateOf(
            LibraryUiState(
                allGames = listOf(hadesWithNotes),
                filteredGames = listOf(hadesWithNotes),
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
                    onInsightsClick = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_NOTES_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()

        val uniqueDraft = "Unique draft note that must survive failure"
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).performTextClearance()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).performTextInput(uniqueDraft)

        currentUiState = currentUiState.copy(
            libraryMutationState = LibraryMutationState.Failed(hadesWithNotes.game.id),
            userMessageRes = R.string.error_library_update_failed,
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(uniqueDraft).assertIsDisplayed()
    }

    @Test
    fun libraryScreen_successfulLibrarySave_closesSheet() {
        var currentUiState by mutableStateOf(
            LibraryUiState(
                allGames = listOf(hadesWithNotes),
                filteredGames = listOf(hadesWithNotes),
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
                    onInsightsClick = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_NOTES_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()

        currentUiState = currentUiState.copy(
            libraryMutationState = LibraryMutationState.Saved(hadesWithNotes.game.id),
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun libraryScreen_editingSheetState_restoresAcrossSavedStateRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = listOf(hadesWithNotes),
                        filteredGames = listOf(hadesWithNotes),
                        selectedTab = LibraryTab.ALL,
                        tabCounts = mapOf(LibraryTab.ALL to 1),
                        isLoading = false,
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onInsightsClick = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LIBRARY_CARD_NOTES_TEST_TAG)
            .performClick()
        composeTestRule
            .onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG)
            .assertTextContains("Initial note")

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // Verify editing sheet and its input survive process recreation via editingGameId rememberSaveable
        composeTestRule
            .onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG)
            .assertTextContains("Initial note")
    }

    @Test
    fun libraryScreen_savingState_disablesActionsAndPreventsDismissal() {
        var currentUiState by mutableStateOf(
            LibraryUiState(
                allGames = listOf(hadesWithNotes),
                filteredGames = listOf(hadesWithNotes),
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
                    onInsightsClick = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_NOTES_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()
        val initialNote = "Note before in-flight save"
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).performTextClearance()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).performTextInput(initialNote)

        currentUiState = currentUiState.copy(
            libraryMutationState = LibraryMutationState.Saving(hadesWithNotes.game.id),
        )
        composeTestRule.waitForIdle()

        // 1. Assert Save and Close buttons are disabled
        val saveText = composeTestRule.activity.getString(R.string.library_save)
        composeTestRule.onNode(hasText(saveText) and hasClickAction()).assertIsNotEnabled()

        val closeDesc = composeTestRule.activity.getString(R.string.library_close)
        composeTestRule.onNodeWithContentDescription(closeDesc).assertIsNotEnabled()

        // 2. Assert Notes input is disabled
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).assertIsNotEnabled()

        // 3. Swipe down is prevented by confirmValueChange
        composeTestRule
            .onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG)
            .performTouchInput {
                val dragDistance = 400.dp.toPx()
                swipe(
                    start = center,
                    end = center.copy(y = center.y + dragDistance),
                    durationMillis = 300,
                )
            }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(initialNote).assertIsDisplayed()

        // 4. On failure, sheet and draft remain intact
        currentUiState = currentUiState.copy(
            libraryMutationState = LibraryMutationState.Failed(hadesWithNotes.game.id),
            userMessageRes = R.string.error_library_update_failed,
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(initialNote).assertIsDisplayed()
    }

    @Test
    fun libraryScreen_closeWhileSaving_doesNotDismiss_thenFailureKeepsDraft() {
        var currentUiState by mutableStateOf(
            LibraryUiState(
                allGames = listOf(hadesWithNotes),
                filteredGames = listOf(hadesWithNotes),
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
                    onInsightsClick = {},
                    onTabSelected = {},
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LIBRARY_CARD_NOTES_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()

        val draftNote = "Draft note that must survive failed save after attempted close"
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).performTextClearance()
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_NOTES_INPUT_TEST_TAG).performTextInput(draftNote)

        currentUiState = currentUiState.copy(
            libraryMutationState = LibraryMutationState.Saving(hadesWithNotes.game.id),
        )
        composeTestRule.waitForIdle()

        // Click close button while saving
        val closeDesc = composeTestRule.activity.getString(R.string.library_close)
        composeTestRule.onNodeWithContentDescription(closeDesc).performClick()
        composeTestRule.waitForIdle()

        // Sheet and draft note must still be displayed
        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(draftNote).assertIsDisplayed()

        // After failure, sheet and draft remain intact
        currentUiState = currentUiState.copy(
            libraryMutationState = LibraryMutationState.Failed(hadesWithNotes.game.id),
            userMessageRes = R.string.error_library_update_failed,
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EDIT_LIBRARY_SHEET_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(draftNote).assertIsDisplayed()
    }

    @Test
    fun libraryScreen_selectedTabRestoration_synchronizesWithPagerAcrossStateRestore() {
        val restorationTester = StateRestorationTester(composeTestRule)
        var lastSelectedTab: LibraryTab? = null

        restorationTester.setContent {
            var currentTab by rememberSaveable { mutableStateOf(LibraryTab.ALL) }
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = sampleGames,
                        filteredGames = if (currentTab == LibraryTab.WISHLIST) listOf(eldenRing) else sampleGames,
                        selectedTab = currentTab,
                        tabCounts = mapOf(
                            LibraryTab.ALL to 2,
                            LibraryTab.PLAYING to 1,
                            LibraryTab.WISHLIST to 1,
                            LibraryTab.COMPLETED to 0,
                            LibraryTab.DROPPED to 0,
                            LibraryTab.NOT_INTERESTED to 0,
                        ),
                        isLoading = false,
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onInsightsClick = {},
                    onTabSelected = {
                        currentTab = it
                        lastSelectedTab = it
                    },
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        // 1. Initial page is ALL
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // 2. Select Wishlist tab via tab click
        val wishlistTitle = composeTestRule.activity.getString(LibraryTab.WISHLIST.titleRes)
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("library_tab_row")) and hasText(wishlistTitle)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(LibraryTab.WISHLIST, lastSelectedTab)
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // 3. Emulate process death / state restoration
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // 4. Assert restored state is still on WISHLIST and shows Elden Ring
        assertEquals(LibraryTab.WISHLIST, lastSelectedTab)
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // 5. Subsequent swipe on restored pager continues to update tab correctly
        composeTestRule.onNodeWithTag("library_pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertEquals(LibraryTab.COMPLETED, lastSelectedTab)
    }

    @Test
    fun libraryScreen_initialRestoredTab_positionsPagerWithoutExtraneousScroll() {
        var reportedTab: LibraryTab? = null

        composeTestRule.setContent {
            GameTrackerTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allGames = sampleGames,
                        filteredGames = listOf(eldenRing),
                        selectedTab = LibraryTab.WISHLIST,
                        tabCounts = mapOf(
                            LibraryTab.ALL to 2,
                            LibraryTab.PLAYING to 1,
                            LibraryTab.WISHLIST to 1,
                        ),
                        isLoading = false,
                    ),
                    onGameClick = {},
                    onNavigateToDiscover = {},
                    onInsightsClick = {},
                    onTabSelected = { reportedTab = it },
                    onToggleFavoritesOnly = {},
                    onSearchQueryChanged = {},
                    onToggleSearchActive = {},
                    onSortOptionSelected = {},
                    onClearSearch = {},
                )
            }
        }

        // Pager should immediately display the restored tab (Wishlist) content
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()
        // No spurious onTabSelected should be emitted since pager and uiState agree
        assertNull(reportedTab)
    }

    @Test
    fun libraryViewModel_and_libraryScreen_integrationRestoration_singleSourceOfTruth() {
        val handle1 = SavedStateHandle()
        val fakeLibRepo = FakeLibraryRepositoryForTest(sampleGames)
        val fakeGameRepo = FakeGameRepositoryForTest()
        val vm1 = LibraryViewModel(
            libraryRepository = fakeLibRepo,
            gameRepository = fakeGameRepo,
            savedStateHandle = handle1,
        )

        var activeViewModel by mutableStateOf(vm1)

        composeTestRule.setContent {
            val vm = activeViewModel
            val uiState by vm.uiState.collectAsStateWithLifecycle()
            androidx.compose.runtime.key(vm) {
                GameTrackerTheme {
                    LibraryScreen(
                        uiState = uiState,
                        onGameClick = {},
                        onNavigateToDiscover = {},
                        onInsightsClick = {},
                        onTabSelected = vm::onTabSelected,
                        onToggleFavoritesOnly = vm::onToggleFavoritesOnly,
                        onSearchQueryChanged = vm::onSearchQueryChanged,
                        onToggleSearchActive = vm::onToggleSearchActive,
                        onSortOptionSelected = vm::onSortOptionSelected,
                        onClearSearch = vm::onClearSearch,
                    )
                }
            }
        }

        // 1. Initial state is ALL tab
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // 2. Select Wishlist tab
        val wishlistTitle = composeTestRule.activity.getString(LibraryTab.WISHLIST.titleRes)
        composeTestRule.onNode(hasAnyAncestor(hasTestTag("library_tab_row")) and hasText(wishlistTitle)).performClick()
        composeTestRule.waitForIdle()

        // Verify ViewModel's SavedStateHandle recorded WISHLIST
        assertEquals(LibraryTab.WISHLIST, handle1.get<LibraryTab>(LibraryViewModel.KEY_SELECTED_TAB))
        assertEquals(LibraryTab.WISHLIST, vm1.uiState.value.selectedTab)
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // 3. Simulate process death: recreate SavedStateHandle and new ViewModel instance
        val restoredMap = handle1.keys().associateWith { handle1.get<Any>(it) }
        val restoredHandle = SavedStateHandle(restoredMap)
        val vm2 = LibraryViewModel(
            libraryRepository = fakeLibRepo,
            gameRepository = fakeGameRepo,
            savedStateHandle = restoredHandle,
        )

        // Verify recreated ViewModel starts with restored tab immediately before emission
        assertEquals(LibraryTab.WISHLIST, vm2.uiState.value.selectedTab)

        // 4. Switch active ViewModel to the recreated instance (simulating composition after recreation)
        activeViewModel = vm2
        composeTestRule.waitForIdle()

        // 5. Pager is restored to Wishlist tab without any race or reconciliation conflict
        assertEquals(LibraryTab.WISHLIST, restoredHandle.get<LibraryTab>(LibraryViewModel.KEY_SELECTED_TAB))
        assertEquals(LibraryTab.WISHLIST, vm2.uiState.value.selectedTab)
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()

        // 6. Swiping on the restored pager correctly updates the authoritative SavedStateHandle
        composeTestRule.onNodeWithTag("library_pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertEquals(LibraryTab.COMPLETED, restoredHandle.get<LibraryTab>(LibraryViewModel.KEY_SELECTED_TAB))
        assertEquals(LibraryTab.COMPLETED, vm2.uiState.value.selectedTab)
    }

    private class FakeLibraryRepositoryForTest(
        initialGames: List<LibraryGame> = emptyList(),
    ) : LibraryRepository {
        val libraryGamesFlow = MutableStateFlow<AppResult<List<LibraryGame>>>(AppResult.Success(initialGames))

        override fun getLibraryGamesFlow(): Flow<AppResult<List<LibraryGame>>> = libraryGamesFlow
        override fun getLibraryEntryFlow(gameId: Long): Flow<AppResult<LibraryEntry?>> =
            flowOf(AppResult.Success(null))
        override suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun addToWishlist(game: Game): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun upsertUserEdits(gameId: Long, draft: LibraryEntryDraft): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun toggleFavorite(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun updateHoursPlayed(gameId: Long, hoursPlayed: Int): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeGameRepositoryForTest : GameRepository {
        override fun getTopRatedGamesFlow(): Flow<List<Game>> = flowOf(emptyList())
        override fun getPagedTopRatedGames(pageSize: Int): Flow<PagingData<Game>> = flowOf(PagingData.empty())
        override suspend fun refreshTopRatedGames(limit: Int, offset: Int): AppResult<Unit> = AppResult.Success(Unit)
        override fun getTrendingGamesFlow(): Flow<List<Game>> = flowOf(emptyList())
        override suspend fun refreshTrendingGames(
            limit: Int,
            offset: Int,
            append: Boolean,
        ): AppResult<PageContinuation> = AppResult.Success(PageContinuation(nextOffset = null, endReached = true))
        override suspend fun refreshPopular(
            type: String,
            limit: Int,
            offset: Int,
            append: Boolean,
        ): AppResult<PageContinuation> = AppResult.Success(PageContinuation(nextOffset = null, endReached = true))
        override fun getPopularGamesFlow(type: String): Flow<List<Game>> = flowOf(emptyList())
        override suspend fun getRecommendationCandidatesPage(
            genres: List<String>,
            themes: List<String>,
            platforms: List<String>,
            exclude: Set<Long>,
            similarTo: List<Long>,
            limit: Int,
            offset: Int,
            sort: String,
        ): AppResult<RecommendationCandidatePage> =
            AppResult.Success(RecommendationCandidatePage(emptyList(), null, true))
        override suspend fun getRecommendationCandidates(
            genres: List<String>,
            themes: List<String>,
            platforms: List<String>,
            exclude: Set<Long>,
            similarTo: List<Long>,
            limit: Int,
        ): AppResult<List<RecommendationCandidate>> = AppResult.Success(emptyList())
        override fun getSearchResultsFlow(query: GameSearchQuery): Flow<List<Game>> = flowOf(emptyList())
        override fun getPagedSearchResults(query: GameSearchQuery, pageSize: Int): Flow<PagingData<Game>> =
            flowOf(PagingData.empty())
        override suspend fun recordSearchHistory(rawQuery: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun searchGames(query: GameSearchQuery, limit: Int, force: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override fun getRecentSearchQueriesFlow(limit: Int): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteSearchQuery(query: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun clearSearchHistory(): AppResult<Unit> = AppResult.Success(Unit)
        override fun getGameDetailsFlow(id: Long): Flow<GameDetails?> = flowOf(null)
        override fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean> = flowOf(false)
        override suspend fun refreshGameDetails(id: Long, force: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int = 0
    }
}

