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
        when (gameRepository.refreshGameDetails(WITCHER_ID, force = false)) {
            is AppResult.Error -> return
            is AppResult.Success -> Unit
        }
        val now = System.currentTimeMillis() / 1000
        libraryRepository.saveLibraryEntry(
            LibraryEntry(
                gameId = WITCHER_ID,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                addedAtEpochSeconds = now,
                updatedAtEpochSeconds = now,
            )
        )
    }

    private companion object {
        const val WITCHER_ID = 1942L
    }
}
