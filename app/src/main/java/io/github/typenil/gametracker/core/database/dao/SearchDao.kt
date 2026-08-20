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
interface SearchDao {

    @Upsert
    suspend fun upsertSearchQuery(query: SearchQueryEntity): Long

    @Query("SELECT * FROM search_queries WHERE query = :query")
    suspend fun getSearchQuery(query: String): SearchQueryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchResults(results: List<SearchResultCrossRef>): List<Long>

    @Query(
        """
        SELECT g.* FROM games g
        INNER JOIN search_results sr ON g.id = sr.gameId
        WHERE sr.query = :query
        ORDER BY sr.position ASC
        """
    )
    fun getSearchResultsFlow(query: String): Flow<List<GameEntity>>

    @Query(
        """
        SELECT g.* FROM games g
        INNER JOIN search_results sr ON g.id = sr.gameId
        WHERE sr.query = :query
        ORDER BY sr.position ASC
        """
    )
    suspend fun getSearchResults(query: String): List<GameEntity>

    @Query(
        """
        SELECT g.* FROM games g
        INNER JOIN search_results sr ON g.id = sr.gameId
        WHERE sr.query = :query
        ORDER BY sr.position ASC
        """
    )
    fun getSearchResultsPagingSource(query: String): PagingSource<Int, GameEntity>

    @Query("SELECT * FROM search_queries ORDER BY lastQueriedAtEpochSeconds DESC LIMIT :limit")
    fun getRecentSearchQueriesFlow(limit: Int = 10): Flow<List<SearchQueryEntity>>

    @Query("DELETE FROM search_results WHERE query = :query")
    suspend fun deleteSearchResultsForQuery(query: String): Int

    @Query("DELETE FROM search_queries WHERE query = :query")
    suspend fun deleteSearchQuery(query: String): Int

    @Query("DELETE FROM search_queries")
    suspend fun clearAllSearchHistory(): Int
}
