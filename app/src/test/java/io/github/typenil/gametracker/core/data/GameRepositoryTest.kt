package io.github.typenil.gametracker.core.data

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.model.GameDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryTest {

    private val remoteDataSource: BffRemoteDataSource = mockk()
    private val gameDao: GameDao = mockk(relaxed = true)
    private val searchDao: SearchDao = mockk(relaxed = true)

    private val passThroughTransactionRunner = object : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }

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

    private val sampleGameEntity = GameEntity(
        id = 1L,
        name = "Cyberpunk 2077",
        coverUrl = "https://example.com/cover.jpg",
        rating = 88.0,
        releaseDateEpochSeconds = 1600000000L,
        summary = "Night city RPG",
        genres = listOf("RPG", "Action"),
        platforms = listOf("PC", "PS5"),
        cachedAtEpochSeconds = 1600000000L
    )

    @Before
    fun setUp() {
        coEvery { searchDao.getSearchQuery(any()) } returns null
        every { searchDao.getSearchResultsFlow(any()) } returns flowOf(emptyList())
        every { gameDao.getGameByIdFlow(any()) } returns flowOf(null)
    }

    private fun createRepository(testDispatcher: kotlinx.coroutines.CoroutineDispatcher): DefaultGameRepository {
        return DefaultGameRepository(
            remoteDataSource = remoteDataSource,
            gameDao = gameDao,
            searchDao = searchDao,
            transactionRunner = passThroughTransactionRunner,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `getTopRatedGamesFlow observes searchDao with discover top-rated key and maps to domain`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { searchDao.getSearchResultsFlow(DefaultGameRepository.KEY_DISCOVER_TOP_RATED) } returns flowOf(
            listOf(sampleGameEntity)
        )

        repository.getTopRatedGamesFlow().test {
            val games = awaitItem()
            assertEquals(1, games.size)
            assertEquals("Cyberpunk 2077", games[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshTopRatedGames saves results with discover top-rated key and positions`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.getTopRatedGames(20, 0) } returns listOf(sampleGameDto)

        val result = repository.refreshTopRatedGames(limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { gameDao.upsertGames(match { it.size == 1 && it[0].id == 1L }) }

        val querySlot = slot<SearchQueryEntity>()
        coVerify(exactly = 1) { searchDao.upsertSearchQuery(capture(querySlot)) }
        assertEquals(DefaultGameRepository.KEY_DISCOVER_TOP_RATED, querySlot.captured.query)
        assertEquals(1, querySlot.captured.resultCount)

        coVerify(exactly = 1) { searchDao.deleteSearchResultsForQuery(DefaultGameRepository.KEY_DISCOVER_TOP_RATED) }

        val resultsSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(resultsSlot)) }
        assertEquals(1, resultsSlot.captured.size)
        assertEquals(DefaultGameRepository.KEY_DISCOVER_TOP_RATED, resultsSlot.captured[0].query)
        assertEquals(1L, resultsSlot.captured[0].gameId)
        assertEquals(0, resultsSlot.captured[0].position)
    }

    @Test
    fun `refreshTopRatedGames returns Error without database writes when network fails`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.getTopRatedGames(any(), any()) } throws IOException("Network down")

        val result = repository.refreshTopRatedGames()

        assertTrue(result is AppResult.Error)
        assertEquals(AppError.NetworkError, (result as AppResult.Error).error)
        coVerify(exactly = 0) { gameDao.upsertGames(any()) }
        coVerify(exactly = 0) { searchDao.insertSearchResults(any()) }
    }

    @Test
    fun `getSearchResultsFlow observes searchDao with prefixed key`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { searchDao.getSearchResultsFlow("q:witcher") } returns flowOf(listOf(sampleGameEntity))

        repository.getSearchResultsFlow("  Witcher  ").test {
            val games = awaitItem()
            assertEquals(1, games.size)
            assertEquals("Cyberpunk 2077", games[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSearchResultsFlow returns empty flow when query is blank`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)

        repository.getSearchResultsFlow("   ").test {
            val games = awaitItem()
            assertTrue(games.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { searchDao.getSearchResultsFlow(any()) }
    }

    @Test
    fun `searchGames returns Success without writes when query is blank`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)

        val result = repository.searchGames(query = "   ", limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { remoteDataSource.searchGames(any(), any(), any()) }
        coVerify(exactly = 0) { gameDao.upsertGames(any()) }
        coVerify(exactly = 0) { searchDao.insertSearchResults(any()) }
    }

    @Test
    fun `searchGames saves query with prefix and does not collide with discover key`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.searchGames("discover:top-rated", 20, 0) } returns listOf(
            sampleGameDto.copy(id = 101L, name = "Discovered Game")
        )

        val result = repository.searchGames(query = "discover:top-rated", limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        val querySlot = slot<SearchQueryEntity>()
        coVerify(exactly = 1) { searchDao.upsertSearchQuery(capture(querySlot)) }
        assertEquals("q:discover:top-rated", querySlot.captured.query)

        val resultsSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(resultsSlot)) }
        assertEquals("q:discover:top-rated", resultsSlot.captured[0].query)
        assertEquals(101L, resultsSlot.captured[0].gameId)
        assertEquals(0, resultsSlot.captured[0].position)
    }

    @Test
    fun `searchGames preserves existing query createdAt timestamp on repeated search`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        val existingQueryEntity = SearchQueryEntity(
            query = "q:witcher",
            createdAtEpochSeconds = 1500000000L,
            lastQueriedAtEpochSeconds = 1500000000L,
            resultCount = 1
        )
        coEvery { searchDao.getSearchQuery("q:witcher") } returns existingQueryEntity
        coEvery { remoteDataSource.searchGames("witcher", 20, 0) } returns listOf(sampleGameDto)

        val result = repository.searchGames("witcher", 20, 0)

        assertTrue(result is AppResult.Success)
        val querySlot = slot<SearchQueryEntity>()
        coVerify(exactly = 1) { searchDao.upsertSearchQuery(capture(querySlot)) }
        assertEquals(1500000000L, querySlot.captured.createdAtEpochSeconds)
    }

    @Test
    fun `searchGames returns Error when dataSource throws HttpException`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        val responseBody = """{"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests"}"""
            .toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<String>(429, responseBody))
        coEvery { remoteDataSource.searchGames(any(), any(), any()) } throws httpException

        val result = repository.searchGames("witcher", 20, 0)

        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).error
        assertTrue(error is AppError.HttpError)
        assertEquals(429, (error as AppError.HttpError).statusCode)
        assertEquals("RATE_LIMIT_EXCEEDED", error.errorCode)
        assertEquals("Too many requests", error.message)

        coVerify(exactly = 0) { gameDao.upsertGames(any()) }
        coVerify(exactly = 0) { searchDao.insertSearchResults(any()) }
    }

    @Test
    fun `getGameDetailsFlow observes gameDao and maps to domain`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { gameDao.getGameByIdFlow(1L) } returns flowOf(sampleGameEntity)

        repository.getGameDetailsFlow(1L).test {
            val game = awaitItem()
            assertEquals(1L, game?.id)
            assertEquals("Cyberpunk 2077", game?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshGameDetails writes to gameDao when game is found`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.getGameDetails(1L) } returns sampleGameDto

        val result = repository.refreshGameDetails(1L)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { gameDao.upsertGame(match { it.id == 1L && it.name == "Cyberpunk 2077" }) }
    }

    @Test
    fun `clearStaleCache delegates to gameDao deleteStaleUnsavedGames`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { gameDao.deleteStaleUnsavedGames(1000L) } returns 5

        val deletedCount = repository.clearStaleCache(1000L)

        assertEquals(5, deletedCount)
        coVerify(exactly = 1) { gameDao.deleteStaleUnsavedGames(1000L) }
    }
}

