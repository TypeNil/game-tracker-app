package io.github.typenil.gametracker.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.data.paging.GameQueryKey
import io.github.typenil.gametracker.core.data.paging.GamesRemoteMediator
import io.github.typenil.gametracker.core.database.dao.GameDao
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
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Default implementation of [GameRepository] with Room database Single Source of Truth (SSOT).
 */
class DefaultGameRepository internal constructor(
    private val remoteDataSource: BffRemoteDataSource,
    private val gameDao: GameDao,
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
        searchDao: SearchDao,
        remoteKeyDao: RemoteKeyDao,
        transactionRunner: TransactionRunner,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        remoteDataSource = remoteDataSource,
        gameDao = gameDao,
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

    override fun getGameDetailsFlow(id: Long): Flow<Game?> {
        return gameDao.getGameByIdFlow(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshGameDetails(id: Long): AppResult<Unit> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val remoteGame = remoteDataSource.getGameDetails(id = id).toDomain()
                val nowSeconds = nowEpochSeconds()
                gameDao.upsertGame(remoteGame.toEntity(nowSeconds))
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
            val excludeKeys = listOf(GameQueryKey.KEY_DISCOVER_TOP_RATED)
            searchDao.deleteStaleSearchQueries(queryCutoffEpoch, excludeKeys)
            remoteKeyDao.deleteStaleRemoteKeys(queryCutoffEpoch, excludeKeys)
            gameDao.deleteStaleUnsavedGames(staleThresholdSeconds)
        }
    }
}
