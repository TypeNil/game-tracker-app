package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point and SSOT for accessing video games catalog, search, and details.
 */
interface GameRepository {

    /**
     * Observes the reactive stream of server-ranked top-rated games from the local Room database (SSOT).
     */
    fun getTopRatedGamesFlow(): Flow<List<Game>>

    /**
     * Refreshes the top-rated games catalog from remote BFF and updates Room SSOT.
     */
    suspend fun refreshTopRatedGames(limit: Int = 20, offset: Int = 0): AppResult<Unit>

    /**
     * Observes the reactive stream of server-ranked search results for the given [query] from Room SSOT.
     */
    fun getSearchResultsFlow(query: String): Flow<List<Game>>

    /**
     * Fetches search results from remote BFF for [query] and updates Room SSOT.
     */
    suspend fun searchGames(query: String, limit: Int = 20, offset: Int = 0): AppResult<Unit>

    /**
     * Observes a specific game by [id] from Room SSOT.
     */
    fun getGameDetailsFlow(id: Long): Flow<Game?>

    /**
     * Refreshes details for game with [id] from remote BFF and updates Room SSOT.
     */
    suspend fun refreshGameDetails(id: Long): AppResult<Unit>

    /**
     * Cleans up stale unreferenced cached games (excluding library and active search results).
     */
    suspend fun clearStaleCache(staleThresholdSeconds: Long): Int
}

