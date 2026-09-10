package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DefaultLibraryBackupRepositoryTest {

    private val libraryDao: LibraryDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC)
    private val repository = DefaultLibraryBackupRepository(
        libraryDao = libraryDao,
        gameDao = gameDao,
        transactionRunner = transactionRunner,
        ioDispatcher = testDispatcher,
        clock = clock,
    )

    @Test
    fun exportLibrary_emptyLibrary_writesValidV1Document() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns emptyList()
        coEvery { gameDao.getGamesReferencedByLibrary() } returns emptyList()

        val result = repository.exportLibrary()

        assertTrue(result is AppResult.Success)
        val parsed = LibraryBackupCodec.decode((result as AppResult.Success).data)
        assertEquals(
            LibraryBackupFile(1, Instant.parse("2026-09-10T12:00:00Z"), emptyList()),
            (parsed as LibraryBackupParseResult.Success).file,
        )
        coVerify(exactly = 0) { gameDao.getGamesByIds(any()) }
    }

    @Test
    fun previewImport_countsNewAndConflicts() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            entryEntity(1L),
            entryEntity(2L),
        )

        val result = repository.previewImport(
            LibraryBackupFile(
                schemaVersion = 1,
                exportedAt = Instant.parse("2026-09-10T12:00:00Z"),
                items = listOf(backupItem(2L, LibraryStatus.PLAYING), backupItem(3L)),
            ),
        )

        assertEquals(
            LibraryImportPreview(foundCount = 2, newCount = 1, conflictCount = 1),
            (result as AppResult.Success).data,
        )
    }

    @Test
    fun importLibrary_merge_insertsNewOverwritesConflictsAndSkipsExistingCatalog() =
        runTest(testDispatcher) {
            coEvery { gameDao.getGameById(2L) } returns gameEntity(2L, name = "Local richer cache")
            coEvery { gameDao.getGameById(3L) } returns null

            val result = repository.importLibrary(
                LibraryBackupFile(
                    schemaVersion = 1,
                    exportedAt = Instant.parse("2026-09-10T12:00:00Z"),
                    items = listOf(
                        backupItem(2L, LibraryStatus.COMPLETED, rating = 8),
                        backupItem(3L, LibraryStatus.WISHLIST),
                    ),
                ),
                LibraryImportMode.MERGE,
            )

            assertTrue(result is AppResult.Success)
            coVerify(exactly = 0) { libraryDao.deleteAllLibraryEntries() }
            coVerify(exactly = 1) { gameDao.upsertGame(match { it.id == 3L }) }
            coVerify(exactly = 0) { gameDao.upsertGame(match { it.id == 2L }) }
            coVerify { libraryDao.upsertLibraryEntry(match { it.gameId == 2L && it.status == LibraryStatus.COMPLETED && it.userRating == 8 }) }
            coVerify { libraryDao.upsertLibraryEntry(match { it.gameId == 3L && it.status == LibraryStatus.WISHLIST }) }
        }

    @Test
    fun importLibrary_replace_deletesLocalLibraryThenWritesFile() = runTest(testDispatcher) {
        coEvery { gameDao.getGameById(3L) } returns null

        val result = repository.importLibrary(
            LibraryBackupFile(
                schemaVersion = 1,
                exportedAt = Instant.parse("2026-09-10T12:00:00Z"),
                items = listOf(backupItem(3L)),
            ),
            LibraryImportMode.REPLACE,
        )

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.deleteAllLibraryEntries() }
        coVerify { gameDao.upsertGame(match { it.id == 3L }) }
        coVerify { libraryDao.upsertLibraryEntry(match { it.gameId == 3L }) }
    }

    @Test
    fun importLibrary_replaceEmpty_clearsLibraryWithoutCatalogWrites() = runTest(testDispatcher) {
        val result = repository.importLibrary(
            LibraryBackupFile(1, Instant.parse("2026-09-10T12:00:00Z"), emptyList()),
            LibraryImportMode.REPLACE,
        )

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { libraryDao.deleteAllLibraryEntries() }
        coVerify(exactly = 0) { gameDao.upsertGame(any()) }
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun importLibrary_mergeEmpty_isNoOp() = runTest(testDispatcher) {
        val result = repository.importLibrary(
            LibraryBackupFile(1, Instant.parse("2026-09-10T12:00:00Z"), emptyList()),
            LibraryImportMode.MERGE,
        )

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { libraryDao.deleteAllLibraryEntries() }
        coVerify(exactly = 0) { gameDao.upsertGame(any()) }
        coVerify(exactly = 0) { libraryDao.upsertLibraryEntry(any()) }
    }

    @Test
    fun importLibrary_propagatesCancellation() = runTest(testDispatcher) {
        coEvery { libraryDao.deleteAllLibraryEntries() } throws CancellationException("cancelled")

        try {
            repository.importLibrary(
                LibraryBackupFile(1, Instant.parse("2026-09-10T12:00:00Z"), emptyList()),
                LibraryImportMode.REPLACE,
            )
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected: must not become AppResult.Error
        }
    }

    private fun backupItem(
        id: Long,
        status: LibraryStatus = LibraryStatus.WISHLIST,
        rating: Int? = null,
    ) = LibraryBackupItem(
        entry = LibraryEntry(
            gameId = id,
            status = status,
            userRating = rating,
            addedAtEpochSeconds = 10L,
            updatedAtEpochSeconds = 20L,
        ),
        game = Game(id = id, name = "Game $id"),
    )

    private fun entryEntity(id: Long) = LibraryEntryEntity(
        gameId = id,
        status = LibraryStatus.WISHLIST,
        addedAtEpochSeconds = 10L,
        updatedAtEpochSeconds = 20L,
    )

    private fun gameEntity(id: Long, name: String) = GameEntity(
        id = id,
        name = name,
        coverUrl = null,
        rating = null,
        releaseDateEpochSeconds = null,
        summary = null,
        genres = emptyList(),
        platforms = emptyList(),
        cachedAtEpochSeconds = 1L,
    )
}
