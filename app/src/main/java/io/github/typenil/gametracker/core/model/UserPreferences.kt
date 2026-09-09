package io.github.typenil.gametracker.core.model

/**
 * Local user preferences. Recommendation fields are cold-start signals only:
 * library evidence outranks them as it accumulates.
 */
data class UserPreferences(
    val recommendationGenres: Set<String> = emptySet(),
    val recommendationPlatforms: Set<String> = emptySet(),
    val recommendationOnboardingDismissed: Boolean = false,
) {
    val hasUsableColdStart: Boolean
        get() = recommendationGenres.size >= MIN_COLD_START_GENRES &&
            recommendationPlatforms.isNotEmpty()

    companion object {
        const val MIN_COLD_START_GENRES = 3

        fun isValidColdStart(genres: Set<String>, platforms: Set<String>): Boolean {
            if (genres.size < MIN_COLD_START_GENRES || platforms.isEmpty()) return false
            if (!genres.all { it in RecommendationGenreCatalog.wireNames }) return false
            return platforms.all { RecommendationPlatformFamily.fromStorageId(it) != null }
        }
    }
}
