package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationSignal

import kotlinx.coroutines.flow.Flow

interface LibraryRepository {

    fun getLibraryGamesFlow(): Flow<AppResult<List<LibraryGame>>>

    fun getLibraryEntryFlow(gameId: Long): Flow<AppResult<LibraryEntry?>>

    suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit>

    suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit>

    /**
     * Parent-first Wishlist insert. No-op Success if an entry already exists.
     */
    suspend fun addToWishlist(game: Game): AppResult<Unit>

    /**
     * Updates an existing library entry. Preserves [LibraryEntry.addedAtEpochSeconds].
     */
    suspend fun upsertUserEdits(
        gameId: Long,
        status: LibraryStatus,
        userRating: Int?,
        hoursPlayed: Int,
        userNotes: String?,
        isFavorite: Boolean,
    ): AppResult<Unit>

    /**
     * Atomically flips [LibraryEntry.isFavorite] for [gameId]. Other fields are unchanged.
     */
    suspend fun toggleFavorite(gameId: Long): AppResult<Unit>

    suspend fun updateHoursPlayed(gameId: Long, hoursPlayed: Int): AppResult<Unit>

    suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit>

    /**
     * Consistent library+catalog snapshot used to rank For You candidates.
     */
    suspend fun getRecommendationSignals(): AppResult<List<RecommendationSignal>> =
        AppResult.Success(emptyList())

}
