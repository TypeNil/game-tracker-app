package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import javax.inject.Inject

class DemoLibrarySeeder @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
) : LibrarySeeder {

    override suspend fun seedIfEmpty() {
        if (libraryDao.getAllLibraryEntries().isNotEmpty()) return
        val now = System.currentTimeMillis() / 1000

        val seedEntries = listOf(
            LibraryEntry(
                gameId = WITCHER_ID,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                userRating = 10,
                hoursPlayed = 150,
                addedAtEpochSeconds = now - 86400 * 30,
                updatedAtEpochSeconds = now - 86400 * 5,
            ),
            LibraryEntry(
                gameId = MYSTERY_INDIE_ID,
                status = LibraryStatus.PLAYING,
                isFavorite = false,
                userRating = null,
                hoursPlayed = 12,
                addedAtEpochSeconds = now - 86400 * 10,
                updatedAtEpochSeconds = now - 86400 * 1,
            ),
            LibraryEntry(
                gameId = GTA_VI_ID,
                status = LibraryStatus.WISHLIST,
                isFavorite = true,
                userRating = null,
                hoursPlayed = 0,
                addedAtEpochSeconds = now - 86400 * 20,
                updatedAtEpochSeconds = now - 86400 * 20,
            ),
            LibraryEntry(
                gameId = DOOM_1993_ID,
                status = LibraryStatus.COMPLETED,
                isFavorite = false,
                userRating = 9,
                hoursPlayed = 25,
                addedAtEpochSeconds = now - 86400 * 40,
                updatedAtEpochSeconds = now - 86400 * 15,
            ),
            LibraryEntry(
                gameId = ARENA_PRO_ID,
                status = LibraryStatus.DROPPED,
                isFavorite = false,
                userRating = 4,
                hoursPlayed = 8,
                addedAtEpochSeconds = now - 86400 * 15,
                updatedAtEpochSeconds = now - 86400 * 2,
            ),
            LibraryEntry(
                gameId = SPACE_INVADERS_ID,
                status = LibraryStatus.NOT_INTERESTED,
                isFavorite = false,
                userRating = null,
                hoursPlayed = 0,
                addedAtEpochSeconds = now - 86400 * 50,
                updatedAtEpochSeconds = now - 86400 * 50,
            ),
        )

        for (entry in seedEntries) {
            when (gameRepository.refreshGameDetails(entry.gameId, force = false)) {
                is AppResult.Error -> continue
                is AppResult.Success -> {
                    libraryRepository.saveLibraryEntry(entry)
                }
            }
        }
    }

    private companion object {
        const val WITCHER_ID = 1942L
        const val GTA_VI_ID = 900003L
        const val SPACE_INVADERS_ID = 900004L
        const val DOOM_1993_ID = 900005L
        const val MYSTERY_INDIE_ID = 900009L
        const val ARENA_PRO_ID = 900012L
    }
}
