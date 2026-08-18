package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game

/**
 * Single entry point for accessing video games catalog and search.
 */
interface GameRepository {

    /**
     * Fetches top-rated games with pagination.
     */
    suspend fun getTopRatedGames(limit: Int = 20, offset: Int = 0): AppResult<List<Game>>

    /**
     * Executes a single search query against the catalog.
     */
    suspend fun searchGames(query: String, limit: Int = 20, offset: Int = 0): AppResult<List<Game>>

    /**
     * Fetches details for a specific game by its ID.
     */
    suspend fun getGameDetails(id: Long): AppResult<Game>
}
