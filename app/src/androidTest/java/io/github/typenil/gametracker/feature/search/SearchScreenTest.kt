package io.github.typenil.gametracker.feature.search

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.PagingState
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.FEED_SKELETON_TEST_TAG
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.designsystem.component.GAME_CARD_LIBRARY_ACTION_TEST_TAG
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppErrorException
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import java.util.concurrent.ConcurrentHashMap
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Outlives activity recreation, standing in for the ViewModelScope that caches the
    // production paging generation.
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        // Release the shared cachedIn generation so it cannot retain test objects for
        // the rest of the instrumented process.
        testScope.cancel()
    }

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

    /**
     * Deterministic paged flow: a static generation carrying explicit NotLoading source states,
     * so header/end-of-pagination branches do not depend on background paging timing.
     */
    private fun completedPaged(games: List<Game>, complete: Boolean = true): Flow<PagingData<Game>> =
        flowOf(
            PagingData.from(
                games,
                LoadStates(
                    refresh = LoadState.NotLoading(endOfPaginationReached = false),
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = complete),
                ),
            )
        )

    private fun pendingPaged(): Flow<PagingData<Game>> = flowOf(
        PagingData.empty(
            sourceLoadStates = LoadStates(
                refresh = LoadState.Loading,
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = false),
            ),
        )
    )

    /** A paging source that fails every load with a data-boundary-classified error. */
    private class FailingPagingSource(private val error: Throwable) : PagingSource<Int, Game>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Game> =
            LoadResult.Error(error)

        override fun getRefreshKey(state: PagingState<Int, Game>): Int? = null
    }

    private fun failingPaged(throwable: Throwable): Flow<PagingData<Game>> =
        Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20),
            pagingSourceFactory = { FailingPagingSource(throwable) },
        ).flow

    @Test
    fun idleState_rendersSearchHint() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "", searchActive = false),
                searchResults = completedPaged(emptyList()),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_hint)).assertIsDisplayed()
    }

    @Test
    fun loadingState_rendersSkeletonAndCopy() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", searchActive = true),
                searchResults = pendingPaged(),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_loading_games)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FEED_SKELETON_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun contentState_rendersGameCards_andForwardsClicks() {
        var clickedGameId: Long? = null

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", searchActive = true),
                searchResults = completedPaged(sampleGames),
                onQueryChange = {},
                onClearQuery = {},
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
    fun contentState_showsNeutralLoadedCountAtEndOfPagination() {
        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", searchActive = true),
                searchResults = completedPaged(sampleGames, complete = true),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        // Terminal wording never claims the server-side total: the end can be a short page
        // or the BFF offset ceiling, and the UI cannot tell them apart. Plurals are resolved
        // through getQuantityString — Resources.getString on a plurals id throws
        // NotFoundException even though the id is valid.
        val loadedCount = composeTestRule.activity.resources.getQuantityString(
            R.plurals.search_results_loaded_count,
            2,
            2,
        )
        composeTestRule.onNodeWithText(loadedCount).assertIsDisplayed()
    }

    @Test
    fun contentState_showsPartialCountWhileMorePagesMayLoad() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", searchActive = true),
                searchResults = completedPaged(sampleGames, complete = false),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_results_count_partial_format, 2))
            .assertIsDisplayed()
    }

    @Test
    fun contentState_libraryAction_doesNotForwardGameClick() {
        var gameClicks = 0
        var libraryActions = 0
        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "witcher",
                    searchActive = true,
                    librarySnapshot = LibrarySnapshot.Ready(emptyMap()),
                ),
                searchResults = completedPaged(sampleGames),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = { gameClicks++ },
                onBackClick = {},
                onLibraryAction = { libraryActions++ },
            )
        }
        composeTestRule.onAllNodesWithTag(GAME_CARD_LIBRARY_ACTION_TEST_TAG)[0].performClick()
        assertEquals(1, libraryActions)
        assertEquals(0, gameClicks)
    }

    @Test
    fun emptyState_rendersEmptyMessageWithQuery() {
        val context = composeTestRule.activity
        var cleared = false

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "nonexistent", searchActive = true),
                searchResults = completedPaged(emptyList()),
                onQueryChange = {},
                onClearQuery = { cleared = true },
                onGameClick = {},
                onBackClick = {}
            )
        }

        val expectedEmptyMessage = context.getString(R.string.search_empty_results_format, "nonexistent")
        composeTestRule.onNodeWithText(expectedEmptyMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_empty_action)).performClick()
        assertTrue(cleared)
    }

    @Test
    fun errorState_rendersClassifiedErrorAndRetryKeepsErrorVisible() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "error", searchActive = true),
                searchResults = failingPaged(AppErrorException(AppError.NetworkError, IOException("no network"))),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.error_network))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.retry_button)).assertIsDisplayed()

        // Retry re-runs the failing source; the screen must settle back into the error state.
        composeTestRule.onNodeWithText(context.getString(R.string.retry_button)).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.error_network))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
    }

    @Test
    fun errorState_unknownThrowableFallsBackToGenericError() {
        val context = composeTestRule.activity

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "error", searchActive = true),
                searchResults = failingPaged(IOException("raw transport error")),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.error_unknown))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(context.getString(R.string.error_unknown)).assertIsDisplayed()
    }

    @Test
    fun textInput_triggersOnQueryChange() {
        val context = composeTestRule.activity
        var enteredText = ""

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "", searchActive = false),
                searchResults = completedPaged(emptyList()),
                onQueryChange = { enteredText = it },
                onClearQuery = {},
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
                uiState = SearchUiState(query = "Witcher", searchActive = true),
                searchResults = pendingPaged(),
                onQueryChange = {},
                onClearQuery = { clearClicked = true },
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
                uiState = SearchUiState(query = "", searchActive = false),
                searchResults = completedPaged(emptyList()),
                onQueryChange = {},
                onClearQuery = {},
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
    fun filterBar_rendersActiveFilterChips_andForwardsRemovals() {
        var genreToggled = false
        val filters = SearchFilters(
            genres = setOf("Role-playing (RPG)"),
            platforms = setOf(io.github.typenil.gametracker.core.designsystem.component.PlatformFamily.PC),
            minRating = MinRatingFilter.R80,
            releaseYear = ReleaseYearFilter.THIS_YEAR,
            sort = SearchSortOption.RATING_DESC,
        )

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "",
                    filters = filters,
                    searchActive = false,
                ),
                searchResults = completedPaged(emptyList()),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {},
                onToggleGenre = { genreToggled = true },
            )
        }

        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.search_filters_count_format, 4)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_sort_rating_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_year_this_year)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("RPG").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.platform_pc)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_rating_80)).performScrollTo().assertIsDisplayed()
        // Click toggle/remove RPG filter
        composeTestRule.onNodeWithText("RPG").performScrollTo().performClick()
        assertTrue(genreToggled)
    }

    @Test
    fun idleState_rendersRecentSearchesAndPresets() {
        var selectedQuery = ""
        var toggledGenre: String? = null

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "",
                    recentQueries = listOf("Elden Ring", "Cyberpunk"),
                    searchActive = false,
                ),
                searchResults = completedPaged(emptyList()),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {},
                onSelectRecentQuery = { selectedQuery = it },
                onToggleGenre = { toggledGenre = it },
            )
        }

        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.search_recent_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Elden Ring").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cyberpunk").assertIsDisplayed()
        // Click recent query
        composeTestRule.onNodeWithText("Elden Ring").performClick()
        assertEquals("Elden Ring", selectedQuery)

        // Click quick preset
        composeTestRule.onNodeWithText("RPG").performClick()
        assertEquals("Role-playing (RPG)", toggledGenre)
    }

    @Test
    fun emptyStateWithConstraints_rendersFilterEmptyMessageAndResetAction() {
        val context = composeTestRule.activity
        var resetClicked = false

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(
                    query = "",
                    filters = SearchFilters(genres = setOf("Action")),
                    searchActive = true,
                ),
                searchResults = completedPaged(emptyList()),
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {},
                onResetFilters = { resetClicked = true },
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_empty_filters_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_empty_filters_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_empty_filters_action)).performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun searchRoute_typingQuery_displaysLoading_thenDisplaysResultGames() {
        val fakeRepository = FakePagedGameRepository()
        val savedStateHandle = SavedStateHandle()
        val viewModel = SearchViewModel(
            gameRepository = fakeRepository,
            savedStateHandle = savedStateHandle,
            libraryRepository = FakeLibraryRepository(),
            clock = java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T12:00:00Z"), java.time.ZoneOffset.UTC),
            networkMonitor = NetworkMonitor(composeTestRule.activity.applicationContext),
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

        composeTestRule.onNodeWithText(searchHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(searchHint).performTextInput("witcher")

        // The pending generation activates the paged container before any repository dispatch;
        // synchronization is the per-query gate registered after the real 300 ms debounce.
        composeTestRule.onNodeWithText(loadingText).assertIsDisplayed()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            fakeRepository.requested("witcher")
        }
        assertEquals(20, fakeRepository.capturedPageSize)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            fakeRepository.capturedHistoryQuery != null
        }
        assertEquals("witcher", fakeRepository.capturedHistoryQuery)

        // The skeleton stays visible while the repository generation has not emitted yet.
        composeTestRule.onNodeWithText(loadingText).assertIsDisplayed()

        fakeRepository.emit("witcher", PagingData.from(sampleGames))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()
    }

    @Test
    fun searchRoute_changingQuery_neverRendersPreviousGenerationItems() {
        val fakeRepository = FakePagedGameRepository()
        val viewModel = SearchViewModel(
            gameRepository = fakeRepository,
            savedStateHandle = SavedStateHandle(),
            libraryRepository = FakeLibraryRepository(),
            clock = java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T12:00:00Z"), java.time.ZoneOffset.UTC),
            networkMonitor = NetworkMonitor(composeTestRule.activity.applicationContext),
        )

        composeTestRule.setContent {
            SearchRoute(
                onGameClick = {},
                onBackClick = {},
                viewModel = viewModel
            )
        }

        val context = composeTestRule.activity
        val loadingText = context.getString(R.string.search_loading_games)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("alpha")
        composeTestRule.waitUntil(timeoutMillis = 5_000) { fakeRepository.requested("alpha") }
        fakeRepository.emit("alpha", PagingData.from(sampleGames))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertIsDisplayed()

        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("beta")
        // The committed beta command replaces the container with the pending generation
        // before any beta data exists: alpha's rendered items must disappear with it —
        // the Paging Compose presenter, not just the ViewModel, is under test here.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(loadingText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertDoesNotExist()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { fakeRepository.requested("beta") }
        val betaGames = listOf(
            Game(
                id = 201L,
                name = "Beta Game",
                rating = 70.0,
                releaseDateEpochSeconds = 1500000000L,
                genres = listOf("Action"),
                platforms = listOf("PC (Microsoft Windows)"),
            )
        )
        fakeRepository.emit("beta", PagingData.from(betaGames))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Beta Game").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Beta Game").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertDoesNotExist()
    }

    private class RecoveringPagingSource(
        private val firstPage: List<Game>,
        private val secondPage: List<Game>,
        private val failRefresh: Boolean,
        private val failAppend: Boolean,
    ) : PagingSource<Int, Game>() {
        // The production GamesRemoteMediator maps transport failures to a classified
        // AppErrorException before Paging reaches presentation; the scripted source must
        // emit the same boundary contract, not a raw IOException.
        private fun offlinePagingError(): AppErrorException =
            AppErrorException(AppError.NetworkError, IOException("device offline"))

        var refreshAttempts = 0
        var appendAttempts = 0

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Game> {
            val key = params.key ?: 0
            return if (key == 0) {
                refreshAttempts++
                if (failRefresh && refreshAttempts == 1) {
                    LoadResult.Error(offlinePagingError())
                } else {
                    LoadResult.Page(
                        data = firstPage,
                        prevKey = null,
                        nextKey = if (secondPage.isEmpty()) null else firstPage.size,
                    )
                }
            } else {
                appendAttempts++
                if (failAppend && appendAttempts == 1) {
                    LoadResult.Error(offlinePagingError())
                } else {
                    LoadResult.Page(data = secondPage, prevKey = null, nextKey = null)
                }
            }
        }

        override fun getRefreshKey(state: PagingState<Int, Game>): Int? = null
    }

    @Test
    fun validatedNetworkReconnect_retriesFailedRefresh_clearsErrorWithoutUserTap() {
        val source = RecoveringPagingSource(sampleGames, emptyList(), failRefresh = true, failAppend = false)
        val results = Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20),
            pagingSourceFactory = { source },
        ).flow
        val networkState = mutableStateOf(NetworkStatus.Unknown)

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", searchActive = true),
                searchResults = results,
                networkStatus = networkState.value,
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {},
            )
        }

        val context = composeTestRule.activity
        // The failed generation is terminal until an event arrives: no connectivity edge yet.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.error_network))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, source.refreshAttempts)

        // Drive a real Unavailable -> Available transition through the screen's input
        // surface, forcing recomposition between the two writes so each LaunchedEffect
        // pass observes one state step.
        composeTestRule.runOnUiThread { networkState.value = NetworkStatus.Unavailable }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { networkState.value = NetworkStatus.Available }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, source.refreshAttempts)
        composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertDoesNotExist()
    }

    @Test
    fun validatedNetworkReconnectDuringActivityRecreation_retriesFailedRefresh() {
        val source = RecoveringPagingSource(sampleGames, emptyList(), failRefresh = true, failAppend = false)
        // cachedIn mirrors production: the ViewModel-scope generation survives the
        // recreation, so the restored composition re-attaches to a still-failed load
        // instead of starting a new refresh.
        val results = Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20),
            pagingSourceFactory = { source },
        ).flow.cachedIn(testScope)
        val networkState = mutableStateOf(NetworkStatus.Unknown)
        val scenario = composeTestRule.activityRule.scenario
        // The rule's own setContent does not survive a manual scenario.recreate(); the
        // content must be attached per activity instance, exactly like the production
        // host does. The new instance restores the rememberSaveable baseline from the
        // recreation Bundle.
        fun attach() {
            scenario.onActivity { activity ->
                activity.setContent {
                    SearchScreen(
                        uiState = SearchUiState(query = "witcher", searchActive = true),
                        searchResults = results,
                        networkStatus = networkState.value,
                        onQueryChange = {},
                        onClearQuery = {},
                        onGameClick = {},
                        onBackClick = {},
                    )
                }
            }
        }
        attach()

        val context = composeTestRule.activity
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.error_network))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, source.refreshAttempts)

        // The composition records the Unavailable baseline...
        composeTestRule.runOnUiThread { networkState.value = NetworkStatus.Unavailable }
        composeTestRule.waitForIdle()
        // ...then the only active composition is destroyed by the recreation. The
        // Available flip is published strictly AFTER recreate() returned and BEFORE the
        // replacement composition attaches, so no live composition can consume the edge
        // first: only the restored rememberSaveable baseline can replay it. A plain
        // remember baseline would restore Unknown and suppress the edge forever.
        scenario.recreate()
        assertEquals(1, source.refreshAttempts)
        composeTestRule.runOnUiThread { networkState.value = NetworkStatus.Available }
        attach()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertEquals(2, source.refreshAttempts)
        composeTestRule.onNodeWithText(context.getString(R.string.error_network)).assertDoesNotExist()
    }

    @Test
    fun validatedNetworkReconnect_retriesFailedAppend_loadsNextPageWithoutUserTap() {
        val secondPage = listOf(
            sampleGames[0].copy(id = 501L, name = "Append First"),
            sampleGames[1].copy(id = 502L, name = "Append Second"),
        )
        val source = RecoveringPagingSource(sampleGames, secondPage, failRefresh = false, failAppend = true)
        val results = Pager(
            config = PagingConfig(pageSize = 2, initialLoadSize = 2, prefetchDistance = 2),
            pagingSourceFactory = { source },
        ).flow
        val networkState = mutableStateOf(NetworkStatus.Unknown)

        composeTestRule.setContent {
            SearchScreen(
                uiState = SearchUiState(query = "witcher", searchActive = true),
                searchResults = results,
                networkStatus = networkState.value,
                onQueryChange = {},
                onClearQuery = {},
                onGameClick = {},
                onBackClick = {},
            )
        }

        // First page renders; the prefetch append fails and stays un-retried.
        composeTestRule.waitUntil(timeoutMillis = 5_000) { source.appendAttempts == 1 }
        composeTestRule.onNodeWithText("The Witcher 3: Wild Hunt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Append First").assertDoesNotExist()

        composeTestRule.runOnUiThread { networkState.value = NetworkStatus.Unavailable }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread { networkState.value = NetworkStatus.Available }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Append First").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, source.appendAttempts)
    }

    @Suppress("TooManyFunctions")
    private class FakePagedGameRepository : GameRepository {
        @Volatile
        var capturedPageSize: Int? = null
        @Volatile
        var capturedHistoryQuery: String? = null

        // One gate per dispatched query: `requested` proves the paged flow started for that
        // query; `emit` releases its generation. This drives deterministic A/B transitions.
        private val gates = ConcurrentHashMap<String, CompletableDeferred<PagingData<Game>>>()

        fun requested(query: String): Boolean = gates.containsKey(query)

        fun emit(query: String, generation: PagingData<Game>) {
            gates.getValue(query).complete(generation)
        }

        override fun getTopRatedGamesFlow(): Flow<List<Game>> = flowOf(emptyList())

        override fun getPagedTopRatedGames(pageSize: Int): Flow<PagingData<Game>> {
            return flowOf(PagingData.empty())
        }

        override suspend fun refreshTopRatedGames(limit: Int, offset: Int): AppResult<Unit> {
            return AppResult.Success(Unit)
        }

        override fun getTrendingGamesFlow(): Flow<List<Game>> = flowOf(emptyList())

        override suspend fun refreshTrendingGames(limit: Int, offset: Int, append: Boolean): AppResult<Unit> {
            return AppResult.Success(Unit)
        }

        override suspend fun getRecommendationCandidates(
            genres: List<String>,
            themes: List<String>,
            platforms: List<String>,
            exclude: Set<Long>,
            similarTo: List<Long>,
            limit: Int,
        ) = AppResult.Success(emptyList<io.github.typenil.gametracker.core.model.RecommendationCandidate>())

        override fun getSearchResultsFlow(query: io.github.typenil.gametracker.core.model.GameSearchQuery): Flow<List<Game>> = flowOf(emptyList())

        override fun getPagedSearchResults(query: io.github.typenil.gametracker.core.model.GameSearchQuery, pageSize: Int): Flow<PagingData<Game>> {
            capturedPageSize = pageSize
            val gate = gates.computeIfAbsent(query.query) { CompletableDeferred() }
            return flow { emit(gate.await()) }
        }

        override suspend fun recordSearchHistory(rawQuery: String): AppResult<Unit> {
            capturedHistoryQuery = rawQuery
            return AppResult.Success(Unit)
        }

        override suspend fun searchGames(query: io.github.typenil.gametracker.core.model.GameSearchQuery, limit: Int, force: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)

        override fun getRecentSearchQueriesFlow(limit: Int): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteSearchQuery(query: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun clearSearchHistory(): AppResult<Unit> = AppResult.Success(Unit)
        override fun getGameDetailsFlow(id: Long): Flow<GameDetails?> = flowOf(null)

        override fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean> = flowOf(false)

        override suspend fun refreshGameDetails(id: Long, force: Boolean): AppResult<Unit> {
            return AppResult.Error(AppError.UnknownError(NoSuchElementException()))
        }

        override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int = 0
    }

    private class FakeLibraryRepository : LibraryRepository {
        override fun getLibraryGamesFlow(): Flow<List<LibraryGame>> = MutableStateFlowHolder.empty
        override fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntry?> = flowOf(null)
        override suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun addToWishlist(game: Game): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun upsertUserEdits(
            gameId: Long,
            status: LibraryStatus,
            userRating: Int?,
            hoursPlayed: Int,
            userNotes: String?,
            isFavorite: Boolean,
        ): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun toggleFavorite(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun updateHoursPlayed(gameId: Long, hoursPlayed: Int): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
    }

    private object MutableStateFlowHolder {
        val empty: Flow<List<LibraryGame>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }
}
