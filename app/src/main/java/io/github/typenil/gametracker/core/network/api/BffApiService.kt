package io.github.typenil.gametracker.core.network.api

import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the Ktor Backend for Frontend (BFF) Mobile API v1.
 */
interface BffApiService {

    @GET("v1/discover/top-rated")
    suspend fun getTopRatedGames(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): List<GameDto>

    @GET("v1/discover/trending")
    suspend fun getTrendingGames(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<GameDto>

    @GET("v1/games/search")
    suspend fun searchGames(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): List<GameDto>

    @GET("v1/games/{id}")
    suspend fun getGameDetails(
        @Path("id") id: Long
    ): GameDetailsDto

    @GET("v1/recommendations/candidates")
    suspend fun getRecommendationCandidates(
        @Query("genres") genres: String? = null,
        @Query("themes") themes: String? = null,
        @Query("platforms") platforms: String? = null,
        @Query("exclude") exclude: String? = null,
        @Query("similarTo") similarTo: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<RecommendationCandidateDto>
}
