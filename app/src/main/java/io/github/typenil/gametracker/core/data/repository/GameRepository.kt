package io.github.typenil.gametracker.core.data.repository

import androidx.paging.PagingData
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
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
     * Observes the paged stream of server-ranked top-rated games backed by Room SSOT and RemoteMediator.
     * Caller should cache the flow (e.g. `cachedIn(viewModelScope)`) across UI subscriptions to prevent redundant mediator re-initialization.
     */
    fun getPagedTopRatedGames(pageSize: Int = 20): Flow<PagingData<Game>>

    /**
     * Refreshes the top-rated games catalog from remote BFF and updates Room SSOT.
     */
    suspend fun refreshTopRatedGames(limit: Int = 20, offset: Int = 0): AppResult<Unit>

    /**
     * Observes the reactive stream of server-ranked search results for the given [query] from Room SSOT.
     */
    fun getSearchResultsFlow(query: String): Flow<List<Game>>

    /**
     * Observes the paged stream of server-ranked search results backed by Room SSOT and RemoteMediator.
     * Caller should cache the flow (e.g. `cachedIn(viewModelScope)`) across UI subscriptions to prevent redundant mediator re-initialization.
     */
    fun getPagedSearchResults(query: String, pageSize: Int = 20): Flow<PagingData<Game>>

    /**
     * Fetches search results from remote BFF for [query] and updates Room SSOT.
     */
    suspend fun searchGames(query: String, limit: Int = 20, offset: Int = 0): AppResult<Unit>

    /**
     * Observes details for game with [id] from Room SSOT. Emits the full cached
     * details when present, otherwise a skeleton built from the catalog row
     * (so a first open offline still shows header data), otherwise null.
     */
    fun getGameDetailsFlow(id: Long): Flow<GameDetails?>

    /**
     * Observes whether the enriched details row is cached in Room. Lets the details
     * screen distinguish a hydrated model from a catalog skeleton without leaking
     * cache internals into the domain model, and detect eviction while visible.
     */
    fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean>

    /**
     * Refreshes details for game with [id] from remote BFF and updates Room SSOT.
     * Skips the network when the cached details row is younger than the details TTL
     * unless [force] is set (pull-to-refresh / retry).
     */
    suspend fun refreshGameDetails(id: Long, force: Boolean = false): AppResult<Unit>

    /**
     * Cleans up stale unreferenced cached games (excluding library and active search results).
     */
    suspend fun clearStaleCache(staleThresholdSeconds: Long): Int
}


