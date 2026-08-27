package io.github.typenil.gametracker.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.entity.CompanyColumn
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
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

    private lateinit var gameDetailsDao: GameDetailsDao
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        gameDetailsDao = database.gameDetailsDao()
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
        assertEquals(true, populated[0].details.isEmpty())

        gameDetailsDao.upsertDetails(
            GameDetailsEntity(
                gameId = 101L,
                name = "Elden Ring",
                coverUrl = null,
                rating = 95.0,
                totalRating = null,
                totalRatingCount = null,
                releaseDateEpochSeconds = null,
                summary = "unused",
                url = null,
                genres = emptyList(),
                themes = emptyList(),
                gameModes = emptyList(),
                platforms = emptyList(),
                releaseDates = emptyList(),
                companies = listOf(CompanyColumn(name = "FromSoftware", isDeveloper = true)),
                screenshots = emptyList(),
                videos = emptyList(),
                similarGames = emptyList(),
                cachedAtEpochSeconds = 100L,
            ),
        )
        val withDetails = libraryDao.getPopulatedLibraryEntriesFlow().first()
        assertEquals(1, withDetails[0].details.size)
        assertEquals("FromSoftware", withDetails[0].details.single().companies.single().name)
        assertEquals(true, withDetails[0].details.single().companies.single().isDeveloper)
    }

    @Test
    fun toggleFavorite_twice_restoresOriginalValue_andPreservesOtherFields() = runTest {
        gameDao.upsertGame(
            GameEntity(101L, "Elden Ring", null, null, null, null, emptyList(), emptyList(), 100L),
        )
        libraryDao.upsertLibraryEntry(
            LibraryEntryEntity(
                gameId = 101L,
                status = LibraryStatus.PLAYING,
                userRating = 10,
                userNotes = "Got the Great Rune",
                isFavorite = true,
                addedAtEpochSeconds = 1000L,
                updatedAtEpochSeconds = 1000L,
                hoursPlayed = 75,
            ),
        )

        assertEquals(1, libraryDao.toggleFavorite(gameId = 101L, updatedAtEpochSeconds = 2000L))
        val afterFirst = libraryDao.getLibraryEntry(101L)
        assertEquals(false, afterFirst?.isFavorite)
        assertEquals(LibraryStatus.PLAYING, afterFirst?.status)
        assertEquals(10, afterFirst?.userRating)
        assertEquals("Got the Great Rune", afterFirst?.userNotes)
        assertEquals(75, afterFirst?.hoursPlayed)
        assertEquals(1000L, afterFirst?.addedAtEpochSeconds)
        assertEquals(2000L, afterFirst?.updatedAtEpochSeconds)

        assertEquals(1, libraryDao.toggleFavorite(gameId = 101L, updatedAtEpochSeconds = 3000L))
        val afterSecond = libraryDao.getLibraryEntry(101L)
        assertEquals(true, afterSecond?.isFavorite)
        assertEquals(LibraryStatus.PLAYING, afterSecond?.status)
        assertEquals(10, afterSecond?.userRating)
        assertEquals("Got the Great Rune", afterSecond?.userNotes)
        assertEquals(75, afterSecond?.hoursPlayed)
        assertEquals(1000L, afterSecond?.addedAtEpochSeconds)
        assertEquals(3000L, afterSecond?.updatedAtEpochSeconds)
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
        val plan = database.explainQueryPlan(LibraryDao.LIBRARY_ENTRIES_BY_STATUS, "PLAYING")
        assertTrue(plan, plan.contains("index_library_entries_status"))
    }

}
