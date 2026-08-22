package io.github.typenil.gametracker.core.model

/**
 * A recommendation pool item. Pure domain — no Android/Room/network types.
 */
data class RecommendationCandidate(
    val gameId: Long,
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
