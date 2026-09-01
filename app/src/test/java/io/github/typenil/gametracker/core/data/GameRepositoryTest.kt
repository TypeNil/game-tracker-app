package io.github.typenil.gametracker.core.data

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.paging.GameQueryKey
import io.github.typenil.gametracker.core.data.paging.DiscoverRailKeys
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.model.CompanyDto
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.ReleaseDateDto
import io.github.typenil.gametracker.core.network.model.SimilarGameDto
import io.github.typenil.gametracker.core.network.model.VideoDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val gameDetailsDao: GameDetailsDao = mockk(relaxed = true)
    private val searchDao: SearchDao = mockk(relaxed = true)
    private val searchHistoryDao: io.github.typenil.gametracker.core.database.dao.SearchHistoryDao = mockk(relaxed = true)
    private val remoteKeyDao: RemoteKeyDao = mockk(relaxed = true)
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

    private val sampleDetailsDto = GameDetailsDto(
        id = 1L,
        name = "Cyberpunk 2077",
        coverUrl = "https://example.com/cover.jpg",
        rating = 88.0,
        releaseDateEpochSeconds = 1600000000L,
        summary = "Night city RPG",
        genres = listOf("RPG"),
        platforms = listOf("PC", "PS5"),
        url = "https://www.igdb.com/games/cyberpunk-2077",
        totalRating = 86.5,
        totalRatingCount = 2187L,
        themes = listOf("Science fiction"),
        gameModes = listOf("Single player"),
        releaseDates = listOf(ReleaseDateDto(platform = "PC", dateEpochSeconds = 1600000000L, year = 2020)),
        companies = listOf(CompanyDto(name = "CD Projekt RED", isDeveloper = true)),
        screenshots = listOf("https://example.com/shot.jpg"),
        videos = listOf(VideoDto(videoId = "abc123", name = "Trailer")),
        similarGames = listOf(SimilarGameDto(id = 2L, name = "The Witcher 3", totalRating = 92.7))
    )

    private val sampleDetailsEntity = GameDetailsEntity(
        gameId = 1L,
        name = "Cyberpunk 2077",
        coverUrl = "https://example.com/cover.jpg",
        rating = 88.0,
        totalRating = 86.5,
        totalRatingCount = 2187L,
        releaseDateEpochSeconds = 1600000000L,
        summary = "Night city RPG",
        url = "https://www.igdb.com/games/cyberpunk-2077",
        genres = listOf("RPG"),
        themes = listOf("Science fiction"),
        gameModes = listOf("Single player"),
        platforms = listOf("PC", "PS5"),
        releaseDates = emptyList(),
        companies = emptyList(),
        screenshots = listOf("https://example.com/shot.jpg"),
        videos = emptyList(),
        similarGames = emptyList(),
        cachedAtEpochSeconds = TEST_NOW_SECONDS
    )

    @Before
    fun setUp() {
        coEvery { searchDao.getSearchQuery(any()) } returns null
        coEvery { searchDao.countSearchResultsForQuery(any()) } returns 0
        every { searchDao.getSearchResultsFlow(any()) } returns flowOf(emptyList())
        coEvery { remoteKeyDao.getRemoteKey(any()) } returns null
        every { gameDao.getGameByIdFlow(any()) } returns flowOf(null)
        every { gameDetailsDao.getGameDetailsFlow(any()) } returns flowOf(null)
        coEvery { gameDetailsDao.getGameDetails(any()) } returns null
    }

    companion object {
        private const val TEST_NOW_SECONDS = 1_600_000_000L
    }

    private fun createRepository(
        testDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        nowEpochSeconds: () -> Long = { TEST_NOW_SECONDS }
    ): DefaultGameRepository {
        return DefaultGameRepository(
            remoteDataSource = remoteDataSource,
            gameDao = gameDao,
            gameDetailsDao = gameDetailsDao,
            searchDao = searchDao,
            searchHistoryDao = searchHistoryDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = passThroughTransactionRunner,
            ioDispatcher = testDispatcher,
            nowEpochSeconds = nowEpochSeconds
        )
    }

    @Test
    fun `getTopRatedGamesFlow observes searchDao with discover top-rated key and maps to domain`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { searchDao.getSearchResultsFlow(GameQueryKey.KEY_DISCOVER_TOP_RATED) } returns flowOf(
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
    fun `getTrendingGamesFlow observes searchDao with discover trending key`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { searchDao.getSearchResultsFlow(GameQueryKey.KEY_DISCOVER_TRENDING) } returns flowOf(
            listOf(sampleGameEntity)
        )

        val games = repository.getTrendingGamesFlow().first()

        assertEquals(1, games.size)
        assertEquals(1L, games[0].id)
        assertEquals("Cyberpunk 2077", games[0].name)
    }

    @Test
    fun `refreshTrendingGames writes discover trending key`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher, nowEpochSeconds = { 1600000000L })
        coEvery { remoteDataSource.getTrendingGames(20, 0) } returns listOf(sampleGameDto)

        val result = repository.refreshTrendingGames(limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        val querySlot = slot<SearchQueryEntity>()
        coVerify(exactly = 1) { searchDao.upsertSearchQuery(capture(querySlot)) }
        assertEquals(GameQueryKey.KEY_DISCOVER_TRENDING, querySlot.captured.query)
        coVerify(exactly = 1) { searchDao.deleteSearchResultsForQuery(GameQueryKey.KEY_DISCOVER_TRENDING) }
    }

    @Test
    fun `refreshTrendingGames append does not delete existing page`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher, nowEpochSeconds = { 1600000000L })
        val pageTwo = sampleGameDto.copy(id = 2L, name = "Second")
        coEvery { remoteDataSource.getTrendingGames(20, 20) } returns listOf(pageTwo)

        val result = repository.refreshTrendingGames(limit = 20, offset = 20, append = true)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { searchDao.deleteSearchResultsForQuery(any()) }
        val resultsSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(resultsSlot)) }
        assertEquals(GameQueryKey.KEY_DISCOVER_TRENDING, resultsSlot.captured.single().query)
        assertEquals(2L, resultsSlot.captured.single().gameId)
        assertEquals(20, resultsSlot.captured.single().position)
    }

    @Test
    fun `refreshPopular appends rail page without deleting prior positions`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher, nowEpochSeconds = { TEST_NOW_SECONDS })
        val page = io.github.typenil.gametracker.core.network.model.GamePageDto(
            items = listOf(sampleGameDto.copy(id = 2L, name = "Second")),
            nextOffset = 40,
            endReached = false,
        )
        coEvery { remoteDataSource.getPopularPage("playing", 20, 20) } returns page

        val result = repository.refreshPopular("playing", 20, 20, append = true)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { searchDao.deleteSearchResultsForQuery(GameQueryKey.popular("playing")) }
        coVerify(exactly = 1) {
            searchDao.deleteSearchResultsFromPosition(GameQueryKey.popular("playing"), 20)
        }
        coVerify(exactly = 1) { remoteKeyDao.upsert(match { it.nextOffset == 40 }) }
    }

    @Test
    fun `getRecommendationCandidates maps remote DTOs without Room writes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery {
            remoteDataSource.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        } returns listOf(
            io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto(
                id = 99L,
                name = "Candidate",
                coverUrl = null,
                rating = 80.0,
                ratingCount = 10L,
                releaseDateEpochSeconds = null,
                summary = null,
                genres = listOf("RPG"),
                themes = emptyList(),
                platforms = listOf("PC"),
                similarToGameIds = emptyList(),
            )
        )

        val result = repository.getRecommendationCandidates(genres = listOf("RPG"))

        assertTrue(result is AppResult.Success)
        assertEquals(99L, (result as AppResult.Success).data.single().gameId)
        coVerify(exactly = 0) { gameDao.upsertGames(any()) }
    }


    @Test
    fun `refreshTopRatedGames saves results with discover top-rated key and positions and updates remoteKey`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher, nowEpochSeconds = { 1600000000L })
        coEvery { remoteDataSource.getTopRatedGames(20, 0) } returns listOf(sampleGameDto)

        val result = repository.refreshTopRatedGames(limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { gameDao.upsertGames(match { it.size == 1 && it[0].id == 1L }) }

        val querySlot = slot<SearchQueryEntity>()
        coVerify(exactly = 1) { searchDao.upsertSearchQuery(capture(querySlot)) }
        assertEquals(GameQueryKey.KEY_DISCOVER_TOP_RATED, querySlot.captured.query)
        assertEquals(1, querySlot.captured.resultCount)

        coVerify(exactly = 1) { searchDao.deleteSearchResultsForQuery(GameQueryKey.KEY_DISCOVER_TOP_RATED) }

        val resultsSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(resultsSlot)) }
        assertEquals(1, resultsSlot.captured.size)
        assertEquals(GameQueryKey.KEY_DISCOVER_TOP_RATED, resultsSlot.captured[0].query)
        assertEquals(1L, resultsSlot.captured[0].gameId)
        assertEquals(0, resultsSlot.captured[0].position)

        val remoteKeySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(remoteKeySlot)) }
        assertEquals(GameQueryKey.KEY_DISCOVER_TOP_RATED, remoteKeySlot.captured.queryKey)
        // 1 item returned < limit 20 -> nextOffset is null (end of list)
        assertEquals(null, remoteKeySlot.captured.nextOffset)
        assertEquals(1600000000L, remoteKeySlot.captured.lastUpdatedEpochSeconds)

        coVerify(exactly = 1) { searchDao.deleteStaleSearchQueries(any(), any()) }
        coVerify(exactly = 1) { remoteKeyDao.deleteStaleRemoteKeys(any(), any()) }
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
        coVerify(exactly = 0) { remoteKeyDao.upsert(any()) }
    }

    @Test
    fun `getSearchResultsFlow observes searchDao with prefixed key`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { searchDao.getSearchResultsFlow(GameQueryKey.search("Witcher")) } returns flowOf(listOf(sampleGameEntity))

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
    fun `getPagedSearchResults returns empty PagingData flow when query is blank`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)

        val flow = repository.getPagedSearchResults("   ")
        val item = flow.first()
        assertNotNull(item)
        coVerify(exactly = 0) { remoteDataSource.searchGames(any(), any(), any()) }
        coVerify(exactly = 0) { searchDao.getSearchResultsPagingSource(any()) }
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
        coVerify(exactly = 0) { remoteKeyDao.upsert(any()) }
    }

    @Test
    fun `searchGames saves query with prefix and does not collide with discover key`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.searchGames(query = "discover:top-rated", limit = 20, offset = 0) } returns listOf(
            sampleGameDto.copy(id = 101L, name = "Discovered Game")
        )

        val result = repository.searchGames(query = "discover:top-rated", limit = 20, offset = 0)

        assertTrue(result is AppResult.Success)
        val querySlot = slot<SearchQueryEntity>()
        coVerify(exactly = 1) { searchDao.upsertSearchQuery(capture(querySlot)) }
        assertEquals(GameQueryKey.search("discover:top-rated"), querySlot.captured.query)

        val resultsSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(resultsSlot)) }
        assertEquals(GameQueryKey.search("discover:top-rated"), resultsSlot.captured[0].query)
        assertEquals(101L, resultsSlot.captured[0].gameId)
        assertEquals(0, resultsSlot.captured[0].position)

        val remoteKeySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(remoteKeySlot)) }
        assertEquals(GameQueryKey.search("discover:top-rated"), remoteKeySlot.captured.queryKey)
    }

    @Test
    fun `searchGames preserves existing query createdAt timestamp on repeated search`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        val expectedKey = GameQueryKey.search("witcher")
        val existingQueryEntity = SearchQueryEntity(
            query = expectedKey,
            createdAtEpochSeconds = 1500000000L,
            lastQueriedAtEpochSeconds = 1500000000L,
            resultCount = 1
        )
        coEvery { searchDao.getSearchQuery(expectedKey) } returns existingQueryEntity
        coEvery { remoteDataSource.searchGames(query = "witcher", limit = 20, offset = 0) } returns listOf(sampleGameDto)
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
        coEvery { remoteDataSource.searchGames(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws httpException
        val result = repository.searchGames("witcher", 20, 0)

        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).error
        assertTrue(error is AppError.HttpError)
        assertEquals(429, (error as AppError.HttpError).statusCode)
        assertEquals("RATE_LIMIT_EXCEEDED", error.errorCode)
        assertEquals("Too many requests", error.message)

        coVerify(exactly = 0) { gameDao.upsertGames(any()) }
        coVerify(exactly = 0) { searchDao.insertSearchResults(any()) }
        coVerify(exactly = 0) { remoteKeyDao.upsert(any()) }
    }

    @Test
    fun `getGameDetailsFlow emits full details when details row is present`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { gameDetailsDao.getGameDetailsFlow(1L) } returns flowOf(sampleDetailsEntity)

        repository.getGameDetailsFlow(1L).test {
            val details = awaitItem()
            assertEquals(1L, details?.id)
            assertEquals("Cyberpunk 2077", details?.name)
            assertEquals(86.5, details?.totalRating ?: 0.0, 0.001)
            assertEquals(listOf("Science fiction"), details?.themes)
            assertEquals(0, details?.releaseDates?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getGameDetailsFlow falls back to catalog skeleton when only games row exists`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { gameDao.getGameByIdFlow(1L) } returns flowOf(sampleGameEntity)

        repository.getGameDetailsFlow(1L).test {
            val skeleton = awaitItem()
            assertEquals(1L, skeleton?.id)
            assertEquals("Cyberpunk 2077", skeleton?.name)
            // Skeleton keeps the catalog critic rating so the badge does not disappear
            assertEquals(88.0, skeleton?.rating ?: 0.0, 0.001)
            assertNull(skeleton?.totalRating)
            assertTrue(skeleton?.themes.isNullOrEmpty())
            assertTrue(skeleton?.screenshots.isNullOrEmpty())
            assertTrue(skeleton?.similarGames.isNullOrEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getGameDetailsFlow emits null when neither details nor catalog row exists`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)

        repository.getGameDetailsFlow(1L).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshGameDetails writes parent-first slim catalog row and details row`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.getGameDetails(1L) } returns sampleDetailsDto

        val result = repository.refreshGameDetails(1L)

        assertTrue(result is AppResult.Success)
        // Slim catalog projection keeps the critic rating, never the aggregate
        coVerify(exactly = 1) {
            gameDao.upsertGame(match { it.id == 1L && it.rating == 88.0 && it.name == "Cyberpunk 2077" })
        }
        val detailsSlot = slot<GameDetailsEntity>()
        coVerify(exactly = 1) { gameDetailsDao.upsertDetails(capture(detailsSlot)) }
        assertEquals(1L, detailsSlot.captured.gameId)
        assertEquals(86.5, detailsSlot.captured.totalRating ?: 0.0, 0.001)
        assertEquals(TEST_NOW_SECONDS, detailsSlot.captured.cachedAtEpochSeconds)
        coVerify(exactly = 1) { gameDetailsDao.deleteStaleDetails(any()) }
    }

    @Test
    fun `refreshGameDetails skips network when cached details are fresh`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { gameDetailsDao.getGameDetails(1L) } returns sampleDetailsEntity.copy(
            cachedAtEpochSeconds = TEST_NOW_SECONDS - 100L
        )

        val result = repository.refreshGameDetails(1L)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { remoteDataSource.getGameDetails(any()) }
        coVerify(exactly = 0) { gameDetailsDao.upsertDetails(any()) }
        coVerify(exactly = 0) { gameDao.upsertGame(any()) }
    }

    @Test
    fun `refreshGameDetails refetches when cached details exceed TTL`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { gameDetailsDao.getGameDetails(1L) } returns sampleDetailsEntity.copy(
            cachedAtEpochSeconds = TEST_NOW_SECONDS - DefaultGameRepository.DETAILS_TTL_SECONDS - 10L
        )
        coEvery { remoteDataSource.getGameDetails(1L) } returns sampleDetailsDto

        val result = repository.refreshGameDetails(1L)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { remoteDataSource.getGameDetails(1L) }
        coVerify(exactly = 1) { gameDetailsDao.upsertDetails(any()) }
    }

    @Test
    fun `refreshGameDetails with force bypasses the TTL gate`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { gameDetailsDao.getGameDetails(1L) } returns sampleDetailsEntity.copy(
            cachedAtEpochSeconds = TEST_NOW_SECONDS - 100L
        )
        coEvery { remoteDataSource.getGameDetails(1L) } returns sampleDetailsDto

        val result = repository.refreshGameDetails(1L, force = true)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { remoteDataSource.getGameDetails(1L) }
        coVerify(exactly = 1) { gameDetailsDao.upsertDetails(any()) }
    }

    @Test
    fun `refreshGameDetails returns Error without writes when network fails`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.getGameDetails(1L) } throws IOException("Network down")

        val result = repository.refreshGameDetails(1L)

        assertTrue(result is AppResult.Error)
        assertEquals(AppError.NetworkError, (result as AppResult.Error).error)
        coVerify(exactly = 0) { gameDao.upsertGame(any()) }
        coVerify(exactly = 0) { gameDetailsDao.upsertDetails(any()) }
    }

    @Test
    fun `clearStaleCache cleans up stale search queries, remote keys and games`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fixedNow = 1_000_000L
        val repository = createRepository(testDispatcher, nowEpochSeconds = { fixedNow })
        coEvery { searchDao.deleteStaleSearchQueries(any(), any()) } returns 3
        coEvery { remoteKeyDao.deleteStaleRemoteKeys(any(), any()) } returns 3
        coEvery { gameDao.deleteStaleUnsavedGames(any()) } returns 5

        val deletedGamesCount = repository.clearStaleCache(500_000L)

        assertEquals(5, deletedGamesCount)
        val expectedQueryCutoff = fixedNow - GameQueryKey.SEARCH_TTL_SECONDS
        val expectedKeys = listOf(
            GameQueryKey.KEY_DISCOVER_TOP_RATED,
            GameQueryKey.KEY_DISCOVER_TRENDING,
        ) + DiscoverRailKeys.all()
        coVerify(exactly = 1) {
            searchDao.deleteStaleSearchQueries(expectedQueryCutoff, expectedKeys)
        }
        coVerify(exactly = 1) {
            remoteKeyDao.deleteStaleRemoteKeys(expectedQueryCutoff, expectedKeys)
        }
        coVerify(exactly = 1) { gameDao.deleteStaleUnsavedGames(500_000L) }
        coVerify(exactly = 1) { gameDetailsDao.deleteStaleDetails(500_000L) }
    }

    @Test
    fun `searchGames records user search history on non-blank query`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        coEvery { remoteDataSource.searchGames(query = "Elden Ring", limit = 20, offset = 0) } returns listOf(sampleGameDto)

        val result = repository.searchGames("Elden Ring", 20, 0)
        assertTrue(result is AppResult.Success)

        val historySlot = slot<io.github.typenil.gametracker.core.database.entity.SearchHistoryEntity>()
        coVerify(exactly = 1) { searchHistoryDao.upsertSearchHistory(capture(historySlot)) }
        assertEquals("elden ring", historySlot.captured.normalizedQuery)
        assertEquals("Elden Ring", historySlot.captured.displayQuery)
    }

    @Test
    fun `searchGames does not record user search history on blank query with filters`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        val query = io.github.typenil.gametracker.core.model.GameSearchQuery(
            query = "",
            genres = listOf("RPG"),
        )
        coEvery { remoteDataSource.searchGames(query = "", genres = listOf("RPG"), limit = 20, offset = 0) } returns listOf(sampleGameDto)

        val result = repository.searchGames(query, 20, 0)
        assertTrue(result is AppResult.Success)

        coVerify(exactly = 0) { searchHistoryDao.upsertSearchHistory(any()) }
    }

    @Test
    fun `getRecentSearchQueriesFlow observes searchHistoryDao`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(testDispatcher)
        every { searchHistoryDao.observeRecentSearchQueries(10) } returns flowOf(listOf("Zelda", "Witcher"))

        repository.getRecentSearchQueriesFlow(10).test {
            val history = awaitItem()
            assertEquals(listOf("Zelda", "Witcher"), history)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
