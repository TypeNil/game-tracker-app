package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.GameEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for cached video games.
 */
@Dao
interface GameDao {

    @Upsert
    suspend fun upsertGames(games: List<GameEntity>): List<Long>

    @Upsert
    suspend fun upsertGame(game: GameEntity): Long

    @Query("SELECT * FROM games WHERE id = :id")
    fun getGameByIdFlow(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    fun getGamesByIdsFlow(ids: List<Long>): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    suspend fun getGamesByIds(ids: List<Long>): List<GameEntity>

    @Query(
        """
        DELETE FROM games 
        WHERE id NOT IN (SELECT gameId FROM library_entries) 
          AND id NOT IN (SELECT gameId FROM search_results) 
          AND cachedAtEpochSeconds < :staleThreshold
        """
    )
    suspend fun deleteStaleUnsavedGames(staleThreshold: Long): Int
}
