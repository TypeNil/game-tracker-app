package io.github.typenil.gametracker.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleGames = listOf(
        Game(
            id = 101L,
            name = "The Witcher 3: Wild Hunt",
            rating = 95.0,
            releaseDateEpochSeconds = 1431993600L,
            genres = listOf("RPG", "Adventure")
        ),
        Game(
            id = 102L,
            name = "Cyberpunk 2077",
            rating = 86.0,
            releaseDateEpochSeconds = 1607558400L,
            genres = listOf("RPG", "Sci-Fi")
        )
    )

    @Test
    fun idleState_rendersSearchGuidance() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "", result = SearchResultUiState.Idle),
                onQueryChange = {},
                onClearQuery = {},
                onRetry = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_idle_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_idle_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_hint)).assertIsDisplayed()
    }

    @Test
    fun loadingState_rendersProgressIndicator() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", result = SearchResultUiState.Loading),
                onQueryChange = {},
                onClearQuery = {},
                onRetry = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_loading_games)).assertIsDisplayed()
    }

    @Test
    fun contentState_rendersGameCards_andForwardsClicks() {
        var clickedGameId: Long? = null

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "witcher",
                    result = SearchResultUiState.Content(sampleGames)
                ),
                onQueryChange = {},
                onClearQuery = {},
                onRetry = {},
                onGameClick = { clickedGameId = it },
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()

        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").performClick()
        assertEquals(101L, clickedGameId)
    }

    @Test
    fun emptyState_rendersEmptyMessageWithQuery() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "nonexistent",
                    result = SearchResultUiState.Empty("nonexistent")
                ),
                onQueryChange = {},
                onClearQuery = {},
                onRetry = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        val expectedEmptyMessage = context.getString(R.string.search_empty_results_format, "nonexistent")
        composeTestRule.onNodeWithText(expectedEmptyMessage).assertIsDisplayed()
    }

    @Test
    fun errorState_rendersErrorAndTriggersRetry() {
        val context = composeTestRule.activity
        var retryClicked = false

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "error",
                    result = SearchResultUiState.Error(AppError.NetworkError)
                ),
                onQueryChange = {},
                onClearQuery = {},
                onRetry = { retryClicked = true },
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.retry_button)).assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.retry_button)).performClick()
        assertTrue(retryClicked)
    }

    @Test
    fun textInput_triggersOnQueryChange() {
        val context = composeTestRule.activity
        var enteredText = ""

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "", result = SearchResultUiState.Idle),
                onQueryChange = { enteredText = it },
                onClearQuery = {},
                onRetry = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_hint)).performTextInput("Zelda")
        assertEquals("Zelda", enteredText)
    }

    @Test
    fun clearAction_visibleWhenQueryNotEmpty_triggersOnClearQuery() {
        val context = composeTestRule.activity
        var clearClicked = false

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "Witcher", result = SearchResultUiState.Loading),
                onQueryChange = {},
                onClearQuery = { clearClicked = true },
                onRetry = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        val clearDescription = context.getString(R.string.search_clear_desc)
        composeTestRule.onNodeWithContentDescription(clearDescription).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(clearDescription).performClick()
        assertTrue(clearClicked)
    }

    @Test
    fun backAction_triggersOnBackClick_andHasAccessibleDescription() {
        val context = composeTestRule.activity
        var backClicked = false

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "", result = SearchResultUiState.Idle),
                onQueryChange = {},
                onClearQuery = {},
                onRetry = {},
                onGameClick = {},
                onBackClick = { backClicked = true }
            )
        }

        val backDescription = context.getString(R.string.back_action_desc)
        composeTestRule.onNodeWithContentDescription(backDescription).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(backDescription).performClick()
        assertTrue(backClicked)
    }
}
