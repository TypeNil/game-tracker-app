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

    companion object {
        const val GAME_BY_ID = "SELECT * FROM games WHERE id = :id"
    }

    @Upsert
    suspend fun upsertGames(games: List<GameEntity>): List<Long>

    @Upsert
    suspend fun upsertGame(game: GameEntity): Long

    @Query(GAME_BY_ID)
    fun getGameByIdFlow(id: Long): Flow<GameEntity?>

    @Query(GAME_BY_ID)
    suspend fun getGameById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    fun getGamesByIdsFlow(ids: List<Long>): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    suspend fun getGamesByIds(ids: List<Long>): List<GameEntity>

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGameById(id: Long): Int

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
