package io.github.typenil.gametracker.core.model

/**
 * Pure domain model for the full game details screen.
 * Free of Android/Compose UI framework and serialization dependencies.
 *
 * Two rating scales coexist deliberately (IGDB semantics):
 * - [rating] is the critic-only rating — the same number list screens show and
 *   the catalog cache (`games` table) stores;
 * - [totalRating] with [totalRatingCount] votes is the IGDB aggregate
 *   (critics + players) shown on the details screen.
 * Never copy [totalRating] into [rating] or into a catalog row: the list
 * contract would silently change scale.
 */
data class GameDetails(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val totalRating: Double? = null,
    val totalRatingCount: Long? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val releaseDates: List<GameReleaseDate> = emptyList(),
    val companies: List<GameCompany> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val videos: List<GameVideo> = emptyList(),
    val similarGames: List<GameSummary> = emptyList(),
    val url: String? = null
)

data class GameReleaseDate(
    val platform: String,
    val dateEpochSeconds: Long? = null,
    val year: Int? = null
)

data class GameCompany(
    val name: String,
    val isDeveloper: Boolean = false,
    val isPublisher: Boolean = false
)

data class GameVideo(
    val videoId: String,
    val name: String? = null
)

/** Compact reference to a similar game; navigation to its details goes by [id]. */
data class GameSummary(
    val id: Long,
    val name: String? = null,
    val coverUrl: String? = null,
    val totalRating: Double? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
)
