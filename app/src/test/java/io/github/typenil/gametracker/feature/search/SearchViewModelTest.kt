package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
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
import io.github.typenil.gametracker.core.model.SearchInputViolation
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LargeClass") // Test suite grows with every search-contract case; an artificial split adds nothing.
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

    /**
     * Default repository stub: a cold, never-completing paging flow. Tests that need a visible
     * content generation override it with a per-query matcher stub.
     */
    private val defaultPagedFlow: Flow<PagingData<Game>> = flow { awaitCancellation() }

    @Before
    fun setUp() {
        every { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) } returns defaultPagedFlow
        coEvery { repository.recordSearchHistory(any()) } returns AppResult.Success(Unit)
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

    private fun stubPagedResults(
        matches: (GameSearchQuery) -> Boolean,
        flow: Flow<PagingData<Game>>,
    ) {
        every { repository.getPagedSearchResults(match<GameSearchQuery> { matches(it) }, any()) } returns flow
    }

    private val networkStatus = MutableStateFlow(NetworkStatus.Unknown)
    private val networkMonitor: NetworkMonitor = mockk {
        every { status } returns networkStatus
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SearchViewModel {
        return SearchViewModel(
            gameRepository = repository,
            savedStateHandle = savedStateHandle,
            libraryRepository = libraryRepository,
            clock = java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T12:00:00Z"), java.time.ZoneOffset.UTC),
            networkMonitor = networkMonitor,
        )
    }

    @Test
    fun `networkStatus is an identity passthrough of the monitor state flow`() {
        val viewModel = createViewModel()
        assertSame(networkStatus, viewModel.networkStatus)
    }

    @Test
    fun `initial state with empty SavedStateHandle is Idle and triggers no repository calls`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals("", item.query)
            assertEquals(false, item.searchActive)
            assertEquals(SearchFilters.Empty, item.filters)
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
        coVerify(exactly = 0) { repository.recordSearchHistory(any()) }
    }

    @Test
    fun `invalid query with quotes is never dispatched and stays idle with inline error`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(false, initial.searchActive)

            viewModel.onQueryChanged("Grand \"Theft\" Auto")
            runCurrent()

            val state = awaitItem()
            assertEquals(false, state.searchActive)
            assertTrue(state.inputValidation is SearchInputValidation.Invalid)

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
    }

    @Test
    fun `leading control character stays invalid and is never dispatched`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onQueryChanged("\nDOOM")
            runCurrent()

            val state = awaitItem()
            assertEquals(false, state.searchActive)
            assertEquals(
                SearchInputValidation.Invalid(SearchInputViolation.CONTROL_CHAR),
                state.inputValidation,
            )

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
        coVerify(exactly = 0) { repository.recordSearchHistory(any()) }
    }

    @Test
    fun `trailing control character stays invalid and is never dispatched`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onQueryChanged("DOOM\t")
            runCurrent()

            val state = awaitItem()
            assertEquals(false, state.searchActive)
            assertEquals(
                SearchInputValidation.Invalid(SearchInputViolation.CONTROL_CHAR),
                state.inputValidation,
            )

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
        coVerify(exactly = 0) { repository.recordSearchHistory(any()) }
    }

    @Test
    fun `101 code point query is preserved as invalid and never dispatched`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val tooLong = "a".repeat(101)
        viewModel.uiState.test {
            awaitItem()

            viewModel.onQueryChanged(tooLong)
            runCurrent()

            val state = awaitItem()
            assertEquals(false, state.searchActive)
            assertEquals(
                SearchInputValidation.Invalid(SearchInputViolation.TOO_LONG),
                state.inputValidation,
            )

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
    }

    @Test
    fun `101 decomposed characters surface TOO_LONG instead of a truncated search`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val decomposed = "e\u0301".repeat(101)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onQueryChanged(decomposed)
            runCurrent()

            val state = awaitItem()
            assertEquals("\u00E9".repeat(101), state.query) // stored as NFC, never mid-mark cut
            assertEquals(false, state.searchActive)
            assertEquals(
                SearchInputValidation.Invalid(SearchInputViolation.TOO_LONG),
                state.inputValidation,
            )

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
    }

    @Test
    fun `decomposed 100 character title is dispatched verbatim and not truncated`() = runTest(testDispatcher) {
        val decomposed = "e\u0301".repeat(100)
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.query == decomposed }, flowOf(gen))
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.uiState.collect { } }
        backgroundScope.launch { viewModel.searchResults.collect { } }
        runCurrent()

        viewModel.onQueryChanged(decomposed)
        advanceTimeBy(350L)
        advanceUntilIdle()

        assertEquals(decomposed, viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.searchActive)

        verify(exactly = 1) {
            repository.getPagedSearchResults(match<GameSearchQuery> { it.query == decomposed }, any())
        }
        coVerify(exactly = 1) { repository.recordSearchHistory(decomposed) }
    }

    @Test
    fun `query does not trigger search before 300 ms debounce`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "witcher" }, flowOf(gen))
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation for the initial blank query

            viewModel.onQueryChanged("witcher")
            runCurrent()
            awaitItem() // pending loading generation is emitted before the debounce elapses

            advanceTimeBy(250L) // Under 300ms debounce
            runCurrent()
            expectNoEvents()
            verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
            coVerify(exactly = 0) { repository.recordSearchHistory(any()) }

            advanceTimeBy(100L) // Exceeds 300ms debounce
            advanceUntilIdle()
            awaitItem() // content generation from the repository

            cancelAndIgnoreRemainingEvents()
        }

        // The paging contract pins the repository pageSize to the VM constant.
        verify(exactly = 1) {
            repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "witcher" }, SearchViewModel.PAGE_SIZE)
        }
    }

    @Test
    fun `query shows loading rather than empty during debounce`() = runTest(testDispatcher) {
        val pagedStarted = CompletableDeferred<Nothing>()
        stubPagedResults({ it.query == "doom" }, flow { pagedStarted.await() })
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation

            viewModel.onQueryChanged("doom")
            runCurrent()

            // A generation exists and was emitted synchronously: the container renders its
            // LoadState (refresh = Loading from the pending generation), never a stale replay
            // or an idle empty state while the debounce is running.
            awaitItem()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing query never renders previous generation items`() = runTest(testDispatcher) {
        val genAlpha = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "alpha" }, flowOf(genAlpha))
        val betaPaged = CompletableDeferred<Nothing>()
        stubPagedResults({ it.query == "beta" }, flow { betaPaged.await() })
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation

            viewModel.onQueryChanged("alpha")
            runCurrent()
            awaitItem() // pending for alpha
            advanceTimeBy(350L)
            advanceUntilIdle()
            val alphaContent = awaitItem() // alpha's content generation

            viewModel.onQueryChanged("beta")
            runCurrent()
            val firstForBeta = awaitItem()
            // The committed command replaces the replayed alpha generation immediately:
            // what the UI shows next can never be alpha's PagingData.
            assertNotSame(alphaContent, firstForBeta)
            assertNotSame(genAlpha, firstForBeta)

            advanceTimeBy(350L)
            runCurrent()
            // Beta's repository flow never emits: the presented generation stays pending
            // (Loading), it does not fall back to alpha's items or to an empty state.
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rapid typing calls repository only once with final query`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "witcher" }, flowOf(gen))
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation

            viewModel.onQueryChanged("w")
            runCurrent()
            awaitItem() // pending for "w"

            advanceTimeBy(100L)
            viewModel.onQueryChanged("wi")
            runCurrent()
            awaitItem() // pending for "wi"

            advanceTimeBy(100L)
            viewModel.onQueryChanged("wit")
            runCurrent()
            awaitItem() // pending for "wit"

            advanceTimeBy(100L)
            viewModel.onQueryChanged("witcher")
            runCurrent()
            awaitItem() // pending for "witcher"

            advanceTimeBy(350L) // Debounce passes for the final query only
            advanceUntilIdle()
            awaitItem() // content generation

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "witcher" }, any()) }
        verify(exactly = 0) { repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "w" }, any()) }
        verify(exactly = 0) { repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "wi" }, any()) }
        verify(exactly = 0) { repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "wit" }, any()) }
        coVerify(exactly = 0) { repository.recordSearchHistory("w") }
        coVerify(exactly = 0) { repository.recordSearchHistory("wi") }
        coVerify(exactly = 1) { repository.recordSearchHistory("witcher") }
    }

    @Test
    fun `in-flight paged search is cancelled immediately when a new query is entered`() = runTest(testDispatcher) {
        val firstQueryCancelled = AtomicBoolean(false)
        stubPagedResults({ it.query == "first" }, flow {
            try {
                awaitCancellation()
            } finally {
                firstQueryCancelled.set(true)
            }
        })
        val genSecond = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "second" }, flowOf(genSecond))

        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation

            viewModel.onQueryChanged("first")
            runCurrent()
            awaitItem() // pending "first"

            advanceTimeBy(350L) // first generation goes in-flight
            runCurrent()

            viewModel.onQueryChanged("second")
            runCurrent() // Immediate cancellation before second query debounce passes

            assertTrue(firstQueryCancelled.get())
            coVerify(exactly = 0) { repository.recordSearchHistory("second") }

            awaitItem() // pending "second" replaces the cancelled generation

            advanceTimeBy(350L) // Second query debounce passes
            advanceUntilIdle()
            awaitItem() // content generation

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "second" }, any()) }
    }

    @Test
    fun `history failure does not prevent paged search generation`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "witcher" }, flowOf(gen))
        coEvery { repository.recordSearchHistory("witcher") } returns
            AppResult.Error(AppError.UnknownError(RuntimeException("disk full")))
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.uiState.collect { } }
        backgroundScope.launch { viewModel.searchResults.collect { } }
        runCurrent()

        viewModel.onQueryChanged("witcher")
        advanceTimeBy(350L)
        advanceUntilIdle()

        // The repository dispatch and the content generation happened despite the failed
        // history write, and the failure surfaced as a one-shot user message.
        verify(exactly = 1) {
            repository.getPagedSearchResults(match<GameSearchQuery> { it.query == "witcher" }, any())
        }
        assertEquals(R.string.error_history_save_failed, viewModel.uiState.value.userMessageRes)
    }

    @Test
    fun `suspended history write does not delay paged generation`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "witcher" }, flowOf(gen))
        val historyGate = CompletableDeferred<Unit>()
        coEvery { repository.recordSearchHistory("witcher") } coAnswers {
            historyGate.await()
            AppResult.Success(Unit)
        }
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation

            viewModel.onQueryChanged("witcher")
            runCurrent()
            awaitItem() // pending

            advanceTimeBy(350L)
            advanceUntilIdle()
            // The content generation arrives while the history write is still suspended:
            // storage latency can never keep the screen on the skeleton.
            awaitItem()
            assertFalse(historyGate.isCompleted)

            historyGate.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing query cancels the in-flight history write`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.query == "witcher" }, flowOf(gen))
        stubPagedResults({ it.query == "doom" }, flowOf(gen))
        val historyCancelled = AtomicBoolean(false)
        coEvery { repository.recordSearchHistory("witcher") } coAnswers {
            try {
                awaitCancellation()
            } finally {
                historyCancelled.set(true)
            }
        }
        coEvery { repository.recordSearchHistory("doom") } returns AppResult.Success(Unit)
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle

            viewModel.onQueryChanged("witcher")
            runCurrent()
            awaitItem() // pending witcher
            advanceTimeBy(350L)
            advanceUntilIdle()
            awaitItem() // content with the history write still in flight

            viewModel.onQueryChanged("doom")
            runCurrent()
            awaitItem() // pending doom
            // flatMapLatest tears down the whole branch scope, so the stale history task is
            // cancelled instead of ever persisting the previous query.
            assertTrue(historyCancelled.get())

            advanceTimeBy(350L)
            advanceUntilIdle()
            awaitItem() // doom content

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter changes trigger search after filter debounce`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames)
        stubPagedResults({ it.genres.contains("Role-playing (RPG)") }, flowOf(gen))

        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle generation

            // Select genre filter on blank query
            viewModel.onGenreToggled("Role-playing (RPG)")
            advanceTimeBy(150L) // Filter debounce window (shorter than the 300ms text debounce)
            runCurrent()

            awaitItem() // pending generation
            awaitItem() // content generation — no 300ms text delay for filter commands
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            repository.getPagedSearchResults(
                match<GameSearchQuery> { it.genres.contains("Role-playing (RPG)") },
                any(),
            )
        }
    }

    @Test
    fun `text query keeps server relevance order and ignores local sort re-application`() = runTest(testDispatcher) {
        val gen = PagingData.from(sampleGames) // [Witcher 3 (95.0), Witcher 2 (88.0)] server order
        stubPagedResults({ it.query == "witcher" }, flowOf(gen))
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle

            viewModel.onQueryChanged("witcher")
            runCurrent()
            awaitItem() // pending
            advanceTimeBy(350L)
            advanceUntilIdle()
            awaitItem() // content generation

            // Switch sort while an active text query is present: toDomainQuery drops wire sort
            // for text searches, so the committed domainQuery is unchanged and
            // distinctUntilChanged absorbs the command — no second paged dispatch.
            viewModel.onSortSelected(SearchSortOption.RATING_DESC)
            advanceTimeBy(350L)
            advanceUntilIdle()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
    }

    @Test
    fun `filter change with active text query starts refresh after filter debounce without 300 ms text delay`() = runTest(testDispatcher) {
        stubPagedResults({ true }, flowOf(PagingData.empty()))
        val viewModel = createViewModel()

        viewModel.searchResults.test {
            awaitItem() // idle

            viewModel.onQueryChanged("witcher")
            advanceTimeBy(350L) // Wait for text debounce
            advanceUntilIdle()
            awaitItem() // pending "witcher"
            awaitItem() // empty content generation

            // Now with text query active, toggle a filter
            viewModel.onGenreToggled("Role-playing (RPG)")
            advanceTimeBy(150L) // Filter debounce only, no 300ms text debounce
            runCurrent()
            awaitItem() // pending for combined query
            awaitItem() // content generation

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) {
            repository.getPagedSearchResults(
                match<GameSearchQuery> { it.query == "witcher" && it.genres.contains("Role-playing (RPG)") },
                any(),
            )
        }
    }

    @Test
    fun `onApplyFilters updates all filters in single atomic step`() = runTest(testDispatcher) {
        stubPagedResults({ true }, flowOf(PagingData.empty()))
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.searchResults.collect { } }
        runCurrent()

        viewModel.onApplyFilters(
            SearchFilters(
                genres = setOf("Role-playing (RPG)", "Action"),
                platforms = setOf(PlatformFamily.PC),
                releaseYear = ReleaseYearFilter.THIS_YEAR,
                minRating = MinRatingFilter.R80,
                sort = SearchSortOption.RATING_DESC,
            )
        )
        advanceUntilIdle()

        val filters = viewModel.filters.value
        assertEquals(setOf("Role-playing (RPG)", "Action"), filters.genres)
        assertEquals(setOf(PlatformFamily.PC), filters.platforms)
        assertEquals(ReleaseYearFilter.THIS_YEAR, filters.releaseYear)
        assertEquals(MinRatingFilter.R80, filters.minRating)
        assertEquals(SearchSortOption.RATING_DESC, filters.sort)

        // Exactly one paged query dispatched (blank text: wire sort is applied).
        verify(exactly = 1) {
            repository.getPagedSearchResults(
                match<GameSearchQuery> { query ->
                    query.genres == listOf("Action", "Role-playing (RPG)") &&
                        query.minRating == 80 &&
                        query.sort == "rating"
                },
                any(),
            )
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
    fun `restored state with filter-only parameters activates search with restored filters`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                SearchViewModel.KEY_QUERY to "",
                SearchViewModel.KEY_FILTERS to SearchFilters(minRating = MinRatingFilter.R80).toSnapshot(),
            )
        )
        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals(MinRatingFilter.R80, viewModel.filters.value.minRating)
        assertTrue(viewModel.uiState.value.searchActive)
        assertEquals(MinRatingFilter.R80, viewModel.uiState.value.filters.minRating)
    }

    @Test
    fun `restored active search starts loading rather than empty`() = runTest(testDispatcher) {
        val started = CompletableDeferred<Nothing>()
        stubPagedResults({ it.query == "witcher" }, flow { started.await() })
        val savedStateHandle = SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "witcher"))
        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        viewModel.searchResults.test {
            // The very first generation for a restored active query is the pending (Loading)
            // one; it arrives without advancing time and before any repository dispatch.
            awaitItem()
            verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restored invalid query starts Idle and is never dispatched`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(
            mapOf(SearchViewModel.KEY_QUERY to "\nDOOM"),
        )
        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        viewModel.uiState.test {
            // Collecting starts WhileSubscribed upstream; invalid input must stay inactive and
            // the search pipeline must never dispatch, even after scheduled work is allowed to run.
            val initial = awaitItem()
            assertEquals(false, initial.searchActive)
            assertEquals(
                SearchInputValidation.Invalid(SearchInputViolation.CONTROL_CHAR),
                initial.inputValidation,
            )

            runCurrent()
            verify(exactly = 0) { repository.getPagedSearchResults(any<GameSearchQuery>(), any()) }
            coVerify(exactly = 0) { repository.recordSearchHistory(any()) }

            cancelAndIgnoreRemainingEvents()
        }
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
    fun `filter-only search keeps constraint context for the empty state`() = runTest(testDispatcher) {
        stubPagedResults({ true }, flowOf(PagingData.empty()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial inactive state

            viewModel.onGenreToggled("Simulator")
            testScheduler.runCurrent()
            advanceTimeBy(150L)
            runCurrent()

            val state = expectMostRecentItem()
            assertTrue(state.searchActive)
            assertTrue(state.filters.hasConstraints)

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
