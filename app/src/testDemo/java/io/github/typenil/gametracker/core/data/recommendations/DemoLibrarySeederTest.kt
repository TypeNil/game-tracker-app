package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLibrarySeederTest {

    private val libraryDao: LibraryDao = mockk()
    private val gameRepository: GameRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk()
    private val seedMarker: DemoSeedMarker = mockk()
    private val installEra: InstallEra = mockk()

    private val seeder = DemoLibrarySeeder(
        libraryDao = libraryDao,
        gameRepository = gameRepository,
        libraryRepository = libraryRepository,
        seedMarker = seedMarker,
        installEra = installEra,
    )

    private fun givenInstall(isFresh: Boolean) {
        every { installEra.isFreshInstall() } returns isFresh
    }

    private fun givenMarker(resolved: Boolean) {
        coEvery { seedMarker.isResolved() } returns resolved
        coEvery { seedMarker.markResolved() } returns Unit
    }

    private fun givenStoredEntries(entries: List<LibraryEntryEntity>) {
        coEvery { libraryDao.getAllLibraryEntries() } returns entries
    }

    private fun givenDetailsResolve() {
        coEvery { gameRepository.refreshGameDetails(any(), force = false) } returns AppResult.Success(Unit)
    }

    @Test
    fun seedIfEmpty_resolvedMarker_neverWrites() = runTest {
        givenMarker(resolved = true)

        seeder.seedIfEmpty()

        coVerify(exactly = 0) { libraryRepository.saveLibraryEntry(any()) }
        coVerify(exactly = 0) { seedMarker.markResolved() }
    }

    @Test
    fun seedIfEmpty_existingLibrary_resolvesMarkerWithoutWriting() = runTest {
        givenMarker(resolved = false)
        givenInstall(isFresh = true)
        givenStoredEntries(listOf(mockk<LibraryEntryEntity>()))

        seeder.seedIfEmpty()

        coVerify(exactly = 0) { libraryRepository.saveLibraryEntry(any()) }
        coVerify(exactly = 1) { seedMarker.markResolved() }
    }

    @Test
    fun seedIfEmpty_updatedInstallWithEmptyLibrary_resolvesWithoutRepopulating() = runTest {
        givenMarker(resolved = false)
        givenInstall(isFresh = false)
        givenStoredEntries(emptyList())

        seeder.seedIfEmpty()

        coVerify(exactly = 0) { libraryRepository.saveLibraryEntry(any()) }
        coVerify(exactly = 1) { seedMarker.markResolved() }
    }

    @Test
    fun seedIfEmpty_freshInstallWithEmptyLibrary_writesEachGameOnceAndResolves() = runTest {
        givenMarker(resolved = false)
        givenInstall(isFresh = true)
        givenStoredEntries(emptyList())
        givenDetailsResolve()
        val written = mutableListOf<LibraryEntry>()
        coEvery { libraryRepository.saveLibraryEntry(capture(written)) } returns AppResult.Success(Unit)

        seeder.seedIfEmpty()

        assertTrue("expected the demo starter library to be written", written.isNotEmpty())
        assertEquals(
            "every seeded game must appear at most once",
            written.size,
            written.map { it.gameId }.distinct().size,
        )
        coVerify(exactly = 1) { seedMarker.markResolved() }
    }

    @Test
    fun seedIfEmpty_whenEveryEntryFailsToSave_keepsMarkerUnresolved() = runTest {
        givenMarker(resolved = false)
        givenInstall(isFresh = true)
        givenStoredEntries(emptyList())
        givenDetailsResolve()
        coEvery { libraryRepository.saveLibraryEntry(any()) } returns
            AppResult.Error(AppError.UnknownError(IllegalStateException("write failed")))

        seeder.seedIfEmpty()

        coVerify(exactly = 0) { seedMarker.markResolved() }
    }

    @Test
    fun seedIfEmpty_whenNoDetailsResolve_writesNothingAndKeepsMarkerUnresolved() = runTest {
        givenMarker(resolved = false)
        givenInstall(isFresh = true)
        givenStoredEntries(emptyList())
        coEvery { gameRepository.refreshGameDetails(any(), force = false) } returns
            AppResult.Error(AppError.UnknownError(IllegalStateException("fixtures missing")))

        seeder.seedIfEmpty()

        coVerify(exactly = 0) { libraryRepository.saveLibraryEntry(any()) }
        coVerify(exactly = 0) { seedMarker.markResolved() }
    }

    @Test
    fun seedIfEmpty_whenOnlySomeEntriesLand_resolvesMarker() = runTest {
        givenMarker(resolved = false)
        givenInstall(isFresh = true)
        givenStoredEntries(emptyList())
        givenDetailsResolve()
        coEvery { libraryRepository.saveLibraryEntry(any()) } returnsMany
            listOf(
                AppResult.Error(AppError.UnknownError(IllegalStateException("write failed"))),
                AppResult.Success(Unit),
            )

        seeder.seedIfEmpty()

        coVerify(exactly = 1) { seedMarker.markResolved() }
    }
}
