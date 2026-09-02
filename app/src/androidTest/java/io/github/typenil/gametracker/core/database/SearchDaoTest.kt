package io.github.typenil.gametracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import androidx.paging.PagingSource
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SearchDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var searchDao: SearchDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        searchDao = database.searchDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun getSearchResultsFlow_returnsGamesInExactPositionOrder() = runTest {
        val g1 = GameEntity(10L, "Game A", null, null, null, null, emptyList(), emptyList(), 100L)
        val g2 = GameEntity(20L, "Game B", null, null, null, null, emptyList(), emptyList(), 100L)
        val g3 = GameEntity(30L, "Game C", null, null, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGames(listOf(g1, g2, g3))

        searchDao.upsertSearchQuery(
            SearchQueryEntity(
                query = "test",
                createdAtEpochSeconds = 100L,
                lastQueriedAtEpochSeconds = 100L,
                resultCount = 3
            )
        )

        // Insert results in deliberate non-id order: pos 0 -> Game C (30), pos 1 -> Game A (10), pos 2 -> Game B (20)
        searchDao.insertSearchResults(
            listOf(
                SearchResultCrossRef(query = "test", gameId = 30L, position = 0),
                SearchResultCrossRef(query = "test", gameId = 10L, position = 1),
                SearchResultCrossRef(query = "test", gameId = 20L, position = 2)
            )
        )

        val results = searchDao.getSearchResultsFlow("test").first()
        assertEquals(3, results.size)
        assertEquals(30L, results[0].id)
        assertEquals("Game C", results[0].name)
        assertEquals(10L, results[1].id)
        assertEquals("Game A", results[1].name)
        assertEquals(20L, results[2].id)
        assertEquals("Game B", results[2].name)
    }

    @Test
    fun deletingSearchQuery_cascadesAndDeletesSearchResults() = runTest {
        val game = GameEntity(1L, "G1", null, null, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGame(game)

        searchDao.upsertSearchQuery(SearchQueryEntity("witcher", 100L, 100L, 1))
        searchDao.insertSearchResults(listOf(SearchResultCrossRef("witcher", 1L, 0)))

        assertEquals(1, searchDao.getSearchResults("witcher").size)

        searchDao.deleteSearchQuery("witcher")

        assertEquals(0, searchDao.getSearchResults("witcher").size)
        assertNull(searchDao.getSearchQuery("witcher"))
    }

    @Test
    fun getRecentSearchQueriesFlow_returnsOrderedByLastQueried() = runTest {
        searchDao.upsertSearchQuery(SearchQueryEntity("old", 100L, 100L, 1))
        searchDao.upsertSearchQuery(SearchQueryEntity("newest", 100L, 300L, 1))
        searchDao.upsertSearchQuery(SearchQueryEntity("middle", 100L, 200L, 1))

        val recent = searchDao.getRecentSearchQueriesFlow(limit = 2).first()
        assertEquals(2, recent.size)
        assertEquals("newest", recent[0].query)
        assertEquals("middle", recent[1].query)
    }

    @Test
    fun discoverKeyAndSearchKeyWithSameNameAreIsolated() = runTest {
        val g1 = GameEntity(1L, "Top Rated Game", null, 99.0, null, null, emptyList(), emptyList(), 100L)
        val g2 = GameEntity(2L, "Searched Game", null, 70.0, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGames(listOf(g1, g2))

        searchDao.upsertSearchQuery(SearchQueryEntity("discover:top-rated", 100L, 100L, 1))
        searchDao.insertSearchResults(listOf(SearchResultCrossRef("discover:top-rated", 1L, 0)))

        searchDao.upsertSearchQuery(SearchQueryEntity("q:discover:top-rated", 100L, 100L, 1))
        searchDao.insertSearchResults(listOf(SearchResultCrossRef("q:discover:top-rated", 2L, 0)))

        val discoverResults = searchDao.getSearchResultsFlow("discover:top-rated").first()
        assertEquals(1, discoverResults.size)
        assertEquals(1L, discoverResults[0].id)
        assertEquals("Top Rated Game", discoverResults[0].name)

        val searchResults = searchDao.getSearchResultsFlow("q:discover:top-rated").first()
        assertEquals(1, searchResults.size)
        assertEquals(2L, searchResults[0].id)
        assertEquals("Searched Game", searchResults[0].name)
    }

    @Test
    fun searchResultsJoin_usesQueryPositionIndex() = runTest {
        seedSearch()
        val plan = database.explainQueryPlan(SearchDao.SEARCH_RESULTS_BY_POSITION, "q")
        assertTrue(plan, plan.contains("index_search_results_query_position"))
    }

    @Test
    fun deleteFromPosition_usesQueryPositionIndex() = runTest {
        seedSearch()
        val plan = database.explainQueryPlan(SearchDao.DELETE_SEARCH_RESULTS_FROM_POSITION, "q", 1)
        assertTrue(plan, plan.contains("index_search_results_query_position"))
    }

    private suspend fun seedSearch() {
        gameDao.upsertGames(
            listOf(GameEntity(1L, "G1", null, null, null, null, emptyList(), emptyList(), 100L)),
        )
        searchDao.upsertSearchQuery(
            SearchQueryEntity(
                query = "q",
                createdAtEpochSeconds = 100L,
                lastQueriedAtEpochSeconds = 100L,
                resultCount = 1,
            ),
        )
        searchDao.insertSearchResults(
            listOf(SearchResultCrossRef(query = "q", gameId = 1L, position = 0)),
        )
    }

    @Test
    fun pagingSource_isInvalidated_andReloadsAppendedRowsInPositionOrder() = runTest(timeout = 30.seconds) {
        val query = "pagination"
        val games = (0L until 40L).map { id ->
            GameEntity(id, "Game $id", null, null, null, null, emptyList(), emptyList(), 100L)
        }
        gameDao.upsertGames(games)
        searchDao.upsertSearchQuery(SearchQueryEntity(query, 100L, 100L, 20))
        searchDao.insertSearchResults(
            (0L until 20L).map { SearchResultCrossRef(query = query, gameId = it, position = it.toInt()) }
        )

        val firstSource: PagingSource<Int, GameEntity> = searchDao.getSearchResultsPagingSource(query)
        val invalidated = CompletableDeferred<Unit>()
        firstSource.registerInvalidatedCallback { invalidated.complete(Unit) }

        val initial = firstSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 20, placeholdersEnabled = false),
        )
        assertTrue(initial is PagingSource.LoadResult.Page)
        assertEquals((0L until 20L).toList(), (initial as PagingSource.LoadResult.Page).data.map { it.id })

        // The append write the RemoteMediator performs for the next page must trip Room's
        // invalidation tracker so the paging container reloads instead of freezing at 20 rows.
        searchDao.insertSearchResults(
            (20L until 40L).map { SearchResultCrossRef(query = query, gameId = it, position = it.toInt()) }
        )
        invalidated.await()

        // Paging restarts through the factory with the next key, exactly like the mediator's
        // resolved append offset: rows from both writes come back in position order.
        val reloaded = searchDao.getSearchResultsPagingSource(query)
            .load(PagingSource.LoadParams.Refresh(key = 20, loadSize = 20, placeholdersEnabled = false))
        assertTrue(reloaded is PagingSource.LoadResult.Page)
        val page = reloaded as PagingSource.LoadResult.Page
        assertEquals((20L until 40L).toList(), page.data.map { it.id })
        assertEquals(20, page.prevKey)
        // Room compares offset + returned rows against the total count: at the true end of
        // the table nextKey is null — exactly the end-of-pagination signal the paging
        // container uses to stop requesting appends.
        assertNull(page.nextKey)
    }
}

