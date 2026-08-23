package io.github.typenil.gametracker.core.network.datasource

import io.github.typenil.gametracker.core.network.api.BffApiService
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto
import javax.inject.Inject

/**
 * Production implementation of [BffRemoteDataSource] communicating with the Ktor BFF over HTTP.
 */
class RetrofitBffDataSource @Inject constructor(
    private val apiService: BffApiService
) : BffRemoteDataSource {

    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
        return apiService.getTopRatedGames(limit = limit, offset = offset)
    }

    override suspend fun getTrendingGames(limit: Int, offset: Int): List<GameDto> {
        return apiService.getTrendingGames(limit = limit, offset = offset)
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
        return apiService.searchGames(query = query, limit = limit, offset = offset)
    }

    override suspend fun getGameDetails(id: Long): GameDetailsDto {
        return apiService.getGameDetails(id = id)
    }

    override suspend fun getRecommendationCandidates(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
    ): List<RecommendationCandidateDto> {
        return apiService.getRecommendationCandidates(
            genres = genres.csvOrNull(),
            themes = themes.csvOrNull(),
            platforms = platforms.csvOrNull(),
            exclude = exclude.csvOrNull(),
            similarTo = similarTo.csvOrNull(),
            limit = limit,
        )
    }

    private fun List<String>.csvOrNull(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",")

    private fun Collection<Long>.csvOrNull(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",")
}
