package io.github.typenil.gametracker.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.RoomTransactionRunner
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.Dispatchers
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class LibraryBackupRepositoryAndroidTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var repository: DefaultLibraryBackupRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultLibraryBackupRepository(
            libraryDao = database.libraryDao(),
            gameDao = database.gameDao(),
            transactionRunner = RoomTransactionRunner(database),
            ioDispatcher = Dispatchers.Unconfined,
            clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun merge_overwritesConflictAndKeepsLocalOnlyGame() = runTest {
        val gameDao = database.gameDao()
        val libraryDao = database.libraryDao()
        gameDao.upsertGame(Game(id = 1L, name = "Local only").toEntity(1L))
        gameDao.upsertGame(Game(id = 2L, name = "Conflict local").toEntity(1L))
        libraryDao.upsertLibraryEntry(entry(1L, LibraryStatus.WISHLIST))
        libraryDao.upsertLibraryEntry(entry(2L, LibraryStatus.PLAYING, rating = 5))

        val result = repository.importLibrary(
            LibraryBackupFile(
                schemaVersion = 1,
                exportedAt = Instant.parse("2026-09-10T12:00:00Z"),
                items = listOf(
                    LibraryBackupItem(
                        entry = LibraryEntry(
                            gameId = 2L,
                            status = LibraryStatus.COMPLETED,
                            userRating = 9,
                            addedAtEpochSeconds = 10L,
                            updatedAtEpochSeconds = 20L,
                        ),
                        game = Game(id = 2L, name = "Sparse snapshot"),
                    ),
                    LibraryBackupItem(
                        entry = LibraryEntry(
                            gameId = 3L,
                            status = LibraryStatus.WISHLIST,
                            addedAtEpochSeconds = 10L,
                            updatedAtEpochSeconds = 20L,
                        ),
                        game = Game(id = 3L, name = "Imported new"),
                    ),
                ),
            ),
            LibraryImportMode.MERGE,
        )

        assertTrue(result is AppResult.Success)
        assertNotNull(libraryDao.getLibraryEntry(1L))
        assertEquals(LibraryStatus.COMPLETED, libraryDao.getLibraryEntry(2L)?.status)
        assertEquals(9, libraryDao.getLibraryEntry(2L)?.userRating)
        assertEquals("Conflict local", gameDao.getGameById(2L)?.name)
        assertEquals("Imported new", gameDao.getGameById(3L)?.name)
        assertNotNull(libraryDao.getLibraryEntry(3L))
    }

    @Test
    fun replaceEmpty_clearsLibraryAndKeepsCatalogGame() = runTest {
        val gameDao = database.gameDao()
        val libraryDao = database.libraryDao()
        gameDao.upsertGame(Game(id = 99L, name = "Catalog only").toEntity(1L))
        gameDao.upsertGame(Game(id = 1L, name = "Owned").toEntity(1L))
        libraryDao.upsertLibraryEntry(entry(1L, LibraryStatus.PLAYING))

        val result = repository.importLibrary(
            LibraryBackupFile(1, Instant.parse("2026-09-10T12:00:00Z"), emptyList()),
            LibraryImportMode.REPLACE,
        )

        assertTrue(result is AppResult.Success)
        assertTrue(libraryDao.getAllLibraryEntries().isEmpty())
        assertNotNull(gameDao.getGameById(99L))
        assertNotNull(gameDao.getGameById(1L))
        assertNull(libraryDao.getLibraryEntry(1L))
    }

    private fun entry(
        id: Long,
        status: LibraryStatus,
        rating: Int? = null,
    ) = LibraryEntry(
        gameId = id,
        status = status,
        userRating = rating,
        addedAtEpochSeconds = 10L,
        updatedAtEpochSeconds = 20L,
    ).toEntity()
}
