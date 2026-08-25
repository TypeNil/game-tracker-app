package io.github.typenil.gametracker.core.data.repository

import androidx.paging.PagingData
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationCandidatePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    ): AppResult<Unit>
    suspend fun refreshPopular(
        type: String,
        limit: Int = 20,
        offset: Int = 0,
        append: Boolean = false,
    ): AppResult<Unit> = AppResult.Success(Unit)
    fun getPopularGamesFlow(type: String): Flow<List<Game>> = flowOf(emptyList())
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
    ): AppResult<RecommendationCandidatePage> = AppResult.Success(
        RecommendationCandidatePage(emptyList(), null, true),
    )
    fun getSearchResultsFlow(query: String): Flow<List<Game>>
    fun getPagedSearchResults(query: String, pageSize: Int = 20): Flow<PagingData<Game>>
    suspend fun searchGames(query: String, limit: Int = 20, offset: Int = 0): AppResult<Unit>
    fun getGameDetailsFlow(id: Long): Flow<GameDetails?>
    fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean>
    suspend fun refreshGameDetails(id: Long, force: Boolean = false): AppResult<Unit>
    suspend fun clearStaleCache(staleThresholdSeconds: Long): Int
}
