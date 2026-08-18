package io.github.typenil.gametracker.feature.discover

import app.cash.turbine.test
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: GameRepository = mockk()

    private val sampleGames = listOf(
        Game(
            id = 1L,
            name = "Elden Ring",
            rating = 96.0
        )
    )

    @Test
    fun `init emits Success when repository succeeds`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Success(sampleGames)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        viewModel.uiState.test {
            val item = awaitItem()
            assertFalse(item.isLoading)
            assertEquals(sampleGames, item.games)
            assertNull(item.error)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getTopRatedGames() }
    }

    @Test
    fun `init emits Error when repository fails on initial load`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Error(AppError.NetworkError)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        viewModel.uiState.test {
            val item = awaitItem()
            assertFalse(item.isLoading)
            assertTrue(item.games.isEmpty())
            assertEquals(AppError.NetworkError, item.error)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getTopRatedGames() }
    }

    @Test
    fun `refresh reloads data and updates games list`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Success(sampleGames)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        val updatedGames = sampleGames + Game(id = 2L, name = "Baldur's Gate 3", rating = 97.0)
        coEvery { repository.getTopRatedGames() } returns AppResult.Success(updatedGames)

        viewModel.refresh()

        viewModel.uiState.test {
            val item = awaitItem()
            assertFalse(item.isRefreshing)
            assertEquals(updatedGames, item.games)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { repository.getTopRatedGames() }
    }

    @Test
    fun `refresh failure with existing data retains games and sets userMessageRes`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Success(sampleGames)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        coEvery { repository.getTopRatedGames() } returns AppResult.Error(AppError.NetworkError)

        viewModel.refresh()

        viewModel.uiState.test {
            val item = awaitItem()
            assertFalse(item.isRefreshing)
            assertEquals(sampleGames, item.games) // Preserves existing data
            assertEquals(R.string.error_refresh_failed, item.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onUserMessageShown()

        viewModel.uiState.test {
            val item = awaitItem()
            assertNull(item.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry reloads data after error state`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Error(AppError.NetworkError)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        coEvery { repository.getTopRatedGames() } returns AppResult.Success(sampleGames)

        viewModel.retry()

        viewModel.uiState.test {
            val item = awaitItem()
            assertFalse(item.isLoading)
            assertEquals(sampleGames, item.games)
            assertNull(item.error)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { repository.getTopRatedGames() }
    }
}
