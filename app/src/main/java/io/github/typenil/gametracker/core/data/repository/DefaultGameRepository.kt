package io.github.typenil.gametracker.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.data.paging.DiscoverRailKeys
import io.github.typenil.gametracker.core.data.paging.GameQueryKey
import io.github.typenil.gametracker.core.data.paging.GamesRemoteMediator
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.mapper.toDomain
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Default implementation of [GameRepository] with Room database Single Source of Truth (SSOT).
 */
@Suppress("TooManyFunctions")
class DefaultGameRepository internal constructor(
    private val remoteDataSource: BffRemoteDataSource,
    private val gameDao: GameDao,
    private val gameDetailsDao: GameDetailsDao,
    private val searchDao: SearchDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val transactionRunner: TransactionRunner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val nowEpochSeconds: () -> Long
) : GameRepository {

    @Inject
    constructor(
        remoteDataSource: BffRemoteDataSource,
        gameDao: GameDao,
        gameDetailsDao: GameDetailsDao,
        searchDao: SearchDao,
        remoteKeyDao: RemoteKeyDao,
        transactionRunner: TransactionRunner,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        remoteDataSource = remoteDataSource,
        gameDao = gameDao,
        gameDetailsDao = gameDetailsDao,
        searchDao = searchDao,
        remoteKeyDao = remoteKeyDao,
        transactionRunner = transactionRunner,
        ioDispatcher = ioDispatcher,
        nowEpochSeconds = { System.currentTimeMillis() / 1000 }
    )

    override fun getTopRatedGamesFlow(): Flow<List<Game>> {
        return searchDao.getSearchResultsFlow(GameQueryKey.KEY_DISCOVER_TOP_RATED)
            .map { it.toDomain() }
            .flowOn(ioDispatcher)
    }

    override fun getTrendingGamesFlow(): Flow<List<Game>> {
        return searchDao.getSearchResultsFlow(GameQueryKey.KEY_DISCOVER_TRENDING)
            .map { it.toDomain() }
            .flowOn(ioDispatcher)
    }


    @OptIn(ExperimentalPagingApi::class)
    override fun getPagedTopRatedGames(pageSize: Int): Flow<PagingData<Game>> {
        val safePageSize = pageSize.coerceIn(1, 30)
        val queryKey = GameQueryKey.KEY_DISCOVER_TOP_RATED
        val mediator = GamesRemoteMediator(
            queryKey = queryKey,
            ttlSeconds = GameQueryKey.DISCOVER_TTL_SECONDS,
            fetcher = { limit, offset ->
                remoteDataSource.getTopRatedGames(limit = limit, offset = offset).toDomain()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            cleanupStaleCache = ::clearStaleCache,
            nowEpochSeconds = nowEpochSeconds
        )
        return Pager(
            config = PagingConfig(
                pageSize = safePageSize,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = safePageSize
            ),
            remoteMediator = mediator,
            pagingSourceFactory = { searchDao.getSearchResultsPagingSource(queryKey) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun refreshTopRatedGames(limit: Int, offset: Int): AppResult<Unit> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val remoteGames = remoteDataSource.getTopRatedGames(limit = limit, offset = offset).toDomain()
                val nowSeconds = nowEpochSeconds()
                val queryKey = GameQueryKey.KEY_DISCOVER_TOP_RATED
                val isEndOfList = remoteGames.size < limit || (offset + remoteGames.size) > GameQueryKey.MAX_BFF_OFFSET
                val nextOffset = if (isEndOfList) null else offset + remoteGames.size
                val distinctGames = remoteGames.distinctBy { it.id }

                transactionRunner {
                    gameDao.upsertGames(distinctGames.map { it.toEntity(nowSeconds) })
                    val existingQuery = searchDao.getSearchQuery(queryKey)
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = queryKey,
                            createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                            lastQueriedAtEpochSeconds = nowSeconds,
                            resultCount = distinctGames.size
                        )
                    )
                    searchDao.deleteSearchResultsForQuery(queryKey)
                    val crossRefs = distinctGames.mapIndexed { index, game ->
                        SearchResultCrossRef(
                            query = queryKey,
                            gameId = game.id,
                            position = offset + index
                        )
                    }
                    searchDao.insertSearchResults(crossRefs)
                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            queryKey = queryKey,
                            prevOffset = null,
                            nextOffset = nextOffset,
                            lastUpdatedEpochSeconds = nowSeconds
                        )
                    )
                }

                runSuspendCatching {
                    clearStaleCache(nowSeconds - GameQueryKey.GAME_STALE_TTL_SECONDS)
                }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    override suspend fun refreshTrendingGames(limit: Int, offset: Int, append: Boolean): AppResult<Unit> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val remoteGames = remoteDataSource.getTrendingGames(limit = limit, offset = offset).toDomain()
                val nowSeconds = nowEpochSeconds()
                val queryKey = GameQueryKey.KEY_DISCOVER_TRENDING
                val isEndOfList = remoteGames.size < limit || (offset + remoteGames.size) > GameQueryKey.MAX_BFF_OFFSET
                val nextOffset = if (isEndOfList) null else offset + remoteGames.size
                val distinctGames = remoteGames.distinctBy { it.id }

                transactionRunner {
                    gameDao.upsertGames(distinctGames.map { it.toEntity(nowSeconds) })
                    val existingQuery = searchDao.getSearchQuery(queryKey)
                    val resultCount = if (append) {
                        (existingQuery?.resultCount ?: 0) + distinctGames.size
                    } else {
                        distinctGames.size
                    }
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = queryKey,
                            createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                            lastQueriedAtEpochSeconds = nowSeconds,
                            resultCount = resultCount
                        )
                    )
                    if (!append) {
                        searchDao.deleteSearchResultsForQuery(queryKey)
                    }
                    val crossRefs = distinctGames.mapIndexed { index, game ->
                        SearchResultCrossRef(
                            query = queryKey,
                            gameId = game.id,
                            position = offset + index
                        )
                    }
                    searchDao.insertSearchResults(crossRefs)
                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            queryKey = queryKey,
                            prevOffset = null,
                            nextOffset = nextOffset,
                            lastUpdatedEpochSeconds = nowSeconds
                        )
                    )
                }

                runSuspendCatching {
                    clearStaleCache(nowSeconds - GameQueryKey.GAME_STALE_TTL_SECONDS)
                }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }
    override fun getPopularGamesFlow(type: String): Flow<List<Game>> {
        return searchDao.getSearchResultsFlow(GameQueryKey.popular(type))
            .map { it.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshPopular(type: String, limit: Int, offset: Int, append: Boolean): AppResult<Unit> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val page = remoteDataSource.getPopularPage(type, limit, offset)
                val now = nowEpochSeconds()
                val queryKey = GameQueryKey.popular(type)
                val games = page.items.toDomain().distinctBy { it.id }
                transactionRunner {
                    gameDao.upsertGames(games.map { it.toEntity(now) })
                    val existing = searchDao.getSearchQuery(queryKey)
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = queryKey,
                            createdAtEpochSeconds = existing?.createdAtEpochSeconds ?: now,
                            lastQueriedAtEpochSeconds = now,
                            resultCount = if (append) (existing?.resultCount ?: 0) + games.size else games.size,
                        )
                    )
                    if (!append) searchDao.deleteSearchResultsForQuery(queryKey)
                    else searchDao.deleteSearchResultsFromPosition(queryKey, offset)
                    searchDao.insertSearchResults(games.mapIndexed { index, game ->
                        SearchResultCrossRef(queryKey, game.id, offset + index)
                    })
                    remoteKeyDao.upsert(
                        RemoteKeyEntity(queryKey, null, page.nextOffset, now)
                    )
                }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { AppResult.Error(it.toAppError()) },
            )
        }
    }

    override suspend fun getRecommendationCandidatesPage(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
        offset: Int,
        sort: String,
    ): AppResult<io.github.typenil.gametracker.core.model.RecommendationCandidatePage> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                remoteDataSource.getRecommendationCandidatesPage(
                    genres, themes, platforms, exclude, similarTo, limit, offset, sort,
                ).let { page ->
                    io.github.typenil.gametracker.core.model.RecommendationCandidatePage(
                        items = page.items.map { it.toDomain() },
                        nextOffset = page.nextOffset,
                        endReached = page.endReached,
                    )
                }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) },
            )
        }
    }


    override suspend fun getRecommendationCandidates(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
    ): AppResult<List<RecommendationCandidate>> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                remoteDataSource.getRecommendationCandidates(
                    genres = genres,
                    themes = themes,
                    platforms = platforms,
                    exclude = exclude,
                    similarTo = similarTo,
                    limit = limit,
                ).map { it.toDomain() }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }


    override fun getSearchResultsFlow(query: String): Flow<List<Game>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return flowOf(emptyList())
        }
        val cacheKey = GameQueryKey.search(trimmed)
        return searchDao.getSearchResultsFlow(cacheKey)
            .map { it.toDomain() }
            .flowOn(ioDispatcher)
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getPagedSearchResults(query: String, pageSize: Int): Flow<PagingData<Game>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return flowOf(PagingData.empty())
        }
        val safePageSize = pageSize.coerceIn(1, 30)
        val queryKey = GameQueryKey.search(trimmed)
        val mediator = GamesRemoteMediator(
            queryKey = queryKey,
            ttlSeconds = GameQueryKey.SEARCH_TTL_SECONDS,
            fetcher = { limit, offset ->
                remoteDataSource.searchGames(query = trimmed, limit = limit, offset = offset).toDomain()
            },
            gameDao = gameDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            cleanupStaleCache = ::clearStaleCache,
            nowEpochSeconds = nowEpochSeconds
        )
        return Pager(
            config = PagingConfig(
                pageSize = safePageSize,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = safePageSize
            ),
            remoteMediator = mediator,
            pagingSourceFactory = { searchDao.getSearchResultsPagingSource(queryKey) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): AppResult<Unit> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return AppResult.Success(Unit)
        }

        return withContext(ioDispatcher) {
            runSuspendCatching {
                val cacheKey = GameQueryKey.search(trimmed)
                val remoteGames = remoteDataSource.searchGames(
                    query = trimmed,
                    limit = limit,
                    offset = offset
                ).toDomain()
                val nowSeconds = nowEpochSeconds()
                val isEndOfList = remoteGames.size < limit || (offset + remoteGames.size) > GameQueryKey.MAX_BFF_OFFSET
                val nextOffset = if (isEndOfList) null else offset + remoteGames.size
                val distinctGames = remoteGames.distinctBy { it.id }

                transactionRunner {
                    gameDao.upsertGames(distinctGames.map { it.toEntity(nowSeconds) })
                    val existingQuery = searchDao.getSearchQuery(cacheKey)
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = cacheKey,
                            createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                            lastQueriedAtEpochSeconds = nowSeconds,
                            resultCount = distinctGames.size
                        )
                    )
                    searchDao.deleteSearchResultsForQuery(cacheKey)
                    val crossRefs = distinctGames.mapIndexed { index, game ->
                        SearchResultCrossRef(
                            query = cacheKey,
                            gameId = game.id,
                            position = offset + index
                        )
                    }
                    searchDao.insertSearchResults(crossRefs)
                    remoteKeyDao.upsert(
                        RemoteKeyEntity(
                            queryKey = cacheKey,
                            prevOffset = null,
                            nextOffset = nextOffset,
                            lastUpdatedEpochSeconds = nowSeconds
                        )
                    )
                }

                runSuspendCatching {
                    clearStaleCache(nowSeconds - GameQueryKey.GAME_STALE_TTL_SECONDS)
                }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    override fun getGameDetailsFlow(id: Long): Flow<GameDetails?> {
        // Full details row wins; otherwise the catalog row renders a skeleton, so a
        // first open offline still shows the header the user just saw on a list.
        // Both DAO flows emit immediately on subscription (combine gating, standard 3.5).
        return combine(
            gameDetailsDao.getGameDetailsFlow(id),
            gameDao.getGameByIdFlow(id)
        ) { details, game ->
            when {
                details != null -> details.toDomain()
                game != null -> game.toDomain().toDetailsSkeleton()
                else -> null
            }
        }.flowOn(ioDispatcher)
    }

    override fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean> {
        return gameDetailsDao.getGameDetailsFlow(id)
            .map { it != null }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshGameDetails(id: Long, force: Boolean): AppResult<Unit> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                // TTL gate reads ONLY game_details.cachedAt: the `games` catalog row is
                // refreshed by every Discover/Search fetch, so gating on it would pin
                // the screen to the skeleton for the whole TTL window.
                val cached = gameDetailsDao.getGameDetails(id)
                val isFresh = cached != null &&
                    nowEpochSeconds() - cached.cachedAtEpochSeconds < DETAILS_TTL_SECONDS
                if (!force && isFresh) return@runSuspendCatching

                val remoteDetails = remoteDataSource.getGameDetails(id = id).toDomain()
                val nowSeconds = nowEpochSeconds()
                transactionRunner {
                    // Parent-first: the slim catalog row must exist before anything may
                    // reference it (future library RESTRICT FK); details are written second.
                    gameDao.upsertGame(remoteDetails.toCatalogGame().toEntity(nowSeconds))
                    gameDetailsDao.upsertDetails(remoteDetails.toEntity(nowSeconds))
                }

                runSuspendCatching {
                    clearStaleCache(nowSeconds - GameQueryKey.GAME_STALE_TTL_SECONDS)
                }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }
    override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int {
        return withContext(ioDispatcher) {
            val now = nowEpochSeconds()
            val queryCutoffEpoch = now - GameQueryKey.SEARCH_TTL_SECONDS
            val excludeKeys = listOf(
                GameQueryKey.KEY_DISCOVER_TOP_RATED,
                GameQueryKey.KEY_DISCOVER_TRENDING,
            ) + DiscoverRailKeys.all()
            searchDao.deleteStaleSearchQueries(queryCutoffEpoch, excludeKeys)
            remoteKeyDao.deleteStaleRemoteKeys(queryCutoffEpoch, excludeKeys)
            gameDetailsDao.deleteStaleDetails(staleThresholdSeconds)
            gameDao.deleteStaleUnsavedGames(staleThresholdSeconds)
        }
    }

    companion object {
        /**
         * Client-side TTL of the enriched details cache (2 hours), mirroring the BFF
         * CachePolicy.GAME_DETAILS. Lives here and not in GameQueryKey: that type is
         * the SSOT of paged query keys, not of cache TTLs.
         */
        const val DETAILS_TTL_SECONDS = 2 * 60 * 60L
    }
}

/**
 * Skeleton details from a catalog row: eight list-contract fields, everything
 * else empty/null. rating is the critic rating the catalog already stores —
 * the UI falls back to it while the aggregate is unknown.
 */
private fun Game.toDetailsSkeleton(): GameDetails {
    return GameDetails(
        id = id,
        name = name,
        coverUrl = coverUrl,
        rating = rating,
        releaseDateEpochSeconds = releaseDateEpochSeconds,
        summary = summary,
        genres = genres,
        themes = themes,
        platforms = platforms
    )
}

/**
 * Slim catalog projection of details. Copies the critic `rating`, NEVER
 * `totalRating`: they are different IGDB scales, and writing the aggregate
 * here would silently rewrite the ratings of Discover/Search cards.
 */
private fun GameDetails.toCatalogGame(): Game {
    return Game(
        id = id,
        name = name,
        coverUrl = coverUrl,
        rating = rating,
        releaseDateEpochSeconds = releaseDateEpochSeconds,
        summary = summary,
        genres = genres,
        themes = themes,
        platforms = platforms
    )
}
