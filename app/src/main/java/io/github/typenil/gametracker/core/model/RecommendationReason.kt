package io.github.typenil.gametracker.core.model

sealed interface RecommendationReason {
    data class GenreOverlap(val tags: List<String>) : RecommendationReason
    data class ThemeOverlap(val tags: List<String>) : RecommendationReason
    data class PlatformOverlap(val tags: List<String>) : RecommendationReason
    data object SimilarGame : RecommendationReason
    data object HighRating : RecommendationReason
    data object RecentRelease : RecommendationReason
}
