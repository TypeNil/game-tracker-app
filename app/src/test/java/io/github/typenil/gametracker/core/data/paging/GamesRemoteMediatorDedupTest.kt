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
import io.github.typenil.gametracker.core.model.Game
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deduplication and legacy-window contract of [GamesRemoteMediator]: dense local
 * ordinals vs raw server cursors, append-only windows, and the sparse-cache defense.
 */
@OptIn(ExperimentalPagingApi::class)
class GamesRemoteMediatorDedupTest {

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

    private val testPagingConfig = PagingConfig(
        pageSize = 20,
        prefetchDistance = 5,
        enablePlaceholders = false,
        initialLoadSize = 20
    )

    private val testPagingState = PagingState<Int, GameEntity>(
        pages = listOf(PagingSource.LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)),
        config = testPagingConfig,
        anchorPosition = null,
        leadingPlaceholderCount = 0
    )

    // Count sequence shared by the stubs and the metadata assertions: REFRESH pre 0 /
    // post 20; APPEND2 pre 20 / post 37; APPEND3 pre 37 / post 57.
    private val threePageCounts = listOf(0, 20, 20, 37, 37, 57)
    private val page2NewRows = 17
    private val firstRepeatedPageTwoId = 18L

    @Before
    fun setUp() {
        coEvery { searchDao.getSearchQuery(any()) } returns null
        coEvery { searchDao.countSearchResultsForQuery(any()) } returns 0
        coEvery { remoteKeyDao.getRemoteKey(any()) } returns null
        coEvery { searchDao.hasDenseSearchResultPositions(any()) } returns true
        coEvery { searchDao.getSearchResultGameIds(any()) } returns emptyList()
    }

    @Test
    fun `freshSparseLegacyWindow_launchesInitialRefresh`() = runTest {
        val now = 10_000L
        val key = "discover:top-rated"
        // Legacy shape: rows at 0..9 and 20..39 (30 rows, metadata matches, key fresh)
        // written while positions came from the server offset. A dense-ordinal APPEND
        // would violate the unique (query, position) index, so the window must refresh.
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 40,
            lastUpdatedEpochSeconds = now - 100
        )
        coEvery { searchDao.countSearchResultsForQuery(key) } returns 30
        coEvery { searchDao.getSearchQuery(key) } returns SearchQueryEntity(
            query = key,
            createdAtEpochSeconds = now - 100,
            lastQueriedAtEpochSeconds = now - 100,
            resultCount = 30
        )
        coEvery { searchDao.hasDenseSearchResultPositions(key) } returns false

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

        assertEquals(
            RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
            mediator.initialize()
        )
    }

    @Test
    fun `load APPEND deduplicates against persisted ids, appends dense positions, never deletes prior rows`() = runTest {
        val now = 6000L
        val key = "discover:top-rated"
        coEvery { remoteKeyDao.getRemoteKey(key) } returns RemoteKeyEntity(
            queryKey = key,
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = now - 100
        )
        // The server repeats ids 19 and 20 (already persisted from page 1) plus 18 new ids.
        val remoteGames = List(20) { index ->
            sampleGame1.copy(id = 19L + index, name = "Game ${19 + index}")
        }
        coEvery { searchDao.getSearchResultGameIds(key) } returns (1L..20L).toList()
        // Pinned by purpose: pre-insert count (dense local start), post-insert count (metadata).
        coEvery { searchDao.countSearchResultsForQuery(key) } returnsMany listOf(20, 38)
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

        // Append-only window: previously presented rows are never deleted or moved.
        coVerify(exactly = 0) { searchDao.deleteSearchResultsFromPosition(any(), any()) }
        coVerify(exactly = 0) { searchDao.deleteSearchResultsForQuery(any()) }

        val crossRefSlot = slot<List<SearchResultCrossRef>>()
        coVerify(exactly = 1) { searchDao.insertSearchResults(capture(crossRefSlot)) }
        assertEquals(18, crossRefSlot.captured.size)
        assertEquals(20, crossRefSlot.captured.first().position)
        assertEquals(37, crossRefSlot.captured.last().position)
        assertEquals((21L..38L).toSet(), crossRefSlot.captured.map { it.gameId }.toSet())

        // Shared payloads refresh for ALL distinct page games, duplicates included.
        coVerify(exactly = 1) {
            gameDao.upsertGames(match { it.size == 20 && it.any { entity -> entity.id == 19L } })
        }

        // Server cursor advances by the raw page size, unaffected by dedup filtering.
        val keySlot = slot<RemoteKeyEntity>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keySlot)) }
        assertEquals(40, keySlot.captured.nextOffset)

        val metaSlots = mutableListOf<SearchQueryEntity>()
        coVerify(exactly = 2) { searchDao.upsertSearchQuery(capture(metaSlots)) }
        assertEquals(38, metaSlots.last().resultCount)
    }

    @Test
    fun `three pages with cross-page duplicates keep dense local positions and raw server cursors`() = runTest {
        val key = "discover:top-rated"
        var cursor: RemoteKeyEntity? = null
        coEvery { remoteKeyDao.getRemoteKey(key) } answers { cursor }
        coEvery { remoteKeyDao.upsert(any()) } answers {
            cursor = firstArg()
            0L
        }

        val inserts = mutableListOf<List<SearchResultCrossRef>>()
        coEvery { searchDao.insertSearchResults(capture(inserts)) } returns emptyList()
        val upsertedGames = mutableListOf<List<GameEntity>>()
        coEvery { gameDao.upsertGames(capture(upsertedGames)) } coAnswers { emptyList() }
        val metas = mutableListOf<SearchQueryEntity>()
        coEvery { searchDao.upsertSearchQuery(capture(metas)) } returns 0L
        coEvery { searchDao.countSearchResultsForQuery(key) } returnsMany threePageCounts
        coEvery { searchDao.getSearchResultGameIds(key) } returnsMany
            listOf((1L..20L).toList(), (1L..37L).toList())

        val pages = listOf(
            List(20) { sampleGame1.copy(id = it + 1L, name = "P1 ${it + 1}") },
            // Page 2 repeats ids 18..20 with updated payloads and adds 17 new ids (21..37).
            List(20) { sampleGame1.copy(id = 18L + it, name = "P2 ${18L + it}") },
            List(20) { sampleGame1.copy(id = 38L + it, name = "P3 ${38L + it}") },
        )
        var loadIndex = 0
        val mediator = GamesRemoteMediator(
            queryKey = key,
            ttlSeconds = 3600L,
            fetcher = { _, offset ->
                assertEquals(loadIndex * 20, offset)
                pages[loadIndex++]
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner
        )

        assertTrue(mediator.successfulLoad(LoadType.REFRESH))
        assertEquals(20, cursor?.nextOffset)
        assertTrue(mediator.successfulLoad(LoadType.APPEND))
        assertEquals(40, cursor?.nextOffset)
        assertTrue(mediator.successfulLoad(LoadType.APPEND))
        assertEquals(60, cursor?.nextOffset)

        assertThreePageWindow(key, inserts, upsertedGames, metas)
    }

    private suspend fun GamesRemoteMediator.successfulLoad(loadType: LoadType): Boolean =
        load(loadType, testPagingState) is RemoteMediator.MediatorResult.Success

    private fun assertThreePageWindow(
        key: String,
        inserts: List<List<SearchResultCrossRef>>,
        upsertedGames: List<List<GameEntity>>,
        metas: List<SearchQueryEntity>,
    ) {
        assertEquals(3, inserts.size)
        assertEquals((0..19).toList(), inserts[0].map { it.position })
        assertEquals(page2NewRows, inserts[1].size)
        assertEquals((20..36).toList(), inserts[1].map { it.position })
        assertEquals((21L..37L).toList(), inserts[1].map { it.gameId })
        assertEquals((37..56).toList(), inserts[2].map { it.position })
        // No previously presented row is ever deleted by an append.
        coVerify(exactly = 1) { searchDao.deleteSearchResultsForQuery(key) }
        coVerify(exactly = 0) { searchDao.deleteSearchResultsFromPosition(any(), any()) }
        // Duplicate page-2 payloads still refresh the shared games table.
        assertTrue(upsertedGames[1].any { it.id == firstRepeatedPageTwoId })
        // Integrity metadata: provisional then final per load; the final value wins.
        assertEquals(threePageCounts, metas.map { it.resultCount })
    }
}
