package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point and SSOT for managing user's personal game library records.
 */
interface LibraryRepository {

    /**
     * Observes the reactive stream of all user library games joined with their game catalog entities.
     */
    fun getLibraryGamesFlow(): Flow<List<LibraryGame>>

    /**
     * Observes the library entry record for a specific [gameId].
     */
    fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntry?>

    /**
     * Updates or sets the library [status] for game [gameId].
     */
    suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit>

    /**
     * Saves or updates a full [LibraryEntry] record (ratings, notes, hours, favorites).
     */
    suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit>

    /**
     * Removes game [gameId] from user's library.
     */
    suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit>
}
