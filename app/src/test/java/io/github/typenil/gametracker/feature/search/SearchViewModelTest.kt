package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameSearchQuery
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.SearchInputValidation
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository: GameRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk()
    private val libraryFlow = MutableStateFlow<List<LibraryGame>>(emptyList())
    private val recentQueriesFlow = MutableStateFlow<List<String>>(emptyList())

    private val sampleGames = listOf(
        Game(
            id = 1L,
            name = "The Witcher 3: Wild Hunt",
            rating = 95.0,
            releaseDateEpochSeconds = 1431993600L,
            genres = listOf("Role-playing (RPG)", "Adventure"),
            platforms = listOf("PC (Microsoft Windows)", "PlayStation 4")
        ),
        Game(
            id = 2L,
            name = "The Witcher 2: Assassins of Kings",
            rating = 88.0,
            releaseDateEpochSeconds = 1305590400L,
            genres = listOf("Role-playing (RPG)"),
            platforms = listOf("PC (Microsoft Windows)", "Xbox 360")
        )
    )

    private val defaultSearchFlow = MutableStateFlow<List<Game>>(emptyList())

    @Before
    fun setUp() {
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns defaultSearchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) } returns AppResult.Success(Unit)
        every { repository.getRecentSearchQueriesFlow(any()) } returns recentQueriesFlow
        every { libraryRepository.getLibraryGamesFlow() } returns libraryFlow
        coEvery { libraryRepository.addToWishlist(any()) } returns AppResult.Success(Unit)
        coEvery {
            libraryRepository.upsertUserEdits(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(Unit)
        coEvery { libraryRepository.removeGameFromLibrary(any()) } returns AppResult.Success(Unit)
        coEvery { repository.refreshGameDetails(any(), any()) } returns AppResult.Success(Unit)
        coEvery { repository.deleteSearchQuery(any()) } returns AppResult.Success(Unit)
        coEvery { repository.clearSearchHistory() } returns AppResult.Success(Unit)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SearchViewModel {
        return SearchViewModel(
            gameRepository = repository,
            savedStateHandle = savedStateHandle,
            libraryRepository = libraryRepository,
            clock = java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T12:00:00Z"), java.time.ZoneOffset.UTC),
        )
    }

    @Test
    fun `initial state with empty SavedStateHandle is Idle and triggers no repository calls`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals("", item.query)
            assertEquals(SearchResultUiState.Idle, item.result)
            assertEquals(SearchFilters.Empty, item.filters)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) }
    }

    @Test
    fun `invalid query with quotes is never dispatched and stays idle with inline error`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(SearchResultUiState.Idle, initial.result)

            viewModel.onQueryChanged("Grand \"Theft\" Auto")
            runCurrent()

            val state = awaitItem()
            assertEquals(SearchResultUiState.Idle, state.result)
            assertTrue(state.inputValidation is SearchInputValidation.Invalid)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) }
    }

    @Test
    fun `query does not trigger search before 300 ms debounce`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(match<GameSearchQuery> { it.query == "witcher" }) } returns searchFlow
        coEvery { repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, any(), any(), any()) } coAnswers {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(SearchResultUiState.Idle, initial.result)
            testScheduler.runCurrent()

            viewModel.onQueryChanged("witcher")
            testScheduler.runCurrent()
            val intermediateLoading = awaitItem()
            assertEquals("witcher", intermediateLoading.query)
            assertEquals(SearchResultUiState.Loading, intermediateLoading.result)

            advanceTimeBy(250L) // Under 300ms debounce
            coVerify(exactly = 0) { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) }

            advanceTimeBy(100L) // Exceeds 300ms debounce
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, 30, any(), any()) }
    }

    @Test
    fun `rapid typing calls repository only once with final query`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(match<GameSearchQuery> { it.query == "witcher" }) } returns searchFlow
        coEvery { repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, any(), any(), any()) } coAnswers {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("w")
            testScheduler.runCurrent()
            awaitItem() // Loading for "w"

            advanceTimeBy(100L)
            viewModel.onQueryChanged("wi")
            testScheduler.runCurrent()
            awaitItem() // Loading for "wi"

            advanceTimeBy(100L)
            viewModel.onQueryChanged("wit")
            testScheduler.runCurrent()
            awaitItem() // Loading for "wit"

            advanceTimeBy(100L)
            viewModel.onQueryChanged("witcher")
            testScheduler.runCurrent()
            awaitItem() // Loading for "witcher"

            advanceTimeBy(350L) // Debounce passes
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, 30, any(), any()) }
        coVerify(exactly = 0) { repository.searchGames(match<GameSearchQuery> { it.query == "w" }, any(), any(), any()) }
        coVerify(exactly = 0) { repository.searchGames(match<GameSearchQuery> { it.query == "wi" }, any(), any(), any()) }
        coVerify(exactly = 0) { repository.searchGames(match<GameSearchQuery> { it.query == "wit" }, any(), any(), any()) }
    }

    @Test
    fun `in-flight search is cancelled immediately when a new query is entered`() = runTest(testDispatcher) {
        val firstQueryCancelled = AtomicBoolean(false)
        val secondSearchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(match<GameSearchQuery> { it.query == "second" }) } returns secondSearchFlow

        coEvery { repository.searchGames(match<GameSearchQuery> { it.query == "first" }, any(), any(), any()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                firstQueryCancelled.set(true)
            }
        }
        coEvery { repository.searchGames(match<GameSearchQuery> { it.query == "second" }, any(), any(), any()) } coAnswers {
            secondSearchFlow.value = sampleGames
            AppResult.Success(Unit)
        }

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("first")
            testScheduler.runCurrent()
            awaitItem() // Loading "first"

            advanceTimeBy(350L) // Triggers first search into in-flight execution

            viewModel.onQueryChanged("second")
            runCurrent() // Immediate cancellation before second query debounce passes

            assertTrue(firstQueryCancelled.get())
            coVerify(exactly = 0) { repository.searchGames(match<GameSearchQuery> { it.query == "second" }, any(), any(), any()) }

            awaitItem() // Loading "second"

            advanceTimeBy(350L) // Second query debounce passes
            advanceUntilIdle()
            val secondContent = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), secondContent.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames(match<GameSearchQuery> { it.query == "second" }, 30, any(), any()) }
    }

    @Test
    fun `cached search results are visible immediately while network search is in flight`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow(sampleGames)
        every { repository.getSearchResultsFlow(match<GameSearchQuery> { it.query == "witcher" }) } returns searchFlow
        val searchDeferred = CompletableDeferred<AppResult<Unit>>()
        coEvery { repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, any(), any(), any()) } coAnswers {
            searchDeferred.await()
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            viewModel.onQueryChanged("witcher")
            runCurrent()
            advanceTimeBy(350L) // Debounce passes
            runCurrent()

            // Cached games appear immediately as Content without waiting for network to complete
            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            // Now network completes with updated list
            val freshGames = sampleGames + Game(id = 3L, name = "The Witcher: Enhanced Edition", rating = 86.0)
            searchFlow.value = freshGames
            searchDeferred.complete(AppResult.Success(Unit))
            runCurrent()

            val updatedState = awaitItem()
            assertEquals(SearchResultUiState.Content(freshGames), updatedState.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter changes trigger search after filter debounce`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every {
            repository.getSearchResultsFlow(match<GameSearchQuery> { it.genres.contains("Role-playing (RPG)") })
        } returns searchFlow
        coEvery {
            repository.searchGames(match<GameSearchQuery> { it.genres.contains("Role-playing (RPG)") }, any(), any(), any())
        } coAnswers {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            // Select genre filter on blank query
            viewModel.onGenreToggled("Role-playing (RPG)")
            advanceTimeBy(150L) // Filter debounce window (shorter than the 300ms text debounce)
            testScheduler.runCurrent()

            val loadingState = awaitItem()
            assertEquals(SearchResultUiState.Loading, loadingState.result)
            assertTrue(loadingState.filters.genres.contains("Role-playing (RPG)"))

            // Verify repository is called immediately without waiting for 300ms debounce
            coVerify(exactly = 1) {
                repository.searchGames(match<GameSearchQuery> { it.genres.contains("Role-playing (RPG)") }, 30, any(), any())
            }

            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `text query keeps server relevance order and ignores local sort re-application`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow(sampleGames) // [Witcher 3 (95.0), Witcher 2 (88.0)]
        every { repository.getSearchResultsFlow(match<GameSearchQuery> { it.query == "witcher" }) } returns searchFlow
        coEvery {
            repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, any(), any(), any())
        } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(SearchResultUiState.Idle, initial.result)

            viewModel.onQueryChanged("witcher")
            advanceTimeBy(350L)
            advanceUntilIdle()

            val defaultContent = awaitItem()
            val gamesRelevance = (defaultContent.result as SearchResultUiState.Content).games
            assertEquals(listOf(1L, 2L), gamesRelevance.map { it.id }) // Relevance order preserved
            assertEquals(SearchResultUiState.Content(sampleGames), defaultContent.result)

            // Switch sort while an active text query is present: the server-side relevance
            // window is preserved, no local re-sort and no second network call (wire sort is
            // only applied for blank-query browse searches).
            viewModel.onSortSelected(SearchSortOption.RATING_DESC)
            advanceTimeBy(350L)
            advanceUntilIdle()

            val afterSort = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), afterSort.result)

            coVerify(exactly = 1) { repository.searchGames(any<GameSearchQuery>(), any<Int>(), any<Int>(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry after filter-only error executes second repository search`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns searchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any<Int>(), any<Int>(), any()) } returnsMany listOf(
            AppResult.Error(io.github.typenil.gametracker.core.model.AppError.NetworkError),
            AppResult.Success(Unit),
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onGenreToggled("Role-playing (RPG)")
            testScheduler.runCurrent()
            val loading = awaitItem()
            assertEquals(SearchResultUiState.Loading, loading.result)

            advanceUntilIdle()
            val errorState = awaitItem()
            assertTrue(errorState.result is SearchResultUiState.Error)

            // Retry should trigger second network call even with identical filter query
            searchFlow.value = sampleGames
            viewModel.retry()
            testScheduler.runCurrent()

            advanceUntilIdle()
            val successState = awaitItem()
            assertTrue(successState.result is SearchResultUiState.Content)
            assertEquals(2, (successState.result as SearchResultUiState.Content).games.size)
            coVerify(exactly = 2) { repository.searchGames(any<GameSearchQuery>(), any<Int>(), any<Int>(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two consecutive errors are both retried and reach repository`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns searchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any<Int>(), any<Int>(), any()) } returns
            AppResult.Error(io.github.typenil.gametracker.core.model.AppError.NetworkError)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onQueryChanged("witcher")
            advanceTimeBy(350L)
            advanceUntilIdle()
            awaitItem() // Error 1

            viewModel.retry()
            testScheduler.runCurrent()
            advanceUntilIdle()
            awaitItem() // Error 2

            viewModel.retry()
            testScheduler.runCurrent()
            advanceUntilIdle()
            awaitItem() // Error 3
            coVerify(exactly = 3) { repository.searchGames(any<GameSearchQuery>(), any<Int>(), any<Int>(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter change with active text query starts refresh after filter debounce without 300 ms text delay`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns searchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onQueryChanged("witcher")
            advanceTimeBy(350L) // Wait for text debounce
            runCurrent()
            awaitItem() // Intermediate state

            // Now with text query active, toggle a filter
            viewModel.onGenreToggled("Role-playing (RPG)")
            advanceTimeBy(150L) // Filter debounce only, no 300ms text debounce
            runCurrent()
            awaitItem() // Loading for combined query

            coVerify(exactly = 1) {
                repository.searchGames(
                    match<GameSearchQuery> { it.query == "witcher" && it.genres.contains("Role-playing (RPG)") },
                    30,
                    any(),
                    any(),
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cached results plus refresh failure exposes stale content state with refreshError`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow(sampleGames)
        every { repository.getSearchResultsFlow(match<GameSearchQuery> { it.query == "witcher" }) } returns searchFlow
        coEvery { repository.searchGames(match<GameSearchQuery> { it.query == "witcher" }, any(), any(), any()) } returns
            AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onQueryChanged("witcher")
            advanceTimeBy(350L)
            advanceUntilIdle()

            val contentState = expectMostRecentItem()
            assertTrue(contentState.result is SearchResultUiState.Content)
            val content = contentState.result as SearchResultUiState.Content
            assertEquals(sampleGames, content.games)
            assertEquals(AppError.NetworkError, content.refreshError)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onApplyFilters updates all filters in single atomic step`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow(sampleGames)
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns searchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onApplyFilters(
                SearchFilters(
                    genres = setOf("Role-playing (RPG)", "Action"),
                    platforms = setOf(PlatformFamily.PC),
                    releaseYear = ReleaseYearFilter.THIS_YEAR,
                    minRating = MinRatingFilter.R80,
                    sort = SearchSortOption.RATING_DESC,
                )
            )
            testScheduler.runCurrent()
            advanceUntilIdle()
            awaitItem() // Loading / Content

            val filters = viewModel.filters.value
            assertEquals(setOf("Role-playing (RPG)", "Action"), filters.genres)
            assertEquals(setOf(PlatformFamily.PC), filters.platforms)
            assertEquals(ReleaseYearFilter.THIS_YEAR, filters.releaseYear)
            assertEquals(MinRatingFilter.R80, filters.minRating)
            assertEquals(SearchSortOption.RATING_DESC, filters.sort)

            // Exactly one search query dispatched
            coVerify(exactly = 1) {
                repository.searchGames(match<GameSearchQuery> { query ->
                    query.genres == listOf("Action", "Role-playing (RPG)") &&
                        query.minRating == 80 &&
                        query.sort == "rating"
                }, any<Int>(), any<Int>(), any())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `release year last 3 years covers exactly 3 calendar years`() {
        val (minYear, maxYear) = ReleaseYearFilter.LAST_3_YEARS.toYearRange(clockYear = 2026)
        assertEquals(2024, minYear)
        assertEquals(2026, maxYear)
    }
    @Test
    fun `recent searches flow is exposed and actions invoke repository`() = runTest(testDispatcher) {
        recentQueriesFlow.value = listOf("Elden Ring", "Cyberpunk 2077")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            testScheduler.runCurrent()
            val state = if (initial.recentQueries.isEmpty()) awaitItem() else initial
            assertEquals(listOf("Elden Ring", "Cyberpunk 2077"), state.recentQueries)

            viewModel.onSelectRecentQuery("Elden Ring")
            testScheduler.runCurrent()
            val queryState = awaitItem()
            assertEquals("Elden Ring", queryState.query)

            viewModel.onRemoveRecentQuery("Elden Ring")
            testScheduler.runCurrent()
            coVerify(exactly = 1) { repository.deleteSearchQuery("Elden Ring") }

            viewModel.onClearAllRecentQueries()
            testScheduler.runCurrent()
            coVerify(exactly = 1) { repository.clearSearchHistory() }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset filters clears all constraints and restores default sort`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onGenreToggled("Action")
        viewModel.onPlatformToggled(PlatformFamily.PC)
        viewModel.onMinRatingSelected(MinRatingFilter.R80)
        viewModel.onReleaseYearSelected(ReleaseYearFilter.THIS_YEAR)
        viewModel.onSortSelected(SearchSortOption.RATING_DESC)
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertTrue(viewModel.filters.value.hasConstraints)

        viewModel.onResetFilters()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertEquals(SearchFilters.Empty, viewModel.filters.value)
    }
    @Test
    fun `quick preset sets genre, platform, or rating filter`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onQuickPresetSelected(QuickSearchPreset.Genre("Role-playing (RPG)"))
        testScheduler.runCurrent()
        assertEquals(setOf("Role-playing (RPG)"), viewModel.filters.value.genres)

        viewModel.onQuickPresetSelected(QuickSearchPreset.Platform(PlatformFamily.PLAYSTATION))
        testScheduler.runCurrent()
        assertEquals(setOf(PlatformFamily.PLAYSTATION), viewModel.filters.value.platforms)

        viewModel.onQuickPresetSelected(QuickSearchPreset.Rating80)
        testScheduler.runCurrent()
        assertEquals(MinRatingFilter.R80, viewModel.filters.value.minRating)
    }

    @Test
    fun `restored state with filter-only parameters initializes uiState in Loading and with restored filters`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns searchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any<Int>(), any<Int>(), any()) } returns AppResult.Success(Unit)

        val savedStateHandle = SavedStateHandle(
            mapOf(
                SearchViewModel.KEY_QUERY to "",
                SearchViewModel.KEY_FILTERS to SearchFilters(minRating = MinRatingFilter.R80).toSnapshot(),
            )
        )
        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals(MinRatingFilter.R80, viewModel.filters.value.minRating)
        assertEquals(SearchResultUiState.Loading, viewModel.uiState.value.result)
        assertEquals(MinRatingFilter.R80, viewModel.uiState.value.filters.minRating)
    }

    @Test
    fun `remove recent query error exposes error message`() = runTest(testDispatcher) {
        coEvery { repository.deleteSearchQuery("Elden Ring") } returns AppResult.Error(AppError.UnknownError(RuntimeException("DB error")))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onRemoveRecentQuery("Elden Ring")
            testScheduler.runCurrent()
            val errorState = awaitItem()
            assertEquals(R.string.error_history_delete_failed, errorState.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear all recent queries error exposes error message`() = runTest(testDispatcher) {
        coEvery { repository.clearSearchHistory() } returns AppResult.Error(AppError.UnknownError(RuntimeException("DB error")))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onClearAllRecentQueries()
            testScheduler.runCurrent()
            val errorState = awaitItem()
            assertEquals(R.string.error_history_delete_failed, errorState.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty search result distinguishes filter constraints from plain query`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow(any<GameSearchQuery>()) } returns searchFlow
        coEvery { repository.searchGames(any<GameSearchQuery>(), any(), any(), any()) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle

            viewModel.onGenreToggled("Simulator")
            testScheduler.runCurrent()
            val loadingState = awaitItem()
            assertEquals(SearchResultUiState.Loading, loadingState.result)

            advanceUntilIdle()
            val filterEmpty = awaitItem()
            assertTrue((filterEmpty.result as SearchResultUiState.Empty).hasConstraints)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library action on non-existing game adds to wishlist`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val game = sampleGames[0]

        viewModel.onLibraryCardAction(game)
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryRepository.addToWishlist(game) }
    }

    @Test
    fun `library action on existing game opens edit sheet`() = runTest(testDispatcher) {
        val entry = LibraryEntry(gameId = 1L, status = LibraryStatus.PLAYING, addedAtEpochSeconds = 1000L, updatedAtEpochSeconds = 1000L)
        libraryFlow.value = listOf(LibraryGame(entry = entry, game = sampleGames[0]))
        val viewModel = createViewModel()

        testScheduler.runCurrent()
        viewModel.uiState.test {
            awaitItem() // Initial state with ready library

            viewModel.onLibraryCardAction(sampleGames[0])
            testScheduler.runCurrent()

            val sheetState = awaitItem()
            assertEquals(1L, sheetState.editingGameId)

            viewModel.onDismissEditLibrary()
            testScheduler.runCurrent()

            val dismissedState = awaitItem()
            assertNull(dismissedState.editingGameId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save library entry invokes upsertUserEdits`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onSaveLibraryEntry(
            gameId = 1L,
            status = LibraryStatus.COMPLETED,
            userRating = 10,
            hoursPlayed = 120,
            userNotes = "Masterpiece",
            isFavorite = true,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            libraryRepository.upsertUserEdits(1L, LibraryStatus.COMPLETED, 10, 120, "Masterpiece", true)
        }
    }

    @Test
    fun `remove from library invokes removeGameFromLibrary`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onRemoveFromLibrary(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryRepository.removeGameFromLibrary(1L) }
    }
}
