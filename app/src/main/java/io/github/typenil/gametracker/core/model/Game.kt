package io.github.typenil.gametracker.core.model

/**
 * Pure domain model representing a video game in the catalog.
 * Free of Android/Compose UI framework dependencies.
 */
data class Game(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList()
)
