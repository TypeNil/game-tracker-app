package io.github.typenil.gametracker.core.data

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.model.GameDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryTest {

    private val remoteDataSource: BffRemoteDataSource = mockk()

    private val sampleGameDto = GameDto(
        id = 1L,
        name = "Cyberpunk 2077",
        coverUrl = "https://example.com/cover.jpg",
        rating = 88.0,
        releaseDateEpochSeconds = 1600000000L,
        summary = "Night city RPG",
        genres = listOf("RPG", "Action"),
        platforms = listOf("PC", "PS5")
    )

    @Test
    fun `getTopRatedGames returns Success when dataSource succeeds`() = runTest {
        val repository = DefaultGameRepository(
            remoteDataSource = remoteDataSource,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        coEvery { remoteDataSource.getTopRatedGames(20, 0) } returns listOf(sampleGameDto)

        val result = repository.getTopRatedGames(limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        val games = (result as AppResult.Success).data
        assertEquals(1, games.size)
        assertEquals("Cyberpunk 2077", games[0].name)
    }

    @Test
    fun `getTopRatedGames returns Error when dataSource throws IOException`() = runTest {
        val repository = DefaultGameRepository(
            remoteDataSource = remoteDataSource,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        coEvery { remoteDataSource.getTopRatedGames(any(), any()) } throws IOException("Network down")

        val result = repository.getTopRatedGames()

        assertTrue(result is AppResult.Error)
        assertEquals(AppError.NetworkError, (result as AppResult.Error).error)
    }

    @Test
    fun `searchGames reactive stream debounces and filters blank queries`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = DefaultGameRepository(
            remoteDataSource = remoteDataSource,
            ioDispatcher = testDispatcher
        )
        coEvery { remoteDataSource.searchGames("witcher", 20, 0) } returns listOf(
            sampleGameDto.copy(name = "The Witcher 3")
        )

        val queryFlow = flow {
            emit("   ")
            delay(350L)
            emit("wit")
            delay(100L) // Under debounce threshold
            emit("witcher")
            delay(350L)
        }

        repository.searchGames(queryFlow = queryFlow, limit = 20).test {
            val blankResult = awaitItem()
            assertTrue(blankResult is AppResult.Success)
            assertTrue((blankResult as AppResult.Success).data.isEmpty())

            val searchResult = awaitItem()
            assertTrue(searchResult is AppResult.Success)
            val games = (searchResult as AppResult.Success).data
            assertEquals(1, games.size)
            assertEquals("The Witcher 3", games[0].name)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { remoteDataSource.searchGames("witcher", 20, 0) }
        coVerify(exactly = 0) { remoteDataSource.searchGames("wit", any(), any()) }
    }

    @Test
    fun `getGameDetails returns Success when game is found`() = runTest {
        val repository = DefaultGameRepository(
            remoteDataSource = remoteDataSource,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        coEvery { remoteDataSource.getGameDetails(1L) } returns sampleGameDto

        val result = repository.getGameDetails(1L)

        assertTrue(result is AppResult.Success)
        assertEquals("Cyberpunk 2077", (result as AppResult.Success).data.name)
    }
}
