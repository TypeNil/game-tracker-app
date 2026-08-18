package io.github.typenil.gametracker.core.network.datasource

import io.github.typenil.gametracker.core.network.model.GameDto

/**
 * Abstraction for remote BFF data fetching.
 */
interface BffRemoteDataSource {
    suspend fun getTopRatedGames(limit: Int = 20, offset: Int = 0): List<GameDto>
    suspend fun searchGames(query: String, limit: Int = 20, offset: Int = 0): List<GameDto>
    suspend fun getGameDetails(id: Long): GameDto
}
