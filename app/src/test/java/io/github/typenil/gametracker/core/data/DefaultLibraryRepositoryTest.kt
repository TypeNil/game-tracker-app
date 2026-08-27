package io.github.typenil.gametracker.core.data

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.DefaultLibraryRepository
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.PopulatedLibraryGameEntity
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.Game

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLibraryRepositoryTest {

    private val libraryDao: LibraryDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val passThroughTransactionRunner = object : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }
    private val repository = DefaultLibraryRepository(
        libraryDao,
        gameDao,
        passThroughTransactionRunner,
        testDispatcher,
    )

    @Test
    fun getLibraryGamesFlow_mapsEntitiesToDomain() = runTest(testDispatcher) {
        val gameEntity = GameEntity(
            id = 1L,
            name = "Hades",
            coverUrl = null,
            rating = 93.0,
            releaseDateEpochSeconds = 1600000000L,
            summary = "Great rogue-like",
            genres = listOf("Action"),
            platforms = listOf("PC"),
            cachedAtEpochSeconds = 100L
        )
        val entryEntity = LibraryEntryEntity(1L, LibraryStatus.PLAYING, 10, "Great game", true, 1000L, 1000L, 25)
        val populated = PopulatedLibraryGameEntity(entry = entryEntity, game = gameEntity)

        every { libraryDao.getPopulatedLibraryEntriesFlow() } returns flowOf(listOf(populated))

        repository.getLibraryGamesFlow().test {
            val games = awaitItem()
            assertEquals(1, games.size)
            assertEquals("Hades", games[0].game.name)
            assertEquals(LibraryStatus.PLAYING, games[0].entry.status)
            assertEquals(10, games[0].entry.userRating)
            assertEquals(true, games[0].entry.isFavorite)
            assertEquals(25, games[0].entry.hoursPlayed)
            awaitComplete()
        }
    }

    @Test
    fun saveLibraryEntry_withoutParentGame_returnsError() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(999L) } returns null

        val entry = LibraryEntry(
            gameId = 999L,
            status = LibraryStatus.COMPLETED,
            addedAtEpochSeconds = 100L,
            updatedAtEpochSeconds = 100L
        )

        val result = repository.saveLibraryEntry(entry)
        assertTrue("Expected Error when parent game is missing", result is AppResult.Error)
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun saveLibraryEntry_withParentGame_upsertsSuccessfully() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(42L) } returns GameEntity(42L, "G", null, null, null, null, emptyList(), emptyList(), 1L)
        coEvery { libraryDao.upsertLibraryEntry(any()) } returns 1L

        val entry = LibraryEntry(
            gameId = 42L,
            status = LibraryStatus.COMPLETED,
            userRating = 10,
            userNotes = "Masterpiece",
            isFavorite = true,
            addedAtEpochSeconds = 100L,
            updatedAtEpochSeconds = 100L,
            hoursPlayed = 60
        )

        val result = repository.saveLibraryEntry(entry)
        assertTrue("Expected Success when parent game exists", result is AppResult.Success)
        coVerify {
            libraryDao.upsertLibraryEntry(
                match { it.gameId == 42L && it.status == LibraryStatus.COMPLETED && it.userRating == 10 }
            )
        }
    }

    @Test
    fun setGameStatus_withoutParentGame_returnsError() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(555L) } returns null

        val result = repository.setGameStatus(555L, LibraryStatus.WISHLIST)
        assertTrue("Expected Error when parent game is missing", result is AppResult.Error)
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun setGameStatus_withParentGame_updatesStatus() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(10L) } returns GameEntity(10L, "Game", null, null, null, null, emptyList(), emptyList(), 1L)
        coEvery { libraryDao.getLibraryEntry(10L) } returns null
        coEvery { libraryDao.upsertLibraryEntry(any()) } returns 1L

        val result = repository.setGameStatus(10L, LibraryStatus.WISHLIST)
        assertTrue(result is AppResult.Success)
        coVerify {
            libraryDao.upsertLibraryEntry(
                match { it.gameId == 10L && it.status == LibraryStatus.WISHLIST }
            )
        }
    }

    @Test
    fun setGameStatus_whenEntryExists_updatesStatusDirectlyWithoutUpsert() = runTest(testDispatcher) {
        coEvery { libraryDao.updateStatus(10L, LibraryStatus.PLAYING, any()) } returns 1

        val result = repository.setGameStatus(10L, LibraryStatus.PLAYING)
        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.updateStatus(10L, LibraryStatus.PLAYING, any()) }
        coVerify(exactly = 0) { gameDao.getGameById(any()) }
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun removeGameFromLibrary_deletesEntry() = runTest(testDispatcher) {
        coEvery { libraryDao.deleteLibraryEntry(10L) } returns 1

        val result = repository.removeGameFromLibrary(10L)
        assertTrue(result is AppResult.Success)
        coVerify { libraryDao.deleteLibraryEntry(10L) }
    }

    @Test
    fun addToWishlist_whenNoCatalogRow_upsertsGameThenEntry() = runTest(testDispatcher) {
        coEvery { libraryDao.getLibraryEntry(7L) } returns null
        coEvery { gameDao.upsertGame(any()) } returns 7L
        coEvery { libraryDao.upsertLibraryEntry(any()) } returns 1L
        val game = Game(id = 7L, name = "Hades II")

        val result = repository.addToWishlist(game)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { gameDao.upsertGame(match { it.id == 7L && it.name == "Hades II" }) }
        coVerify(exactly = 1) {
            libraryDao.upsertLibraryEntry(match { it.gameId == 7L && it.status == LibraryStatus.WISHLIST })
        }
    }

    @Test
    fun addToWishlist_whenEntryExists_doesNotWrite() = runTest(testDispatcher) {
        coEvery { libraryDao.getLibraryEntry(7L) } returns LibraryEntryEntity(
            gameId = 7L,
            status = LibraryStatus.COMPLETED,
            addedAtEpochSeconds = 1L,
            updatedAtEpochSeconds = 1L,
        )
        val result = repository.addToWishlist(Game(id = 7L, name = "Hades II"))
        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { gameDao.upsertGame(any()) }
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun upsertUserEdits_preservesAddedAt_andClampsRating() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(7L) } returns GameEntity(
            7L, "Hades II", null, null, null, null, emptyList(), emptyList(), 1L,
        )
        coEvery { libraryDao.getLibraryEntry(7L) } returns LibraryEntryEntity(
            gameId = 7L,
            status = LibraryStatus.WISHLIST,
            userRating = 9,
            userNotes = "keep",
            isFavorite = true,
            addedAtEpochSeconds = 111L,
            updatedAtEpochSeconds = 111L,
            hoursPlayed = 3,
        )
        coEvery { libraryDao.upsertLibraryEntry(any()) } returns 1L

        val result = repository.upsertUserEdits(
            gameId = 7L,
            status = LibraryStatus.PLAYING,
            userRating = 99,
            hoursPlayed = 12,
            userNotes = "  fun  ",
            isFavorite = false,
        )
        assertTrue(result is AppResult.Success)
        coVerify {
            libraryDao.upsertLibraryEntry(
                match {
                    it.status == LibraryStatus.PLAYING &&
                        it.userRating == 10 &&
                        it.hoursPlayed == 12 &&
                        it.userNotes == "fun" &&
                        !it.isFavorite &&
                        it.addedAtEpochSeconds == 111L
                },
            )
        }
    }

    @Test
    fun upsertUserEdits_whenNoEntry_returnsError() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(7L) } returns GameEntity(
            7L, "Hades II", null, null, null, null, emptyList(), emptyList(), 1L,
        )
        coEvery { libraryDao.getLibraryEntry(7L) } returns null
        val result = repository.upsertUserEdits(7L, LibraryStatus.WISHLIST, null, 0, null, false)
        assertTrue(result is AppResult.Error)
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun upsertUserEdits_executesExistenceCheckAndWriteInTransaction() = runTest(testDispatcher) {
        var inTransaction = false
        var getInsideTransaction = false
        var upsertInsideTransaction = false
        val trackingRunner = object : TransactionRunner {
            override suspend fun <T> invoke(block: suspend () -> T): T {
                inTransaction = true
                try {
                    return block()
                } finally {
                    inTransaction = false
                }
            }
        }
        val trackingRepository = DefaultLibraryRepository(
            libraryDao,
            gameDao,
            trackingRunner,
            testDispatcher,
        )
        coEvery { gameDao.getGameById(7L) } returns GameEntity(
            7L, "Hades II", null, null, null, null, emptyList(), emptyList(), 1L,
        )
        coEvery { libraryDao.getLibraryEntry(7L) } answers {
            getInsideTransaction = inTransaction
            LibraryEntryEntity(
                gameId = 7L,
                status = LibraryStatus.WISHLIST,
                addedAtEpochSeconds = 1L,
                updatedAtEpochSeconds = 1L,
            )
        }
        coEvery { libraryDao.upsertLibraryEntry(any()) } answers {
            upsertInsideTransaction = inTransaction
            1L
        }

        val result = trackingRepository.upsertUserEdits(
            gameId = 7L,
            status = LibraryStatus.PLAYING,
            userRating = 8,
            hoursPlayed = 1,
            userNotes = null,
            isFavorite = false,
        )

        assertTrue(result is AppResult.Success)
        assertTrue(getInsideTransaction)
        assertTrue(upsertInsideTransaction)
    }


}
