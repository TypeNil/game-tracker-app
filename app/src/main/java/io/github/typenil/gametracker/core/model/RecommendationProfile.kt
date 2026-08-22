package io.github.typenil.gametracker.core.model

/**
 * Normalized taste profile derived from [RecommendationSignal]s.
 * Weights per axis are in `[-1, 1]` after max-abs normalization.
 */
data class RecommendationProfile(
    val genreWeights: Map<String, Float>,
    val themeWeights: Map<String, Float>,
    val platformWeights: Map<String, Float>,
    val excludedGameIds: Set<Long>,
    val isColdStart: Boolean,
)
