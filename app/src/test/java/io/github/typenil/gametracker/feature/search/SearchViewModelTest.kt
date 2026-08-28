package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.LibraryRepository

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.LibrarySnapshot


import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository: GameRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk()
    private val libraryFlow = MutableStateFlow<List<LibraryGame>>(emptyList())


    private val sampleGames = listOf(
        Game(
            id = 1L,
            name = "The Witcher 3: Wild Hunt",
            rating = 95.0
        ),
        Game(
            id = 2L,
            name = "The Witcher 2: Assassins of Kings",
            rating = 88.0
        )
    )

    private val defaultSearchFlow = MutableStateFlow<List<Game>>(emptyList())

    @Before
    fun setUp() {
        every { repository.getSearchResultsFlow(any()) } returns defaultSearchFlow
        every { libraryRepository.getLibraryGamesFlow() } returns libraryFlow
        coEvery { libraryRepository.addToWishlist(any()) } returns AppResult.Success(Unit)
        coEvery {
            libraryRepository.upsertUserEdits(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(Unit)
        coEvery { libraryRepository.removeGameFromLibrary(any()) } returns AppResult.Success(Unit)
        coEvery { repository.refreshGameDetails(any(), any()) } returns AppResult.Success(Unit)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SearchViewModel {
        return SearchViewModel(
            gameRepository = repository,
            savedStateHandle = savedStateHandle,
            libraryRepository = libraryRepository,
        )
    }

    @Test
    fun `initial state with empty SavedStateHandle is Idle and triggers no repository calls`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals("", item.query)
            assertEquals(SearchResultUiState.Idle, item.result)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }
    }

    @Test
    fun `query does not trigger search before 300 ms debounce`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("witcher") } returns searchFlow
        coEvery { repository.searchGames("witcher", any(), any()) } coAnswers {
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
            coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }

            advanceTimeBy(100L) // Exceeds 300ms debounce
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("witcher", 30, any()) }
    }

    @Test
    fun `rapid typing calls repository only once with final query`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("witcher") } returns searchFlow
        coEvery { repository.searchGames("witcher", any(), any()) } coAnswers {
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

            advanceTimeBy(350L) // 300ms debounce fires for "witcher"
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("witcher", 30, any()) }
        coVerify(exactly = 0) { repository.searchGames("w", any(), any()) }
        coVerify(exactly = 0) { repository.searchGames("wi", any(), any()) }
        coVerify(exactly = 0) { repository.searchGames("wit", any(), any()) }
    }

    @Test
    fun `in-flight search is cancelled immediately when a new query is entered before debounce`() = runTest(testDispatcher) {
        val firstQueryCancelled = AtomicBoolean(false)
        val secondSearchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("second") } returns secondSearchFlow

        coEvery { repository.searchGames("first", any(), any()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                firstQueryCancelled.set(true)
            }
        }
        coEvery { repository.searchGames("second", any(), any()) } coAnswers {
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
            coVerify(exactly = 0) { repository.searchGames("second", any(), any()) }

            awaitItem() // Loading "second"

            advanceTimeBy(350L) // Second query debounce passes
            advanceUntilIdle()
            val secondContent = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), secondContent.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("second", 30, any()) }
    }

    @Test
    fun `cached search results are visible immediately while network search is in flight`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow(sampleGames)
        every { repository.getSearchResultsFlow("witcher") } returns searchFlow
        val searchDeferred = CompletableDeferred<AppResult<Unit>>()
        coEvery { repository.searchGames("witcher", any(), any()) } coAnswers {
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
    fun `search failure retains cached games from Room Flow without showing Error`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow(sampleGames)
        every { repository.getSearchResultsFlow("witcher") } returns searchFlow
        coEvery { repository.searchGames("witcher", any(), any()) } returns AppResult.Error(AppError.NetworkError)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            viewModel.onQueryChanged("witcher")
            runCurrent()
            advanceTimeBy(350L)
            advanceUntilIdle()

            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query change immediately stops displaying previous query content`() = runTest(testDispatcher) {
        val firstSearchFlow = MutableStateFlow(sampleGames)
        val secondSearchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("first") } returns firstSearchFlow
        every { repository.getSearchResultsFlow("second") } returns secondSearchFlow
        coEvery { repository.searchGames("first", any(), any()) } returns AppResult.Success(Unit)
        coEvery { repository.searchGames("second", any(), any()) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("first")
            testScheduler.runCurrent()
            awaitItem() // Intermediate Loading for "first"
            advanceTimeBy(350L)
            advanceUntilIdle()
            val firstContent = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), firstContent.result)

            viewModel.onQueryChanged("second")
            testScheduler.runCurrent()
            val intermediateState = awaitItem()
            assertEquals("second", intermediateState.query)
            assertEquals(SearchResultUiState.Loading, intermediateState.result) // Never shows "first" games!

            advanceTimeBy(350L)
            advanceUntilIdle()
            val secondContent = awaitItem()
            assertEquals(SearchResultUiState.Empty("second"), secondContent.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing query cancels pending debounce and immediately resets state to Idle`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals("", initial.query)
            assertEquals(SearchResultUiState.Idle, initial.result)
            testScheduler.runCurrent()

            viewModel.onQueryChanged("cyberpunk")
            testScheduler.runCurrent()
            awaitItem() // Loading "cyberpunk"

            advanceTimeBy(100L) // Under 300ms debounce
            viewModel.onClearQuery()
            testScheduler.runCurrent()

            val idleState = awaitItem()
            assertEquals("", idleState.query)
            assertEquals(SearchResultUiState.Idle, idleState.result)

            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }
    }

    @Test
    fun `whitespace changes do not trigger duplicate search requests`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("zelda") } returns searchFlow
        coEvery { repository.searchGames("zelda", any(), any()) } coAnswers {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("zelda")
            testScheduler.runCurrent()
            awaitItem() // Loading
            advanceTimeBy(350L)
            advanceUntilIdle()
            awaitItem() // Content

            viewModel.onQueryChanged("  zelda  ")
            advanceTimeBy(350L)
            advanceUntilIdle()

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("zelda", 30, any()) }
    }

    @Test
    fun `search returns Empty status when repository returns empty list`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("nonexistent") } returns searchFlow
        coEvery { repository.searchGames("nonexistent", any(), any()) } returns AppResult.Success(Unit)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("nonexistent")
            testScheduler.runCurrent()
            awaitItem() // Loading

            advanceTimeBy(350L)
            advanceUntilIdle()
            val emptyState = awaitItem()
            assertEquals("nonexistent", emptyState.query)
            assertEquals(SearchResultUiState.Empty("nonexistent"), emptyState.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search returns Error status when repository fails with empty cache`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("error_query") } returns searchFlow
        coEvery { repository.searchGames("error_query", any(), any()) } returns AppResult.Error(AppError.NetworkError)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("error_query")
            testScheduler.runCurrent()
            awaitItem() // Loading

            advanceTimeBy(350L)
            advanceUntilIdle()
            val errorState = awaitItem()
            assertEquals("error_query", errorState.query)
            assertEquals(SearchResultUiState.Error(AppError.NetworkError), errorState.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-executes search immediately without debounce`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("retry_query") } returns searchFlow
        coEvery { repository.searchGames("retry_query", any(), any()) } returns AppResult.Error(
            AppError.NetworkError
        ) andThenAnswer {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("retry_query")
            testScheduler.runCurrent()
            awaitItem() // Loading
            advanceTimeBy(350L)
            advanceUntilIdle()
            val errorState = awaitItem()
            assertEquals(SearchResultUiState.Error(AppError.NetworkError), errorState.result)

            viewModel.retry()
            testScheduler.runCurrent()
            val loadingState = awaitItem() // Retry emits Loading immediately
            assertEquals(SearchResultUiState.Loading, loadingState.result)

            advanceUntilIdle()
            val successState = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), successState.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { repository.searchGames("retry_query", 30, any()) }
    }


    @Test
    fun `blank retry performs no repository call`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.retry()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }
    }

    @Test
    fun `unsubscribing for more than 5 seconds does not re-trigger search on resubscription`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("witcher") } returns searchFlow
        coEvery { repository.searchGames("witcher", any(), any()) } coAnswers {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()
            viewModel.onQueryChanged("witcher")
            testScheduler.runCurrent()
            awaitItem() // Loading
            advanceTimeBy(350L)
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)
            cancelAndIgnoreRemainingEvents() // Detach subscriber (simulate navigating to Details)
        }

        advanceTimeBy(10_000L) // 10 seconds pass on Details screen

        viewModel.uiState.test {
            val item = awaitItem() // Re-subscribe (simulate navigating back to Search)
            assertEquals(SearchResultUiState.Content(sampleGames), item.result)
            cancelAndIgnoreRemainingEvents()
        }

        // SharingStarted.Lazily keeps pipeline active, preventing redundant search on return
        coVerify(exactly = 1) { repository.searchGames("witcher", 30, any()) }
    }

    @Test
    fun `restored query from SavedStateHandle automatically executes search exactly once`() = runTest(testDispatcher) {
        val searchFlow = MutableStateFlow<List<Game>>(emptyList())
        every { repository.getSearchResultsFlow("restored_game") } returns searchFlow
        coEvery { repository.searchGames("restored_game", any(), any()) } coAnswers {
            searchFlow.value = sampleGames
            AppResult.Success(Unit)
        }
        val savedStateHandle = SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "restored_game"))

        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals("restored_game", initialState.query)
            assertEquals(SearchResultUiState.Loading, initialState.result)

            advanceTimeBy(350L)
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals(SearchResultUiState.Content(sampleGames), contentState.result)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("restored_game", 30, any()) }
    }

    @Test
    fun `query length is clamped by Unicode code points without splitting surrogate pairs`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        // Emoji 😀 is 2 UTF-16 code units but 1 Unicode code point
        val emojiQuery = "😀".repeat(150)

        viewModel.onQueryChanged(emojiQuery)

        val result = viewModel.rawQuery.value
        assertEquals(100, result.codePointCount(0, result.length))
        assertEquals("😀".repeat(100), result)
    }

    @Test
    fun addToWishlist_whenAbsent_callsRepository() = runTest(testDispatcher) {
        val game = Game(id = 11L, name = "Trending Game")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToWishlist(game)
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryRepository.addToWishlist(game) }
        coVerify(exactly = 1) { repository.refreshGameDetails(game.id, force = false) }
        coVerify(exactly = 0) { libraryRepository.setGameStatus(any(), any()) }
    }

    @Test
    fun addToWishlist_onError_setsUserMessage() = runTest(testDispatcher) {
        coEvery { libraryRepository.addToWishlist(any()) } returns
            AppResult.Error(AppError.UnknownError(IllegalStateException("fail")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToWishlist(Game(id = 11L, name = "Trending Game"))
        viewModel.uiState.test {
            val state = awaitItemUntil { it.userMessageRes != null }
            assertEquals(R.string.error_library_update_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onSaveLibraryEntry_delegatesToUpsertUserEdits() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSaveLibraryEntry(11L, LibraryStatus.PLAYING, 8, 12, "fun", true)
        advanceUntilIdle()
        coVerify {
            libraryRepository.upsertUserEdits(11L, LibraryStatus.PLAYING, 8, 12, "fun", true)
        }
    }

    @Test
    fun libraryFlowFailure_exposesFailureAndKeepsSearchContent() = runTest(testDispatcher) {
        every { libraryRepository.getLibraryGamesFlow() } returns flow {
            throw IllegalStateException("room down")
        }
        val searchFlow = MutableStateFlow(sampleGames)
        every { repository.getSearchResultsFlow("witcher") } returns searchFlow
        coEvery { repository.searchGames("witcher", any(), any()) } returns AppResult.Success(Unit)
        val viewModel = createViewModel(SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "witcher")))
        viewModel.uiState.test {
            advanceTimeBy(350L)
            advanceUntilIdle()
            val state = awaitItemUntil {
                it.result is SearchResultUiState.Content && it.librarySnapshot is LibrarySnapshot.Failed
            }
            assertTrue(state.result is SearchResultUiState.Content)
            assertEquals(R.string.error_library_load_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveFailure_keepsEditingGameId() = runTest(testDispatcher) {
        libraryFlow.value = listOf(
            LibraryGame(
                game = Game(id = 11L, name = "Trending Game"),
                entry = LibraryEntry(
                    gameId = 11L,
                    status = LibraryStatus.WISHLIST,
                    addedAtEpochSeconds = 1L,
                    updatedAtEpochSeconds = 1L,
                ),
            ),
        )
        coEvery {
            libraryRepository.upsertUserEdits(any(), any(), any(), any(), any(), any())
        } returns AppResult.Error(AppError.UnknownError(IllegalStateException("fail")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onLibraryCardAction(Game(id = 11L, name = "Trending Game"))
        advanceUntilIdle()
        viewModel.onSaveLibraryEntry(11L, LibraryStatus.PLAYING, 8, 12, "fun", true)
        viewModel.uiState.test {
            val state = awaitItemUntil { it.userMessageRes != null }
            assertEquals(11L, state.editingGameId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SearchUiState>.awaitItemUntil(
        predicate: (SearchUiState) -> Boolean,
    ): SearchUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}

