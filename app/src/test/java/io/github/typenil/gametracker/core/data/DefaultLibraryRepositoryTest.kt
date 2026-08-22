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
    private val repository = DefaultLibraryRepository(libraryDao, gameDao, testDispatcher)

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
    fun removeGameFromLibrary_deletesEntry() = runTest(testDispatcher) {
        coEvery { libraryDao.deleteLibraryEntry(10L) } returns 1

        val result = repository.removeGameFromLibrary(10L)
        assertTrue(result is AppResult.Success)
        coVerify { libraryDao.deleteLibraryEntry(10L) }
    }
}
