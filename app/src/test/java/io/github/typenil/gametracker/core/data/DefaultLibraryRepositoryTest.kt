package io.github.typenil.gametracker.core.data

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.DefaultLibraryRepository
import io.github.typenil.gametracker.core.data.recommendations.RoomRecommendationSignalCollector

import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.PopulatedLibraryGameEntity
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult

import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryEntryDraft
import io.github.typenil.gametracker.core.model.LibraryNotes

import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.Game

import io.mockk.coEvery

import io.mockk.coVerify
import io.mockk.slot

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

class DefaultLibraryRepositoryTest {

    private val libraryDao: LibraryDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val signalCollector: RoomRecommendationSignalCollector = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val passThroughTransactionRunner = object : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }
    private val repository = DefaultLibraryRepository(
        libraryDao,
        gameDao,
        passThroughTransactionRunner,
        signalCollector,
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
            val result = awaitItem()
            assertTrue(result is AppResult.Success)
            val games = (result as AppResult.Success).data
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
    fun getLibraryGamesFlow_emitsErrorWhenDaoThrows() = runTest(testDispatcher) {
        every { libraryDao.getPopulatedLibraryEntriesFlow() } returns flow {
            throw IllegalStateException("room down")
        }

        repository.getLibraryGamesFlow().test {
            val result = awaitItem()
            assertTrue(result is AppResult.Error)
            assertTrue((result as AppResult.Error).error is AppError.UnknownError)
            awaitComplete()
        }
    }

    @Test
    fun getLibraryEntryFlow_emitsErrorWhenDaoThrows() = runTest(testDispatcher) {
        every { libraryDao.getLibraryEntryFlow(1L) } returns flow {
            throw IllegalStateException("room down")
        }

        repository.getLibraryEntryFlow(1L).test {
            val result = awaitItem()
            assertTrue(result is AppResult.Error)
            assertTrue((result as AppResult.Error).error is AppError.UnknownError)
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
    fun acceptedNotes_surviveSaveAndReloadWithoutTruncation() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(42L) } returns GameEntity(
            42L, "G", null, null, null, null, emptyList(), emptyList(), 1L,
        )
        val captured = slot<LibraryEntryEntity>()
        coEvery { libraryDao.upsertLibraryEntry(capture(captured)) } returns 1L

        val rocket = String(intArrayOf(0x1F680), 0, 1)
        val notes = rocket.repeat(LibraryNotes.MAX_CODE_POINTS)
        assertEquals(LibraryNotes.MAX_CODE_POINTS, LibraryNotes.codePointCount(notes))

        val result = repository.saveLibraryEntry(
            LibraryEntry(
                gameId = 42L,
                status = LibraryStatus.PLAYING,
                userNotes = notes,
                addedAtEpochSeconds = 100L,
                updatedAtEpochSeconds = 100L,
            ),
        )

        assertTrue(result is AppResult.Success)
        assertEquals(notes, captured.captured.userNotes)
        assertEquals(
            LibraryNotes.MAX_CODE_POINTS,
            LibraryNotes.codePointCount(captured.captured.userNotes!!),
        )
    }

    @Test
    fun saveLibraryEntry_whenExisting_preservesOriginalAddedAt() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(42L) } returns parentGame(42L)
        coEvery { libraryDao.getLibraryEntry(42L) } returns LibraryEntryEntity(
            gameId = 42L,
            status = LibraryStatus.WISHLIST,
            addedAtEpochSeconds = 111L,
            updatedAtEpochSeconds = 111L,
        )
        val captured = slot<LibraryEntryEntity>()
        coEvery { libraryDao.upsertLibraryEntry(capture(captured)) } returns 1L

        val result = repository.saveLibraryEntry(
            LibraryEntry(
                gameId = 42L,
                status = LibraryStatus.COMPLETED,
                userRating = 8,
                hoursPlayed = 12,
                addedAtEpochSeconds = 999L,
                updatedAtEpochSeconds = 999L,
                releaseNotificationsEnabled = true,
            ),
        )

        assertTrue(result is AppResult.Success)
        assertEquals(111L, captured.captured.addedAtEpochSeconds)
        assertEquals(true, captured.captured.releaseNotificationsEnabled)
    }

    @Test
    fun saveLibraryEntry_whenNew_usesInjectedClock() = runTest(testDispatcher) {
        val nowSeconds = 1_700_000_000L
        val clocked = DefaultLibraryRepository(
            libraryDao,
            gameDao,
            passThroughTransactionRunner,
            signalCollector,
            testDispatcher,
            Clock.fixed(Instant.ofEpochSecond(nowSeconds), ZoneOffset.UTC),
        )
        coEvery { gameDao.getGameById(42L) } returns parentGame(42L)
        coEvery { libraryDao.getLibraryEntry(42L) } returns null
        val captured = slot<LibraryEntryEntity>()
        coEvery { libraryDao.upsertLibraryEntry(capture(captured)) } returns 1L

        val result = clocked.saveLibraryEntry(
            LibraryEntry(
                gameId = 42L,
                status = LibraryStatus.PLAYING,
                addedAtEpochSeconds = 0L,
                updatedAtEpochSeconds = 0L,
            ),
        )

        assertTrue(result is AppResult.Success)
        assertEquals(nowSeconds, captured.captured.addedAtEpochSeconds)
        assertEquals(nowSeconds, captured.captured.updatedAtEpochSeconds)
    }

    @Test
    fun saveLibraryEntry_parentCheckAndUpsertRunInsideTransaction() = runTest(testDispatcher) {
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
            signalCollector,
            testDispatcher,
        )
        coEvery { gameDao.getGameById(42L) } answers {
            getInsideTransaction = inTransaction
            parentGame(42L)
        }
        coEvery { libraryDao.getLibraryEntry(42L) } returns null
        coEvery { libraryDao.upsertLibraryEntry(any()) } answers {
            upsertInsideTransaction = inTransaction
            1L
        }

        val result = trackingRepository.saveLibraryEntry(
            LibraryEntry(
                gameId = 42L,
                status = LibraryStatus.PLAYING,
                addedAtEpochSeconds = 1L,
                updatedAtEpochSeconds = 1L,
            ),
        )

        assertTrue(result is AppResult.Success)
        assertTrue(getInsideTransaction)
        assertTrue(upsertInsideTransaction)
    }

    @Test
    fun saveLibraryEntry_clampsRatingHoursAndNotes() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(42L) } returns parentGame(42L)
        coEvery { libraryDao.getLibraryEntry(42L) } returns null
        val captured = slot<LibraryEntryEntity>()
        coEvery { libraryDao.upsertLibraryEntry(capture(captured)) } returns 1L
        val tooLong = "x".repeat(LibraryNotes.MAX_CODE_POINTS + 8)

        val result = repository.saveLibraryEntry(
            LibraryEntry(
                gameId = 42L,
                status = LibraryStatus.PLAYING,
                userRating = 99,
                hoursPlayed = -4,
                userNotes = tooLong,
                addedAtEpochSeconds = 1L,
                updatedAtEpochSeconds = 1L,
            ),
        )

        assertTrue(result is AppResult.Success)
        assertEquals(10, captured.captured.userRating)
        assertEquals(0, captured.captured.hoursPlayed)
        assertEquals(LibraryNotes.MAX_CODE_POINTS, LibraryNotes.codePointCount(captured.captured.userNotes!!))
    }

    private fun parentGame(id: Long) = GameEntity(
        id, "G", null, null, null, null, emptyList(), emptyList(), 1L,
    )



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
                match {
                    it.gameId == 10L &&
                        it.status == LibraryStatus.WISHLIST &&
                        // Changing status is not a notification subscription.
                        !it.releaseNotificationsEnabled
                }
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
    fun setGameStatus_whenEntryExists_usesInjectedClock() = runTest(testDispatcher) {
        val nowSeconds = 1_700_000_000L
        val clocked = DefaultLibraryRepository(
            libraryDao,
            gameDao,
            passThroughTransactionRunner,
            signalCollector,
            testDispatcher,
            Clock.fixed(Instant.ofEpochSecond(nowSeconds), ZoneOffset.UTC),
        )
        coEvery { libraryDao.updateStatus(10L, LibraryStatus.PLAYING, nowSeconds) } returns 1

        val result = clocked.setGameStatus(10L, LibraryStatus.PLAYING)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.updateStatus(10L, LibraryStatus.PLAYING, nowSeconds) }
    }

    @Test
    fun removeGameFromLibrary_deletesEntry() = runTest(testDispatcher) {
        coEvery { libraryDao.deleteLibraryEntry(10L) } returns 1

        val result = repository.removeGameFromLibrary(10L)
        assertTrue(result is AppResult.Success)
        coVerify { libraryDao.deleteLibraryEntry(10L) }
    }

    @Test
    fun updateHoursPlayed_whenEntryExists_updatesSuccessfully() = runTest(testDispatcher) {
        coEvery { libraryDao.updateHoursPlayed(10L, 75, any()) } returns 1

        val result = repository.updateHoursPlayed(10L, 75)
        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.updateHoursPlayed(10L, 75, any()) }
    }

    @Test
    fun updateHoursPlayed_withoutExistingEntry_returnsError() = runTest(testDispatcher) {
        coEvery { libraryDao.updateHoursPlayed(999L, 75, any()) } returns 0

        val result = repository.updateHoursPlayed(999L, 75)
        assertTrue(result is AppResult.Error)
    }

    @Test
    fun updateHoursPlayed_clampsHoursToRange() = runTest(testDispatcher) {
        coEvery { libraryDao.updateHoursPlayed(10L, 999_999, any()) } returns 1
        coEvery { libraryDao.updateHoursPlayed(10L, 0, any()) } returns 1

        val overResult = repository.updateHoursPlayed(10L, 1_500_000)
        assertTrue(overResult is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.updateHoursPlayed(10L, 999_999, any()) }

        val underResult = repository.updateHoursPlayed(10L, -10)
        assertTrue(underResult is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.updateHoursPlayed(10L, 0, any()) }
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
            libraryDao.upsertLibraryEntry(
                match {
                    it.gameId == 7L &&
                        it.status == LibraryStatus.WISHLIST &&
                        // A one-tap add must not silently subscribe the user to release reminders.
                        !it.releaseNotificationsEnabled
                },
            )
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
            draft = LibraryEntryDraft(
                status = LibraryStatus.PLAYING,
                userRating = 99,
                hoursPlayed = 12,
                userNotes = "  fun  ",
                isFavorite = false,
                releaseNotificationsEnabled = true,
            ),
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
                        it.addedAtEpochSeconds == 111L &&
                        it.releaseNotificationsEnabled
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
        val result = repository.upsertUserEdits(
            7L,
            LibraryEntryDraft(
                status = LibraryStatus.WISHLIST,
                userRating = null,
                hoursPlayed = 0,
                userNotes = null,
                isFavorite = false,
                releaseNotificationsEnabled = false,
            ),
        )
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
            signalCollector,
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
            draft = LibraryEntryDraft(
                status = LibraryStatus.PLAYING,
                userRating = 8,
                hoursPlayed = 1,
                userNotes = null,
                isFavorite = false,
                releaseNotificationsEnabled = false,
            ),
        )

        assertTrue(result is AppResult.Success)
        assertTrue(getInsideTransaction)
        assertTrue(upsertInsideTransaction)
    }

    @Test
    fun recommendationSignals_areReadInsideOneTransaction() = runTest(testDispatcher) {
        var inTransaction = false
        var collectInsideTransaction = false
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
        val trackingCollector = mockk<RoomRecommendationSignalCollector>()
        coEvery { trackingCollector.collect() } answers {
            collectInsideTransaction = inTransaction
            emptyList()
        }
        val trackingRepository = DefaultLibraryRepository(
            libraryDao,
            gameDao,
            trackingRunner,
            trackingCollector,
            testDispatcher,
        )

        val result = trackingRepository.getRecommendationSignals()

        assertTrue(result is AppResult.Success)
        assertTrue(collectInsideTransaction)
    }



}
