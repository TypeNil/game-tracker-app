package io.github.typenil.gametracker.core.model

import androidx.compose.runtime.Immutable

/**
 * Pure domain model representing a video game in the catalog.
 */
@Immutable
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
