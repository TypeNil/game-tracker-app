package io.github.typenil.gametracker.core.network.model

import kotlinx.serialization.Serializable

/**
 * Network DTOs matching the enriched GET /v1/games/{id} BFF response (Mobile Contract v1).
 */
@Serializable
data class ReleaseDateDto(
    val platform: String,
    val dateEpochSeconds: Long? = null,
    val year: Int? = null
)

@Serializable
data class CompanyDto(
    val name: String,
    val isDeveloper: Boolean = false,
    val isPublisher: Boolean = false
)

@Serializable
data class VideoDto(
    val videoId: String,
    val name: String? = null
)

@Serializable
data class SimilarGameDto(
    val id: Long,
    val name: String? = null,
    val coverUrl: String? = null,
    val totalRating: Double? = null
)

/**
 * Enriched details response of the BFF. The first eight fields mirror the list
 * contract of [GameDto] without renames; the remaining fields are details-only.
 */
@Serializable
data class GameDetailsDto(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val url: String? = null,
    val totalRating: Double? = null,
    val totalRatingCount: Long? = null,
    val themes: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val releaseDates: List<ReleaseDateDto> = emptyList(),
    val companies: List<CompanyDto> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val videos: List<VideoDto> = emptyList(),
    val similarGames: List<SimilarGameDto> = emptyList()
)
