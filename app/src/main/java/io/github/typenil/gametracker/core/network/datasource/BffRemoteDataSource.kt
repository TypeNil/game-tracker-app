package io.github.typenil.gametracker.core.network.datasource

import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto

/**
 * Abstraction for remote BFF data fetching.
 */
interface BffRemoteDataSource {
    suspend fun getTopRatedGames(limit: Int = 20, offset: Int = 0): List<GameDto>
    suspend fun getTrendingGames(limit: Int = 20, offset: Int = 0): List<GameDto>
    suspend fun searchGames(query: String, limit: Int = 20, offset: Int = 0): List<GameDto>
    suspend fun getGameDetails(id: Long): GameDetailsDto
    suspend fun getRecommendationCandidates(
        genres: List<String> = emptyList(),
        themes: List<String> = emptyList(),
        platforms: List<String> = emptyList(),
        exclude: Set<Long> = emptySet(),
        similarTo: List<Long> = emptyList(),
        limit: Int = 30,
    ): List<RecommendationCandidateDto>
}
