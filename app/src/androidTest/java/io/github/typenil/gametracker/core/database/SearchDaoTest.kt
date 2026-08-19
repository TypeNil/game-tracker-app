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
}
