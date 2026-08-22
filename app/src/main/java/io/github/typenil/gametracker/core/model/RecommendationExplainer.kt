package io.github.typenil.gametracker.core.model

object RecommendationExplainer {

    private val factorOrder = listOf("genre", "theme", "platform", "similar", "rating", "recency")

    fun explain(
        ranked: RankedRecommendation,
        profile: RecommendationProfile,
        weights: RankerWeights = RankerWeights(),
        maxReasons: Int = 2,
    ): List<RecommendationReason> {
        val factors = ranked.factors
        val candidate = ranked.candidate
        val scored = listOf(
            ScoredReason(
                key = "genre",
                contribution = weights.genreOverlap * factors.genreOverlap,
                reason = overlapReason(candidate.genres, profile.genreWeights, ::genreReason),
            ),
            ScoredReason(
                key = "theme",
                contribution = weights.themeOverlap * factors.themeOverlap,
                reason = overlapReason(candidate.themes, profile.themeWeights, ::themeReason),
            ),
            ScoredReason(
                key = "platform",
                contribution = weights.platformOverlap * factors.platformOverlap,
                reason = overlapReason(candidate.platforms, profile.platformWeights, ::platformReason),
            ),
            ScoredReason(
                key = "similar",
                contribution = weights.similarBoost * factors.similarBoost,
                reason = RecommendationReason.SimilarGame.takeIf { factors.similarBoost > 0f },
            ),
            ScoredReason(
                key = "rating",
                contribution = weights.rating * factors.bayesianRating,
                reason = RecommendationReason.HighRating.takeIf { factors.bayesianRating > 0f },
            ),
            ScoredReason(
                key = "recency",
                contribution = weights.recency * factors.recency,
                reason = RecommendationReason.RecentRelease.takeIf { factors.recency > 0f },
            ),
        )
        return scored
            .filter { it.contribution > 0f && it.reason != null }
            .sortedWith(
                compareByDescending<ScoredReason> { it.contribution }
                    .thenBy { factorOrder.indexOf(it.key) },
            )
            .take(maxReasons)
            .mapNotNull { it.reason }
    }

    private fun overlapReason(
        tags: List<String>,
        weights: Map<String, Float>,
        wrap: (List<String>) -> RecommendationReason,
    ): RecommendationReason? {
        val matched = tags.distinct().filter { (weights[it] ?: 0f) > 0f }.sorted()
        return matched.takeIf { it.isNotEmpty() }?.let(wrap)
    }

    private fun genreReason(tags: List<String>) = RecommendationReason.GenreOverlap(tags)
    private fun themeReason(tags: List<String>) = RecommendationReason.ThemeOverlap(tags)
    private fun platformReason(tags: List<String>) = RecommendationReason.PlatformOverlap(tags)

    private data class ScoredReason(
        val key: String,
        val contribution: Float,
        val reason: RecommendationReason?,
    )
}
