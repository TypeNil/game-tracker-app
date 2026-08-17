package io.github.typenil.gametracker.feature.discover

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            assertTrue(item is DiscoverUiState.Success)
            assertEquals(sampleGames, (item as DiscoverUiState.Success).games)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getTopRatedGames() }
    }

    @Test
    fun `init emits Error when repository fails`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Error(AppError.NetworkError)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue(item is DiscoverUiState.Error)
            assertEquals(AppError.NetworkError, (item as DiscoverUiState.Error).error)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.getTopRatedGames() }
    }

    @Test
    fun `refresh reloads data and updates state`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Success(sampleGames)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        val updatedGames = sampleGames + Game(id = 2L, name = "Baldur's Gate 3", rating = 97.0)
        coEvery { repository.getTopRatedGames() } returns AppResult.Success(updatedGames)

        viewModel.refresh()

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue(item is DiscoverUiState.Success)
            assertEquals(updatedGames, (item as DiscoverUiState.Success).games)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { repository.getTopRatedGames() }
    }

    @Test
    fun `retry reloads data after error state`() = runTest {
        coEvery { repository.getTopRatedGames() } returns AppResult.Error(AppError.NetworkError)

        val viewModel = DiscoverViewModel(gameRepository = repository)

        coEvery { repository.getTopRatedGames() } returns AppResult.Success(sampleGames)

        viewModel.retry()

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue(item is DiscoverUiState.Success)
            assertEquals(sampleGames, (item as DiscoverUiState.Success).games)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { repository.getTopRatedGames() }
    }
}
