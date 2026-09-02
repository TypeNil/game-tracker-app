package io.github.typenil.gametracker.core.data.paging

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.transaction.RoomTransactionRunner
import io.github.typenil.gametracker.core.model.Game
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end persistence contract of [GamesRemoteMediator] on a real in-memory Room
 * database (not scripted mocks): cross-page deduplication produces a dense display
 * window under the unique (query, position) index, FK ordering inside the mediator
 * transaction holds (game rows always precede their cross-references), and the final
 * integrity metadata equals the actually persisted row count while the remote cursor
 * keeps tracking the raw server page size.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalPagingApi::class)
class GamesRemoteMediatorRoomTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var searchDao: SearchDao
    private lateinit var remoteKeyDao: RemoteKeyDao

    private val pageSize = 20
    private val queryKey = "dedup:room"
    private val totalUniqueGames = 57
    private val refreshedDuplicateIndex = 17

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        searchDao = database.searchDao()
        remoteKeyDao = database.remoteKeyDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun threePageDedup_persistsDenseWindowAndMatchingMetadata() = runTest {
        val pages = listOf(
            (1L..20L).map(::roomGame),
            // Page 2 repeats ids 18..20 with updated payloads and adds 21..37.
            (18L..37L).map { roomGame(it).copy(name = "Refreshed $it") },
            (38L..57L).map(::roomGame),
        )
        val requestedOffsets = mutableListOf<Int>()
        val mediator = GamesRemoteMediator(
            queryKey = queryKey,
            ttlSeconds = 900L,
            fetcher = { _, offset ->
                requestedOffsets += offset
                pages[requestedOffsets.lastIndex]
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = RoomTransactionRunner(database),
            nowEpochSeconds = { 1000L }
        )

        val pagingState = PagingState<Int, GameEntity>(
            pages = listOf(
                PagingSource.LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            ),
            config = PagingConfig(
                pageSize = pageSize,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = pageSize
            ),
            anchorPosition = null,
            leadingPlaceholderCount = 0
        )

        assertTrue(mediator.load(LoadType.REFRESH, pagingState).isSuccess())
        assertTrue(mediator.load(LoadType.APPEND, pagingState).isSuccess())
        assertTrue(mediator.load(LoadType.APPEND, pagingState).isSuccess())

        // Server cursors: raw page sizes, never compacted by deduplication.
        assertEquals(listOf(0, pageSize, 2 * pageSize), requestedOffsets)
        assertEquals(3 * pageSize, remoteKeyDao.getRemoteKey(queryKey)?.nextOffset)

        val rows = searchDao.getSearchResults(queryKey)
        assertEquals((1L..totalUniqueGames).toList(), rows.map { it.id })
        assertTrue(searchDao.hasDenseSearchResultPositions(queryKey))
        assertEquals(totalUniqueGames, searchDao.countSearchResultsForQuery(queryKey))
        // The stored metadata equals the real persisted count, and the refreshed
        // duplicate payload is visible through the window join.
        assertEquals(totalUniqueGames, searchDao.getSearchQuery(queryKey)?.resultCount)
        assertEquals("Refreshed 18", rows[refreshedDuplicateIndex].name)
    }

    private fun RemoteMediator.MediatorResult.isSuccess(): Boolean =
        this is RemoteMediator.MediatorResult.Success

    private fun roomGame(id: Long) = Game(id = id, name = "Game $id")
}
