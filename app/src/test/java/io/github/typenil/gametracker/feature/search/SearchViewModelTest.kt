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
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state with empty SavedStateHandle is Idle and triggers no repository calls`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals("", item.query)
            assertEquals(SearchStatus.Idle, item.status)
            assertTrue(item.games.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }
    }

    @Test
    fun `query does not trigger search before 300 ms debounce`() = runTest(testDispatcher) {
        coEvery { repository.searchGames("witcher", any(), any()) } returns AppResult.Success(sampleGames)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem() // Initial Idle
            assertEquals(SearchStatus.Idle, initial.status)

            viewModel.onQueryChanged("witcher")

            advanceTimeBy(250L)
            coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }

            advanceTimeBy(100L) // Exceeds 300ms debounce
            val loadingState = awaitItem()
            assertEquals("witcher", loadingState.query)
            assertEquals(SearchStatus.Loading, loadingState.status)

            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchStatus.Content, contentState.status)
            assertEquals(sampleGames, contentState.games)

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

            viewModel.onQueryChanged("w")
            advanceTimeBy(100L)
            viewModel.onQueryChanged("wi")
            advanceTimeBy(100L)
            viewModel.onQueryChanged("wit")
            advanceTimeBy(100L)
            viewModel.onQueryChanged("witcher")
            advanceTimeBy(350L)

            val loadingState = awaitItem()
            assertEquals("witcher", loadingState.query)
            assertEquals(SearchStatus.Loading, loadingState.status)

            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals("witcher", contentState.query)
            assertEquals(SearchStatus.Content, contentState.status)
            assertEquals(sampleGames, contentState.games)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("witcher", 30, any()) }
        coVerify(exactly = 0) { repository.searchGames("w", any(), any()) }
        coVerify(exactly = 0) { repository.searchGames("wi", any(), any()) }
        coVerify(exactly = 0) { repository.searchGames("wit", any(), any()) }
    }

    @Test
    fun `clearing query cancels pending debounce and ensures no search occurs`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals("", initial.query)
            assertEquals(SearchStatus.Idle, initial.status)

            viewModel.onQueryChanged("cyberpunk")
            advanceTimeBy(100L) // Under 300ms debounce
            viewModel.onClearQuery()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.query)
            assertEquals(SearchStatus.Idle, viewModel.uiState.value.status)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.searchGames(any(), any(), any()) }
    }

    @Test
    fun `clearing query after search immediately resets state to Idle and clears results`() = runTest(testDispatcher) {
        coEvery { repository.searchGames("cyberpunk", any(), any()) } returns AppResult.Success(sampleGames)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle

            viewModel.onQueryChanged("cyberpunk")
            advanceTimeBy(350L)
            awaitItem() // Loading
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals(SearchStatus.Content, contentState.status)
            assertEquals(sampleGames, contentState.games)

            viewModel.onClearQuery()
            advanceUntilIdle()

            val idleState = awaitItem()
            assertEquals("", idleState.query)
            assertEquals(SearchStatus.Idle, idleState.status)
            assertTrue(idleState.games.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("cyberpunk", 30, any()) }
    }

    @Test
    fun `in-flight search is cancelled when a new query is entered`() = runTest(testDispatcher) {
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

            viewModel.onQueryChanged("first")
            advanceTimeBy(350L) // Triggers first query
            val loadingFirst = awaitItem()
            assertEquals("first", loadingFirst.query)

            viewModel.onQueryChanged("second")
            advanceTimeBy(350L) // Triggers second query, cancelling first
            val loadingSecond = awaitItem()
            assertEquals("second", loadingSecond.query)

            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals("second", contentState.query)
            assertEquals(SearchStatus.Content, contentState.status)

            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(firstQueryCancelled.get())
        coVerify(exactly = 1) { repository.searchGames("second", 30, any()) }
    }

    @Test
    fun `whitespace changes do not trigger duplicate search requests`() = runTest(testDispatcher) {
        coEvery { repository.searchGames("zelda", any(), any()) } returns AppResult.Success(sampleGames)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle

            viewModel.onQueryChanged("zelda")
            advanceTimeBy(350L)
            awaitItem() // Loading
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

            viewModel.onQueryChanged("nonexistent")
            advanceTimeBy(350L)
            awaitItem() // Loading

            advanceUntilIdle()
            val emptyState = awaitItem()
            assertEquals("nonexistent", emptyState.query)
            assertEquals(SearchStatus.Empty, emptyState.status)
            assertTrue(emptyState.games.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search returns Error status when repository fails`() = runTest(testDispatcher) {
        coEvery { repository.searchGames("error_query", any(), any()) } returns AppResult.Error(AppError.NetworkError)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle

            viewModel.onQueryChanged("error_query")
            advanceTimeBy(350L)
            awaitItem() // Loading

            advanceUntilIdle()
            val errorState = awaitItem()
            assertEquals("error_query", errorState.query)
            assertEquals(SearchStatus.Error(AppError.NetworkError), errorState.status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-executes search for the current query`() = runTest(testDispatcher) {
        coEvery { repository.searchGames("retry_query", any(), any()) } returnsMany listOf(
            AppResult.Error(AppError.NetworkError),
            AppResult.Success(sampleGames)
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial Idle

            viewModel.onQueryChanged("retry_query")
            advanceTimeBy(350L)
            awaitItem() // Loading
            advanceUntilIdle()
            val errorState = awaitItem()
            assertEquals(SearchStatus.Error(AppError.NetworkError), errorState.status)

            viewModel.retry()
            advanceUntilIdle()
            awaitItem() // Loading
            val successState = awaitItem()
            assertEquals(SearchStatus.Content, successState.status)
            assertEquals(sampleGames, successState.games)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { repository.searchGames("retry_query", 30, any()) }
    }

    @Test
    fun `restored query from SavedStateHandle automatically executes search`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf(SearchViewModel.KEY_QUERY to "restored_game"))
        coEvery { repository.searchGames("restored_game", any(), any()) } returns AppResult.Success(sampleGames)

        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals("restored_game", initialState.query)

            advanceTimeBy(350L)
            advanceUntilIdle()
            val contentState = awaitItem()
            assertEquals(SearchStatus.Content, contentState.status)
            assertEquals(sampleGames, contentState.games)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.searchGames("restored_game", 30, any()) }
    }

    @Test
    fun `query length is clamped to maximum 100 characters`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val overlyLongQuery = "a".repeat(150)

        viewModel.onQueryChanged(overlyLongQuery)

        assertEquals(100, viewModel.rawQuery.value.length)
        assertEquals("a".repeat(100), viewModel.rawQuery.value)
    }
}
