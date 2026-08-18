package io.github.typenil.gametracker.feature.details.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation key representing the Game Details destination for a specific [gameId].
 */
@Serializable
data class GameDetailsKey(
    val gameId: Long
)
