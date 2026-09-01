package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing persistent user search history.
 */
@Dao
interface SearchHistoryDao {

    @Query(
        "SELECT displayQuery FROM search_history " +
            "ORDER BY lastQueriedAtEpochSeconds DESC, normalizedQuery ASC LIMIT :limit"
    )
    fun observeRecentSearchQueries(limit: Int): Flow<List<String>>

    @Upsert
    suspend fun upsertSearchHistory(entity: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE normalizedQuery = :normalized")
    suspend fun deleteSearchHistory(normalized: String): Int

    /**
     * Keeps the history table bounded: everything beyond the newest [keep] normalized queries is removed.
     */
    @Query(
        "DELETE FROM search_history WHERE normalizedQuery NOT IN (" +
            "SELECT normalizedQuery FROM search_history " +
            "ORDER BY lastQueriedAtEpochSeconds DESC, normalizedQuery ASC LIMIT :keep" +
            ")"
    )
    suspend fun trimSearchHistory(keep: Int): Int

    @Query("DELETE FROM search_history")
    suspend fun clearAllSearchHistory(): Int
}
