package io.github.typenil.gametracker.core.network.model

import kotlinx.serialization.Serializable

/**
 * Network Data Transfer Object (DTO) matching the Ktor BFF Mobile Contract v1.
 */
@Serializable
data class GameDto(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList()
)
