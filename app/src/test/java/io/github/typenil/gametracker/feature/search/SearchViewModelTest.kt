package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository: GameRepository = mockk()

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

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): SearchViewModel {
        return SearchViewModel(
            gameRepository = repository,
            savedStateHandle = savedStateHandle
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
        coEvery { repository.searchGames("witcher", any(), any()) } returns AppResult.Success(sampleGames)
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
        coEvery { repository.searchGames("witcher", any(), any()) } returns AppResult.Success(sampleGames)
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

        coEvery { repository.searchGames("first", any(), any()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                firstQueryCancelled.set(true)
            }
        }
        coEvery { repository.searchGames("second", any(), any()) } returns AppResult.Success(sampleGames)

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
    fun `query change immediately stops displaying previous query content`() = runTest(testDispatcher) {
        coEvery { repository.searchGames("first", any(), any()) } returns AppResult.Success(sampleGames)
        coEvery { repository.searchGames("second", any(), any()) } returns AppResult.Success(emptyList())

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle
            testScheduler.runCurrent()

            viewModel.onQueryChanged("first")
            testScheduler.runCurrent()
            awaitItem() // Loading "first"
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
        coEvery { repository.searchGames("zelda", any(), any()) } returns AppResult.Success(sampleGames)
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
        coEvery { repository.searchGames("nonexistent", any(), any()) } returns AppResult.Success(emptyList())
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
    fun `search returns Error status when repository fails`() = runTest(testDispatcher) {
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
        coEvery { repository.searchGames("retry_query", any(), any()) } returnsMany listOf(
            AppResult.Error(AppError.NetworkError),
            AppResult.Success(sampleGames)
        )
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
        coEvery { repository.searchGames("witcher", any(), any()) } returns AppResult.Success(sampleGames)
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
        val savedStateHandle = SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "restored_game"))
        coEvery { repository.searchGames("restored_game", any(), any()) } returns AppResult.Success(sampleGames)

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
}
