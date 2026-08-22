package io.github.typenil.gametracker.core.model

data class RankerWeights(
    val genreOverlap: Float = 1.0f,
    val themeOverlap: Float = 0.8f,
    val platformOverlap: Float = 0.5f,
    val similarBoost: Float = 1.5f,
    val rating: Float = 0.4f,
    val negativePenalty: Float = 1.0f,
    val recency: Float = 0.0f,
)

data class RecommendationFactors(
    val genreOverlap: Float,
    val themeOverlap: Float,
    val platformOverlap: Float,
    val similarBoost: Float,
    val bayesianRating: Float,
    val negativePenalty: Float,
    val recency: Float,
)

data class RankedRecommendation(
    val candidate: RecommendationCandidate,
    val score: Float,
    val factors: RecommendationFactors,
)

/**
 * Deterministic ranker. Time-based recency uses [nowEpochSeconds], never wall clock.
 */
object RecommendationRanker {
    const val BAYESIAN_C = 70.0
    const val BAYESIAN_M = 10.0
    const val RECENCY_HORIZON_YEARS = 10.0
    private const val SECONDS_PER_YEAR = 365.25 * 86400.0

    fun rank(
        profile: RecommendationProfile,
        candidates: List<RecommendationCandidate>,
        nowEpochSeconds: Long,
        weights: RankerWeights = RankerWeights(),
    ): List<RankedRecommendation> {
        return candidates
            .filterNot { it.gameId in profile.excludedGameIds }
            .map { candidate ->
                val factors = factors(profile, candidate, nowEpochSeconds)
                RankedRecommendation(
                    candidate = candidate,
                    score = score(factors, weights),
                    factors = factors,
                )
            }
            .sortedWith(compareByDescending<RankedRecommendation> { it.score }.thenBy { it.candidate.gameId })
    }

    fun bayesian(rating: Double?, ratingCount: Long?): Float {
        if (rating == null) return 0f
        val votes = (ratingCount ?: 0L).toDouble()
        val adjusted = (votes / (votes + BAYESIAN_M)) * rating +
            (BAYESIAN_M / (votes + BAYESIAN_M)) * BAYESIAN_C
        return (adjusted / 100.0).toFloat()
    }

    fun recency(releaseEpochSeconds: Long?, nowEpochSeconds: Long): Float {
        if (releaseEpochSeconds == null) return 0f
        val ageYears = (nowEpochSeconds - releaseEpochSeconds).coerceAtLeast(0) / SECONDS_PER_YEAR
        return (1.0 - (ageYears / RECENCY_HORIZON_YEARS).coerceIn(0.0, 1.0)).toFloat()
    }

    private fun factors(
        profile: RecommendationProfile,
        candidate: RecommendationCandidate,
        nowEpochSeconds: Long,
    ): RecommendationFactors {
        val genrePos = positiveOverlap(profile.genreWeights, candidate.genres)
        val themePos = positiveOverlap(profile.themeWeights, candidate.themes)
        val platformPos = positiveOverlap(profile.platformWeights, candidate.platforms)
        val negative = negativeOverlap(profile.genreWeights, candidate.genres) +
            negativeOverlap(profile.themeWeights, candidate.themes) +
            negativeOverlap(profile.platformWeights, candidate.platforms)
        return RecommendationFactors(
            genreOverlap = genrePos,
            themeOverlap = themePos,
            platformOverlap = platformPos,
            similarBoost = if (candidate.similarToGameIds.isNotEmpty()) 1f else 0f,
            bayesianRating = bayesian(candidate.rating, candidate.ratingCount),
            negativePenalty = negative,
            recency = recency(candidate.releaseDateEpochSeconds, nowEpochSeconds),
        )
    }

    private fun score(factors: RecommendationFactors, weights: RankerWeights): Float =
        weights.genreOverlap * factors.genreOverlap +
            weights.themeOverlap * factors.themeOverlap +
            weights.platformOverlap * factors.platformOverlap +
            weights.similarBoost * factors.similarBoost +
            weights.rating * factors.bayesianRating +
            weights.negativePenalty * factors.negativePenalty +
            weights.recency * factors.recency

    private fun positiveOverlap(weights: Map<String, Float>, tags: List<String>): Float =
        tags.distinct().sumOf { tag -> maxOf(0f, weights[tag] ?: 0f).toDouble() }.toFloat()

    private fun negativeOverlap(weights: Map<String, Float>, tags: List<String>): Float =
        tags.distinct().sumOf { tag -> minOf(0f, weights[tag] ?: 0f).toDouble() }.toFloat()
}
