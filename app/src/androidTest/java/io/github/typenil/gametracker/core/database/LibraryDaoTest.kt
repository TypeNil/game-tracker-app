package io.github.typenil.gametracker.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
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
class LibraryDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        libraryDao = database.libraryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertAndGetLibraryEntry_updatesStatusAndFavorites() = runTest {
        val game = GameEntity(101L, "Elden Ring", null, null, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGame(game)

        val entry = LibraryEntryEntity(
            gameId = 101L,
            status = LibraryStatus.PLAYING,
            userRating = 10,
            userNotes = "Got the Great Rune",
            isFavorite = true,
            addedAtEpochSeconds = 1000L,
            updatedAtEpochSeconds = 1000L
        )

        libraryDao.upsertLibraryEntry(entry)

        val retrieved = libraryDao.getLibraryEntryFlow(101L).first()
        assertNotNull(retrieved)
        assertEquals(LibraryStatus.PLAYING, retrieved?.status)
        assertEquals(10, retrieved?.userRating)
        assertEquals("Got the Great Rune", retrieved?.userNotes)
        assertEquals(true, retrieved?.isFavorite)
        assertEquals(0, retrieved?.hoursPlayed)

        // Update status to COMPLETED and record hours played
        libraryDao.upsertLibraryEntry(
            entry.copy(
                status = LibraryStatus.COMPLETED,
                updatedAtEpochSeconds = 2000L,
                hoursPlayed = 75
            )
        )
        val updated = libraryDao.getLibraryEntry(101L)
        assertEquals(LibraryStatus.COMPLETED, updated?.status)
        assertEquals(2000L, updated?.updatedAtEpochSeconds)
        assertEquals(75, updated?.hoursPlayed)
    }

    @Test
    fun getLibraryEntriesByStatus_filtersCorrectly() = runTest {
        val g1 = GameEntity(1L, "G1", null, null, null, null, emptyList(), emptyList(), 100L)
        val g2 = GameEntity(2L, "G2", null, null, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGames(listOf(g1, g2))

        libraryDao.upsertLibraryEntry(LibraryEntryEntity(1L, LibraryStatus.PLAYING, null, null, false, 100L, 200L))
        libraryDao.upsertLibraryEntry(LibraryEntryEntity(2L, LibraryStatus.COMPLETED, null, null, false, 100L, 300L))

        val playing = libraryDao.getLibraryEntriesByStatusFlow(LibraryStatus.PLAYING).first()
        assertEquals(1, playing.size)
        assertEquals(1L, playing[0].gameId)

        val completed = libraryDao.getLibraryEntriesByStatusFlow(LibraryStatus.COMPLETED).first()
        assertEquals(1, completed.size)
        assertEquals(2L, completed[0].gameId)
    }

    @Test
    fun foreignKeyRestrict_preventsDirectDeletionOfGameInLibrary() = runTest {
        val game = GameEntity(101L, "Elden Ring", null, null, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGame(game)

        libraryDao.upsertLibraryEntry(
            LibraryEntryEntity(
                gameId = 101L,
                status = LibraryStatus.WISHLIST,
                addedAtEpochSeconds = 100L,
                updatedAtEpochSeconds = 100L
            )
        )

        var constraintViolated = false
        try {
            // Direct SQLite DELETE bypassing safe eviction
            database.openHelper.writableDatabase.execSQL("DELETE FROM games WHERE id = 101")
        } catch (e: SQLiteConstraintException) {
            constraintViolated = true
        }

        assertTrue("Expected SQLiteConstraintException due to ForeignKey.RESTRICT", constraintViolated)
        assertNotNull("Game must still exist in DB", gameDao.getGameById(101L))
    }

    @Test
    fun getPopulatedLibraryEntriesFlow_emitsJoinedGameAndEntry() = runTest {
        val game = GameEntity(101L, "Elden Ring", null, 95.0, 1600000000L, "Summary", emptyList(), emptyList(), 100L)
        gameDao.upsertGame(game)

        val entry = LibraryEntryEntity(
            gameId = 101L,
            status = LibraryStatus.WISHLIST,
            userRating = 9,
            addedAtEpochSeconds = 1000L,
            updatedAtEpochSeconds = 1000L
        )
        libraryDao.upsertLibraryEntry(entry)

        val populated = libraryDao.getPopulatedLibraryEntriesFlow().first()
        assertEquals(1, populated.size)
        assertEquals("Elden Ring", populated[0].game.name)
        assertEquals(LibraryStatus.WISHLIST, populated[0].entry.status)
        assertEquals(9, populated[0].entry.userRating)
    }

    @Test
    fun rawSqlPlanToPlay_deserializesAsWishlist() = runTest {
        val game = GameEntity(102L, "Bloodborne", null, null, null, null, emptyList(), emptyList(), 100L)
        gameDao.upsertGame(game)

        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO library_entries (gameId, status, addedAtEpochSeconds, updatedAtEpochSeconds, isFavorite, hoursPlayed) VALUES (102, 'PLAN_TO_PLAY', 100, 100, 0, 0)"
        )

        val entry = libraryDao.getLibraryEntry(102L)
        assertNotNull(entry)
        assertEquals(LibraryStatus.WISHLIST, entry?.status)
    }

    @Test
    fun libraryByStatus_usesStatusIndex() = runTest {
        val plan = explain(LibraryDao.LIBRARY_ENTRIES_BY_STATUS, "PLAYING")
        assertTrue(plan, plan.contains("index_library_entries_status"))
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
