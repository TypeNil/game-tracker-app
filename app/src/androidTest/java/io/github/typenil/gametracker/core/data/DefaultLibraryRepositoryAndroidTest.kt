package io.github.typenil.gametracker.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.data.repository.DefaultLibraryRepository
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DefaultLibraryRepositoryAndroidTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var libraryDao: LibraryDao
    private lateinit var repository: DefaultLibraryRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        libraryDao = database.libraryDao()
        repository = DefaultLibraryRepository(
            libraryDao = libraryDao,
            gameDao = gameDao,
            transactionRunner = RoomTransactionRunner(database),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun addToWishlist_whenCatalogRowMissing_insertsGameAndEntry() = runTest {
        val game = Game(id = 7L, name = "Hades II")
        val result = repository.addToWishlist(game)
        assertTrue(result is AppResult.Success)
        assertNotNull(gameDao.getGameById(7L))
        val entry = libraryDao.getLibraryEntry(7L)
        assertNotNull(entry)
        assertEquals(LibraryStatus.WISHLIST, entry!!.status)
    }

    @Test
    fun addToWishlist_whenCompletedExists_doesNotChangeStatus() = runTest {
        gameDao.upsertGame(Game(id = 7L, name = "Hades II").toEntity(1L))
        libraryDao.upsertLibraryEntry(
            LibraryEntry(
                gameId = 7L,
                status = LibraryStatus.COMPLETED,
                userRating = 9,
                addedAtEpochSeconds = 1L,
                updatedAtEpochSeconds = 1L,
            ).toEntity(),
        )
        val result = repository.addToWishlist(Game(id = 7L, name = "Hades II"))
        assertTrue(result is AppResult.Success)
        val entry = libraryDao.getLibraryEntry(7L)!!
        assertEquals(LibraryStatus.COMPLETED, entry.status)
        assertEquals(9, entry.userRating)
    }
}
