package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for cached enriched game details.
 */
@Dao
interface GameDetailsDao {

    @Upsert
    suspend fun upsertDetails(details: GameDetailsEntity): Long

    @Query("SELECT * FROM game_details WHERE gameId = :gameId")
    fun getGameDetailsFlow(gameId: Long): Flow<GameDetailsEntity?>

    @Query("SELECT * FROM game_details WHERE gameId = :gameId")
    suspend fun getGameDetails(gameId: Long): GameDetailsEntity?

    @Query("DELETE FROM game_details WHERE cachedAtEpochSeconds < :staleThreshold")
    suspend fun deleteStaleDetails(staleThreshold: Long): Int
}
