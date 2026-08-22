package io.github.typenil.gametracker.core.network.model

import kotlinx.serialization.Serializable

/**
 * Network DTO for GET /v1/recommendations/candidates.
 * Richer than list [GameDto]: themes, ratingCount, and similar-seed ids.
 */
@Serializable
data class RecommendationCandidateDto(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val ratingCount: Long? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val similarToGameIds: List<Long> = emptyList(),
)
