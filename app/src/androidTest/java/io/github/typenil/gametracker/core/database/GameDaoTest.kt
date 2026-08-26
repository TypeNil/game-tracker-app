package io.github.typenil.gametracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class GameDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var libraryDao: LibraryDao
    private lateinit var searchDao: SearchDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        libraryDao = database.libraryDao()
        searchDao = database.searchDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertAndGetGameById_returnsCorrectGame() = runTest {
        val game = GameEntity(
            id = 1942L,
            name = "The Witcher 3: Wild Hunt",
            coverUrl = "https://example.com/witcher.jpg",
            rating = 95.8,
            releaseDateEpochSeconds = 1431993600L,
            summary = "Story-driven open world RPG",
            genres = listOf("RPG", "Adventure"),
            platforms = listOf("PC", "PS5"),
            cachedAtEpochSeconds = 1000L
        )

        gameDao.upsertGame(game)

        val retrieved = gameDao.getGameByIdFlow(1942L).first()
        assertNotNull(retrieved)
        assertEquals("The Witcher 3: Wild Hunt", retrieved?.name)
        assertEquals(95.8, retrieved?.rating)
        assertEquals(listOf("RPG", "Adventure"), retrieved?.genres)
        assertEquals(listOf("PC", "PS5"), retrieved?.platforms)
    }

    @Test
    fun getGamesByIds_returnsMatchingList() = runTest {
        val game1 = GameEntity(1L, "G1", null, 90.0, null, null, emptyList(), emptyList(), 100L)
        val game2 = GameEntity(2L, "G2", null, 80.0, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGames(listOf(game1, game2))

        val retrieved = gameDao.getGamesByIds(listOf(1L, 2L))
        assertEquals(2, retrieved.size)
    }

    @Test
    fun deleteStaleUnsavedGames_deletesOnlyStaleUnreferencedGames() = runTest {
        val staleGame = GameEntity(1L, "Stale", null, null, null, null, emptyList(), emptyList(), 100L)
        val freshGame = GameEntity(2L, "Fresh", null, null, null, null, emptyList(), emptyList(), 500L)
        val libraryGame = GameEntity(3L, "Library", null, null, null, null, emptyList(), emptyList(), 100L)
        val searchGame = GameEntity(4L, "Search", null, null, null, null, emptyList(), emptyList(), 100L)

        gameDao.upsertGames(listOf(staleGame, freshGame, libraryGame, searchGame))

        libraryDao.upsertLibraryEntry(
            LibraryEntryEntity(
                gameId = 3L,
                status = LibraryStatus.PLAYING,
                addedAtEpochSeconds = 100L,
                updatedAtEpochSeconds = 100L
            )
        )

        searchDao.upsertSearchQuery(
            SearchQueryEntity(
                query = "witcher",
                createdAtEpochSeconds = 100L,
                lastQueriedAtEpochSeconds = 100L,
                resultCount = 1
            )
        )
        searchDao.insertSearchResults(listOf(SearchResultCrossRef("witcher", 4L, 0)))

        val deletedCount = gameDao.deleteStaleUnsavedGames(staleThreshold = 300L)
        assertEquals(1, deletedCount)

        assertNull(gameDao.getGameById(1L))
        assertNotNull(gameDao.getGameById(2L))
        assertNotNull(gameDao.getGameById(3L))
        assertNotNull(gameDao.getGameById(4L))
    }

    @Test
    fun getGameById_usesPrimaryKey() = runTest {
        val plan = explain(GameDao.GAME_BY_ID, 1L)
        assertTrue(plan, plan.contains("PRIMARY KEY") || plan.contains("INTEGER PRIMARY KEY"))
    }

    private fun explain(queryConst: String, vararg bindArgs: Any): String {
        var sql = queryConst
        for (name in listOf(":query", ":fromPosition", ":status", ":id")) {
            sql = sql.replace(name, "?")
        }
        val cursor = database.query("EXPLAIN QUERY PLAN $sql", arrayOf(*bindArgs))
        val details = buildString {
            while (cursor.moveToNext()) {
                appendLine(cursor.getString(cursor.columnCount - 1))
            }
        }
        cursor.close()
        return details
    }
}
