package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import javax.inject.Inject

/**
 * Seeds the demo flavor's starter library, exactly once per install.
 *
 * Resolution rule — "library is empty" alone is not enough, see [InstallEra]:
 * 1. marker already resolved → do nothing; an empty library is then the user's own doing;
 * 2. library not empty, or this install was updated in place → resolve without writing;
 * 3. otherwise seed, and resolve the marker **only when at least one entry actually landed**, so a
 *    seed that failed outright (broken fixtures, unresolvable details) retries on the next launch
 *    instead of leaving the demo permanently empty.
 *
 * Every entry goes through [LibraryRepository.saveLibraryEntry] — the same parent-first path the UI
 * uses — and each one is best effort, so a single unresolvable game cannot stop the rest.
 */
class DemoLibrarySeeder @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val seedMarker: DemoSeedMarker,
    private val installEra: InstallEra,
) : LibrarySeeder {

    override suspend fun seedIfEmpty() {
        if (seedMarker.isResolved()) return
        if (!isUntouchedFreshInstall()) {
            seedMarker.markResolved()
            return
        }
        if (writeSeedEntries() > 0) seedMarker.markResolved()
    }

    private suspend fun isUntouchedFreshInstall(): Boolean {
        if (!installEra.isFreshInstall()) return false
        return libraryDao.getAllLibraryEntries().isEmpty()
    }

    private suspend fun writeSeedEntries(): Int {
        val now = System.currentTimeMillis() / 1000
        var written = 0
        for (entry in seedEntries(now)) {
            val refreshed = gameRepository.refreshGameDetails(entry.gameId, force = false)
            if (refreshed !is AppResult.Success) continue
            if (libraryRepository.saveLibraryEntry(entry) is AppResult.Success) written++
        }
        return written
    }

    private fun seedEntries(now: Long): List<LibraryEntry> = listOf(
        // Released in 2015: nothing to be notified about.
        LibraryEntry(
            gameId = WITCHER_ID, status = LibraryStatus.COMPLETED, userRating = 10, hoursPlayed = 150,
            isFavorite = true,
            addedAtEpochSeconds = now - 30 * SECONDS_PER_DAY, updatedAtEpochSeconds = now - 5 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = MYSTERY_INDIE_ID, status = LibraryStatus.PLAYING, hoursPlayed = 12,
            addedAtEpochSeconds = now - 10 * SECONDS_PER_DAY, updatedAtEpochSeconds = now - 1 * SECONDS_PER_DAY,
        ),
        // Release date is still TBA, so this is the seeded entry that exercises the enabled state.
        LibraryEntry(
            gameId = GTA_VI_ID, status = LibraryStatus.WISHLIST,
            isFavorite = true, releaseNotificationsEnabled = true,
            addedAtEpochSeconds = now - 20 * SECONDS_PER_DAY, updatedAtEpochSeconds = now - 20 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = DOOM_1993_ID, status = LibraryStatus.COMPLETED, userRating = 9, hoursPlayed = 25,
            addedAtEpochSeconds = now - 40 * SECONDS_PER_DAY, updatedAtEpochSeconds = now - 15 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = ARENA_PRO_ID, status = LibraryStatus.DROPPED, userRating = 4, hoursPlayed = 8,
            addedAtEpochSeconds = now - 15 * SECONDS_PER_DAY, updatedAtEpochSeconds = now - 2 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = SPACE_INVADERS_ID, status = LibraryStatus.NOT_INTERESTED,
            addedAtEpochSeconds = now - 50 * SECONDS_PER_DAY, updatedAtEpochSeconds = now - 50 * SECONDS_PER_DAY,
        ),
    )

    private companion object {
        const val SECONDS_PER_DAY = 86_400L

        const val WITCHER_ID = 1942L
        const val GTA_VI_ID = 900003L
        const val SPACE_INVADERS_ID = 900004L
        const val DOOM_1993_ID = 900005L
        const val MYSTERY_INDIE_ID = 900009L
        const val ARENA_PRO_ID = 900012L
    }
}
