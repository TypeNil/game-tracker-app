package io.github.typenil.gametracker.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppErrorException
import io.github.typenil.gametracker.core.model.Game
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class GamesRemoteMediatorTest {

    private val gameDao: GameDao = mockk(relaxed = true)
    private val searchDao: SearchDao = mockk(relaxed = true)
    private val remoteKeyDao: RemoteKeyDao = mockk(relaxed = true)
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }

    private val sampleGame1 = Game(
        id = 1L,
        name = "Cyberpunk 2077",
        coverUrl = "https://example.com/1.jpg",
        rating = 88.0,
        releaseDateEpochSeconds = 1600000000L,
        summary = "Night City",
        genres = listOf("RPG"),
        platforms = listOf("PC")
    )

    private val sampleGame2 = Game(
        id = 2L,
        name = "The Witcher 3",
        coverUrl = "https://example.com/2.jpg",
        rating = 95.0,
        releaseDateEpochSeconds = 1430000000L,
        summary = "Geralt RPG",
        genres = listOf("RPG"),
        platforms = listOf("PC")
    )

    private val testPagingConfig = PagingConfig(
        pageSize = 20,
        prefetchDistance = 5,
        enablePlaceholders = false,
        initialLoadSize = 20
    )

    private val testPagingState = PagingState<Int, GameEntity>(
        pages = listOf(PagingSource.LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)),
        anchorPosition = null,
        config = testPagingConfig,
        leadingPlaceholderCount = 0
    )

    @Before
    fun setUp() {
        coEvery { searchDao.getSearchQuery(any()) } returns null
        coEvery { searchDao.countSearchResultsForQuery(any()) } returns 0
        coEvery { remoteKeyDao.getRemoteKey(any()) } returns null
    }

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when remoteKey is fresh and local rows exist`() = runTest {
        val now = 10_000L
        val ttl = 3600L
        val key = GameQueryKey.KEY_DISCOVER_TOP_RATED
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = now - 100
        )
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 20
        coEvery { searchDao.getSearchQuery(key) } returns SearchQueryEntity(
            query = key,
            createdAtEpochSeconds = now - 400,
            lastQueriedAtEpochSeconds = now - 100,
            resultCount = 20,
        )

        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = ttl,
            fetcher = { _, _ -> emptyList() },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, result)
    }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when remoteKey is missing`() = runTest {
        val now = 10_000L
        val key = GameQueryKey.KEY_DISCOVER_TOP_RATED
        coEvery { remoteKeyDao.getRemoteKey(key) } returns null
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 20

        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ -> emptyList() },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, result)
    }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when remoteKey is expired`() = runTest {
        val now = 10_000L
        val ttl = 3600L
        val key = GameQueryKey.KEY_DISCOVER_TOP_RATED
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = now - 4000
        )
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 20
        coEvery { searchDao.getSearchQuery(key) } returns SearchQueryEntity(
            query = key,
            createdAtEpochSeconds = now - 400,
            lastQueriedAtEpochSeconds = now - 4000,
            resultCount = 20,
        )

        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = ttl,
            fetcher = { _, _ -> emptyList() },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, result)
    }

    @Test
    fun `fresh terminal empty cache skips initial refresh`() = runTest {
        val now = 10_000L
        val key = GameQueryKey.KEY_DISCOVER_TOP_RATED
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = null,
            lastUpdatedEpochSeconds = now - 100
        )
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 0
        coEvery { searchDao.getSearchQuery(key) } returns SearchQueryEntity(
            query = key,
            createdAtEpochSeconds = now - 100,
            lastQueriedAtEpochSeconds = now - 100,
            resultCount = 0,
        )

        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ -> emptyList() },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, result)
    }

    @Test
    fun `missing rows with nonzero metadata launches initial refresh`() = runTest {
        val now = 10_000L
        val key = GameQueryKey.KEY_DISCOVER_TOP_RATED
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = now - 100
        )
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 0
        coEvery { searchDao.getSearchQuery(key) } returns SearchQueryEntity(
            query = key,
            createdAtEpochSeconds = now - 100,
            lastQueriedAtEpochSeconds = now - 100,
            resultCount = 20,
        )

        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ -> emptyList() },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, result)
    }

    @Test
    fun `future remote key timestamp launches initial refresh`() = runTest {
        val now = 10_000L
        val key = GameQueryKey.KEY_DISCOVER_TOP_RATED
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = now + 500
        )
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 20
        coEvery { searchDao.getSearchQuery(key) } returns SearchQueryEntity(
            query = key,
            createdAtEpochSeconds = now + 400,
            lastQueriedAtEpochSeconds = now + 500,
            resultCount = 20,
        )

        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ -> emptyList() },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.initialize()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, result)
    }

    @Test
    fun `load PREPEND immediately returns Success end true without fetch`() = runTest {
        var fetcherCalled = false
        val mediator = GamesRemoteMediator(
            queryKey = "q:witcher",
            ttlSeconds = 900L,
            fetcher = { _, _ ->
                fetcherCalled = true
                emptyList()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.PREPEND, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(false, fetcherCalled)
    }

    @Test
    fun `load REFRESH fetches offset 0 and saves continuous positions starting from 0`() = runTest {
        val now = 5000L
        val key = "discover:top-rated"
        val remoteGames = List(20) { index ->
            sampleGame1.copy(id = index + 1L, name = "Game ${index + 1}")
        }
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { limit, offset ->
                assertEquals(20, limit)
                assertEquals(0, offset)
                remoteGames
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.load(LoadType.REFRESH, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        coVerify(exactly = 1) { gameDao.upsertGames(match { it.size == 20 }) }
        coVerify(exactly = 1) { searchDao.deleteSearchResultsForQuery(key) }

        val crossRefSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(crossRefSlot)) }
        assertEquals(20, crossRefSlot.captured.size)
        assertEquals(0, crossRefSlot.captured.first().position)
        assertEquals(19, crossRefSlot.captured.last().position)

        val keySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keySlot)) }
        assertEquals(key, keySlot.captured.queryKey)
        assertNull(keySlot.captured.prevOffset)
        assertEquals(20, keySlot.captured.nextOffset)
        assertEquals(now, keySlot.captured.lastUpdatedEpochSeconds)
    }

    @Test
    fun `load APPEND uses nextOffset, deletes position greater equal targetOffset, and appends continuous positions`() = runTest {
        val now = 6000L
        val key = "discover:top-rated"
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = now - 100
        )
        val remoteGames = List(20) { index ->
            sampleGame1.copy(id = index + 21L, name = "Game ${index + 21}")
        }
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { limit, offset ->
                assertEquals(20, limit)
                assertEquals(20, offset)
                remoteGames
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            nowEpochSeconds = { now }
        )

        val result = mediator.load(LoadType.APPEND, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        coVerify(exactly = 1) { searchDao.deleteSearchResultsFromPosition(key, 20) }

        val crossRefSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(crossRefSlot)) }
        assertEquals(20, crossRefSlot.captured.size)
        assertEquals(20, crossRefSlot.captured.first().position)
        assertEquals(39, crossRefSlot.captured.last().position)

        val keySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keySlot)) }
        assertEquals(40, keySlot.captured.nextOffset)
    }

    @Test
    fun `load returns endOfPaginationReached true and nextOffset null when remote returns short page`() = runTest {
        val key = "q:zelda"
        val remoteGames = listOf(sampleGame1, sampleGame2) // 2 items < loadSize 20
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 900L,
            fetcher = { _, _ -> remoteGames },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.REFRESH, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        val keySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keySlot)) }
        assertNull(keySlot.captured.nextOffset)
    }

    @Test
    fun `load calculates nextOffset from original remote response size before distinctBy`() = runTest {
        val key = "q:witcher"
        // 20 items returned by BFF, but with duplicate IDs (e.g. 10 unique IDs duplicated)
        val remoteGamesWithDuplicates = List(20) { index ->
            sampleGame1.copy(id = (index % 10) + 1L, name = "Game ${(index % 10) + 1}")
        }
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 900L,
            fetcher = { _, _ -> remoteGamesWithDuplicates },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.REFRESH, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        // Only 10 distinct games inserted
        coVerify(exactly = 1) { gameDao.upsertGames(match { it.size == 10 }) }
        coVerify(exactly = 1) { searchDao.insertSearchResults(match { it.size == 10 }) }

        // But nextOffset must be targetOffset(0) + originalRemote.size(20) = 20
        val keySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keySlot)) }
        assertEquals(20, keySlot.captured.nextOffset)
    }

    @Test
    fun `load APPEND crossing the BFF offset ceiling reports end and records null nextOffset`() = runTest {
        val key = "discover:top-rated"
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 1000,
            lastUpdatedEpochSeconds = 1000L
        )
        // A full page is returned, yet the page crosses MAX_BFF_OFFSET (1000 + 20 > 1000):
        // pagination must stop because of the ceiling, and the terminal decision must be
        // recorded (null nextOffset) instead of being guessed by the UI from
        // endOfPaginationReached — offset exhaustion is not result exhaustion.
        val ceilingPage = List(20) { index -> sampleGame1.copy(id = 900L + index) }
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { limit, offset ->
                assertEquals(20, limit)
                assertEquals(1000, offset)
                ceilingPage
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.APPEND, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        // The page itself is still persisted: users keep access to every reachable row.
        coVerify(exactly = 1) { searchDao.insertSearchResults(match { it.size == 20 }) }

        val keySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keySlot)) }
        assertNull(keySlot.captured.nextOffset)

        // The next append reads the recorded terminal state and stops without any request.
        coEvery { remoteKeyDao.getRemoteKey(key) } returns keySlot.captured
        var fetcherCalled = false
        val terminalMediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ ->
                fetcherCalled = true
                emptyList()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val terminal = terminalMediator.load(LoadType.APPEND, testPagingState)
        assertTrue(terminal is RemoteMediator.MediatorResult.Success)
        assertTrue((terminal as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(false, fetcherCalled)
    }

    @Test
    fun `load APPEND beyond the BFF offset ceiling ends without any request`() = runTest {
        val key = "discover:top-rated"
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 1001,
            lastUpdatedEpochSeconds = 1000L
        )
        var fetcherCalled = false
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ ->
                fetcherCalled = true
                emptyList()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.APPEND, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(false, fetcherCalled)
    }

    @Test
    fun `load returns Error without database writes when fetcher throws IOException`() = runTest {
        val mediator = GamesRemoteMediator(
            queryKey = "discover:top-rated",
            ttlSeconds = 3600L,
            fetcher = { _, _ -> throw IOException("Connection lost") },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.REFRESH, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Error)
        val throwable = (result as RemoteMediator.MediatorResult.Error).throwable
        assertTrue(throwable is AppErrorException)
        assertEquals(AppError.NetworkError, (throwable as AppErrorException).error)
        assertTrue(throwable.cause is IOException)

        coVerify(exactly = 0) { gameDao.upsertGames(any()) }
        coVerify(exactly = 0) { searchDao.insertSearchResults(any()) }
        coVerify(exactly = 0) { remoteKeyDao.upsert(any()) }
    }

    @Test
    fun `load executes best-effort cleanup on successful REFRESH and does not fail if cleanup throws`() = runTest {
        var cleanupCalled = false
        val mediator = GamesRemoteMediator(
            queryKey = "discover:top-rated",
            ttlSeconds = 3600L,
            fetcher = { _, _ -> listOf(sampleGame1) },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            cleanupStaleCache = {
                cleanupCalled = true
                throw RuntimeException("Cleanup DB locked")
            }
        )

        val result = mediator.load(LoadType.REFRESH, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertEquals(true, cleanupCalled)
    }

    @Test
    fun `parent entities games and search_queries are upserted before search_results crossrefs`() = runTest {
        val mediator = GamesRemoteMediator(
            queryKey = "discover:top-rated",
            ttlSeconds = 3600L,
            fetcher = { _, _ -> listOf(sampleGame1) },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.REFRESH, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)

        coVerifyOrder {
            gameDao.upsertGames(any())
            searchDao.upsertSearchQuery(any())
            searchDao.deleteSearchResultsForQuery("discover:top-rated")
            searchDao.insertSearchResults(any())
            remoteKeyDao.upsert(any())
        }
    }

    @Test
    fun `load APPEND when remoteKey is missing returns Success end true without fetch`() = runTest {
        val key = "discover:top-rated"
        coEvery { remoteKeyDao.getRemoteKey(key) } returns null
        var fetcherCalled = false
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ ->
                fetcherCalled = true
                emptyList()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.APPEND, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(false, fetcherCalled)
    }

    @Test
    fun `load APPEND when nextOffset is null returns Success end true without fetch`() = runTest {
        val key = "discover:top-rated"
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = null,
            lastUpdatedEpochSeconds = 1000L
        )
        var fetcherCalled = false
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, _ ->
                fetcherCalled = true
                emptyList()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        val result = mediator.load(LoadType.APPEND, testPagingState)
        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(false, fetcherCalled)
    }
}
