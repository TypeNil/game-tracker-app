package io.github.typenil.gametracker.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun searchRoute_typingQuery_displaysLoading_thenDisplaysResultGames() {
        val fakeRepository = FakeDeferredGameRepository()
        val savedStateHandle = SavedStateHandle()
        val viewModel = SearchViewModel(
            gameRepository = fakeRepository,
            savedStateHandle = savedStateHandle
        )

        composeTestRule.setContent {
            SearchRoute(
                onGameClick = {},
                onBackClick = {},
                viewModel = viewModel
            )
        }

        val context = composeTestRule.activity
        val searchHint = context.getString(R.string.search_hint)
        val loadingText = context.getString(R.string.search_loading_games)

        composeTestRule.onNodeWithText(context.getString(R.string.search_idle_title)).assertIsDisplayed()

        composeTestRule.onNodeWithText(searchHint).performTextInput("witcher")

        // combine() maps Idle + non-blank query to Loading before debounce / searchGames().
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            fakeRepository.capturedQuery != null
        }
        composeTestRule.onNodeWithText(loadingText).assertIsDisplayed()

        assertEquals("witcher", fakeRepository.capturedQuery)
        assertEquals(30, fakeRepository.capturedLimit)
        assertEquals(0, fakeRepository.capturedOffset)

        fakeRepository.searchFlow.value = sampleGames
        fakeRepository.deferredResult.complete(AppResult.Success(Unit))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()
    }

    private class FakeDeferredGameRepository : GameRepository {
        @Volatile
        var capturedQuery: String? = null
        @Volatile
        var capturedLimit: Int? = null
        @Volatile
        var capturedOffset: Int? = null
        val deferredResult = CompletableDeferred<AppResult<Unit>>()
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())

        override fun getTopRatedGamesFlow(): Flow<List<Game>> = flowOf(emptyList())

        override fun getPagedTopRatedGames(pageSize: Int): Flow<PagingData<Game>> {
            return flowOf(PagingData.empty())
        }

        override suspend fun refreshTopRatedGames(limit: Int, offset: Int): AppResult<Unit> {
            return AppResult.Success(Unit)
        }

        override fun getSearchResultsFlow(query: String): Flow<List<Game>> = searchFlow

        override fun getPagedSearchResults(query: String, pageSize: Int): Flow<PagingData<Game>> {
            return flowOf(PagingData.empty())
        }

        override suspend fun searchGames(query: String, limit: Int, offset: Int): AppResult<Unit> {
            capturedQuery = query
            capturedLimit = limit
            capturedOffset = offset
            return deferredResult.await()
        }

        override fun getGameDetailsFlow(id: Long): Flow<GameDetails?> = flowOf(null)

        override suspend fun refreshGameDetails(id: Long, force: Boolean): AppResult<Unit> {
            return AppResult.Error(AppError.UnknownError(NoSuchElementException()))
        }

        override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int = 0
    }
}

