package io.github.typenil.gametracker.core.model

/**
 * Local user preferences. Recommendation fields are cold-start signals only:
 * library evidence outranks them as it accumulates.
 */
data class UserPreferences(
    val recommendationGenres: Set<String> = emptySet(),
    val recommendationThemes: Set<String> = emptySet(),
    val recommendationPlatforms: Set<String> = emptySet(),
    val recommendationOnboardingDismissed: Boolean = false,
) {
    val selectedRecommendationTags: Set<String>
        get() = recommendationGenres + recommendationThemes

    val hasUsableColdStart: Boolean
        get() = isValidColdStart(selectedRecommendationTags, recommendationPlatforms)

    companion object {
        const val MIN_COLD_START_GENRES = 3

        fun isValidColdStart(tags: Set<String>, platforms: Set<String>): Boolean {
            if (tags.size < MIN_COLD_START_GENRES || platforms.isEmpty()) return false
            if (!tags.all { it in RecommendationTagCatalog.wireNames }) return false
            return platforms.all { RecommendationPlatformFamily.fromStorageId(it) != null }
        }
    }
}
