package io.github.typenil.gametracker.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for search queries and server-ranked search results.
 */
@Dao
@Suppress("TooManyFunctions")
interface SearchDao {

    companion object {
        const val SEARCH_RESULTS_BY_POSITION =
            "SELECT g.* FROM games g INNER JOIN search_results sr ON g.id = sr.gameId WHERE sr.query = :query ORDER BY sr.position ASC"
        const val DELETE_SEARCH_RESULTS_FROM_POSITION =
            "DELETE FROM search_results WHERE query = :query AND position >= :fromPosition"
    }

    @Upsert
    suspend fun upsertSearchQuery(query: SearchQueryEntity): Long

    @Query("SELECT * FROM search_queries WHERE query = :query")
    suspend fun getSearchQuery(query: String): SearchQueryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchResults(results: List<SearchResultCrossRef>): List<Long>

    @Query(SEARCH_RESULTS_BY_POSITION)
    fun getSearchResultsFlow(query: String): Flow<List<GameEntity>>

    @Query(SEARCH_RESULTS_BY_POSITION)
    suspend fun getSearchResults(query: String): List<GameEntity>

    @Query(SEARCH_RESULTS_BY_POSITION)
    fun getSearchResultsPagingSource(query: String): PagingSource<Int, GameEntity>

    @Query("SELECT * FROM search_queries ORDER BY lastQueriedAtEpochSeconds DESC LIMIT :limit")
    fun getRecentSearchQueriesFlow(limit: Int = 10): Flow<List<SearchQueryEntity>>

    @Query("DELETE FROM search_results WHERE query = :query")
    suspend fun deleteSearchResultsForQuery(query: String): Int

    @Query(DELETE_SEARCH_RESULTS_FROM_POSITION)
    suspend fun deleteSearchResultsFromPosition(query: String, fromPosition: Int): Int

    @Query("SELECT gameId FROM search_results WHERE query = :query")
    suspend fun getSearchResultGameIds(query: String): List<Long>

    /**
     * Dense = positions are exactly 0..COUNT-1 with no holes. Legacy caches written while
     * positions were taken from the server offset (with intra-page distinctBy compaction)
     * can be sparse; appending dense ordinals onto them would violate the unique
     * (query, position) index, so a dense window is a precondition for cache reuse.
     */
    @Query(
        """
        SELECT CASE
            WHEN COUNT(*) = 0 THEN 1
            WHEN MIN(position) = 0 AND MAX(position) = COUNT(*) - 1 THEN 1
            ELSE 0
        END
        FROM search_results WHERE query = :query
        """
    )
    suspend fun hasDenseSearchResultPositions(query: String): Boolean

    @Query("SELECT COUNT(*) FROM search_results WHERE query = :query")
    suspend fun countSearchResultsForQuery(query: String): Int

    @Query("DELETE FROM search_queries WHERE query = :query")
    suspend fun deleteSearchQuery(query: String): Int

    @Query("DELETE FROM search_queries WHERE lastQueriedAtEpochSeconds < :staleThreshold AND query NOT IN (:excludeQueries)")
    suspend fun deleteStaleSearchQueries(staleThreshold: Long, excludeQueries: List<String>): Int

    @Query("DELETE FROM search_queries")
    suspend fun clearAllSearchHistory(): Int
}
