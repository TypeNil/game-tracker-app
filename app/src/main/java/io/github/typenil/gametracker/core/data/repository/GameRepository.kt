package io.github.typenil.gametracker.core.data.repository

import androidx.paging.PagingData
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.PageContinuation

import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameSearchQuery
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationCandidatePage
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface GameRepository {
    fun getTopRatedGamesFlow(): Flow<List<Game>>
    fun getPagedTopRatedGames(pageSize: Int = 20): Flow<PagingData<Game>>
    suspend fun refreshTopRatedGames(limit: Int = 20, offset: Int = 0): AppResult<Unit>
    fun getTrendingGamesFlow(): Flow<List<Game>>
    suspend fun refreshTrendingGames(
        limit: Int = 20,
        offset: Int = 0,
        append: Boolean = false,
    ): AppResult<PageContinuation>
    suspend fun refreshPopular(
        type: String,
        limit: Int = 20,
        offset: Int = 0,
        append: Boolean = false,
    ): AppResult<PageContinuation>

    fun getPopularGamesFlow(type: String): Flow<List<Game>>
    suspend fun getRecommendationCandidates(
        genres: List<String> = emptyList(),
        themes: List<String> = emptyList(),
        platforms: List<String> = emptyList(),
        exclude: Set<Long> = emptySet(),
        similarTo: List<Long> = emptyList(),
        limit: Int = 30,
    ): AppResult<List<RecommendationCandidate>>
    suspend fun getRecommendationCandidatesPage(
        genres: List<String> = emptyList(),
        themes: List<String> = emptyList(),
        platforms: List<String> = emptyList(),
        exclude: Set<Long> = emptySet(),
        similarTo: List<Long> = emptyList(),
        limit: Int = 30,
        offset: Int = 0,
        sort: String = "follows",
    ): AppResult<RecommendationCandidatePage>
    fun getSearchResultsFlow(query: GameSearchQuery): Flow<List<Game>>
    fun getSearchResultsFlow(query: String): Flow<List<Game>> =
        getSearchResultsFlow(GameSearchQuery(query = query))
    fun getPagedSearchResults(
        query: GameSearchQuery,
        pageSize: Int = 20,
    ): Flow<PagingData<Game>>
    fun getPagedSearchResults(query: String, pageSize: Int = 20): Flow<PagingData<Game>> =
        getPagedSearchResults(GameSearchQuery(query = query), pageSize)
    /**
     * Records a user-issued search in the recent-queries history (normalized, trimmed to a bound).
     * Blank queries are user intent without a searchable term and are never recorded.
     */
    suspend fun recordSearchHistory(rawQuery: String): AppResult<Unit>
    suspend fun searchGames(
        query: GameSearchQuery,
        limit: Int = 20,
        force: Boolean = false,
    ): AppResult<Unit>
    suspend fun searchGames(query: String, limit: Int = 20, force: Boolean = false): AppResult<Unit> =
        searchGames(GameSearchQuery(query = query), limit, force)
    fun getRecentSearchQueriesFlow(limit: Int = 10): Flow<List<String>>
    suspend fun deleteSearchQuery(query: String): AppResult<Unit>
    suspend fun clearSearchHistory(): AppResult<Unit>
    fun recordPreview(game: Game) {}
    fun recordPreview(details: GameDetails) {}
    fun getInitialGameDetails(id: Long): GameDetails? = null
    fun getGameDetailsFlow(id: Long): Flow<GameDetails?>
    fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean>
    suspend fun refreshGameDetails(id: Long, force: Boolean = false): AppResult<Unit>
    suspend fun clearStaleCache(staleThresholdSeconds: Long): Int
}
