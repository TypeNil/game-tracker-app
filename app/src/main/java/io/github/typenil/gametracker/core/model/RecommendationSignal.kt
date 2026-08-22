package io.github.typenil.gametracker.core.model

/**
 * One library row turned into recommendation evidence.
 * Free of Android/Room/Compose dependencies.
 */
data class RecommendationSignal(
    val gameId: Long,
    val status: LibraryStatus,
    val userRating: Int? = null,
    val isFavorite: Boolean = false,
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
)
