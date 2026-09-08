package io.github.typenil.gametracker.core.network.datasource

import io.github.typenil.gametracker.core.network.api.BffApiService
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto
import javax.inject.Inject

/**
 * Production implementation of [BffRemoteDataSource] communicating with the Ktor BFF over HTTP.
 */
private const val IGDB_PC_PLATFORM = "PC (Microsoft Windows)"

private fun canonicalizePlatform(platform: String): String {
    val trimmed = platform.trim()
    return if (trimmed.equals("PC", ignoreCase = true) || trimmed.equals(IGDB_PC_PLATFORM, ignoreCase = true)) {
        IGDB_PC_PLATFORM
    } else {
        trimmed
    }
}

class RetrofitBffDataSource @Inject constructor(
    private val apiService: BffApiService
) : BffRemoteDataSource {

    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
        return apiService.getTopRatedGames(limit = limit, offset = offset)
    }

    override suspend fun getTrendingGames(limit: Int, offset: Int): List<GameDto> {
        return apiService.getTrendingGames(limit = limit, offset = offset)
    }
    override suspend fun getPopularPage(
        type: String,
        limit: Int,
        offset: Int
    ): io.github.typenil.gametracker.core.network.model.GamePageDto {
        return apiService.getPopularPage(type = type, limit = limit, offset = offset)
    }

    override suspend fun searchGames(
        query: String?,
        genres: List<String>,
        platforms: List<String>,
        minRating: Int?,
        minYear: Int?,
        maxYear: Int?,
        sort: String?,
        limit: Int,
        offset: Int,
    ): List<GameDto> {
        return apiService.searchGames(
            query = query?.takeIf { it.isNotBlank() },
            genres = genres.takeIf { it.isNotEmpty() },
            platforms = platforms.map(::canonicalizePlatform).takeIf { it.isNotEmpty() },
            minRating = minRating,
            minYear = minYear,
            maxYear = maxYear,
            sort = sort?.takeIf { it.isNotBlank() },
            limit = limit,
            offset = offset,
        )
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
            genres = genres.takeIf { it.isNotEmpty() },
            themes = themes.takeIf { it.isNotEmpty() },
            platforms = platforms.map(::canonicalizePlatform).takeIf { it.isNotEmpty() },
            exclude = exclude.csvOrNull(),
            similarTo = similarTo.csvOrNull(),
            limit = limit,
        )
    }
    override suspend fun getRecommendationCandidatesPage(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
        offset: Int,
        sort: String,
    ): io.github.typenil.gametracker.core.network.model.RecommendationCandidatePageDto {
        return apiService.getRecommendationCandidatesPage(
            genres = genres.takeIf { it.isNotEmpty() },
            themes = themes.takeIf { it.isNotEmpty() },
            platforms = platforms.map(::canonicalizePlatform).takeIf { it.isNotEmpty() },
            exclude = exclude.csvOrNull(),
            similarTo = similarTo.csvOrNull(),
            limit = limit,
            offset = offset,
            sort = sort,
        )
    }

    private fun Collection<Long>.csvOrNull(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",")
}
