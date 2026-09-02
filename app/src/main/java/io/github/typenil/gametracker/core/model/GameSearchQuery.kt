package io.github.typenil.gametracker.core.model

/**
 * Pure domain model representing a structured game search and catalog filter query.
 * Free of Android/Compose UI and Room cache formatting dependencies.
 */
data class GameSearchQuery(
    val query: String = "",
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val minRating: Int? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val sort: String? = null,
) {
    val hasConstraints: Boolean
        get() = genres.isNotEmpty() ||
            platforms.isNotEmpty() ||
            minRating != null ||
            minYear != null ||
            maxYear != null

    val shouldSearch: Boolean
        get() = query.isNotBlank() || hasConstraints
}
