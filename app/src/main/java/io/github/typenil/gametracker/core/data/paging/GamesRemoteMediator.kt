package io.github.typenil.gametracker.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.Game

/**
 * Paging 3 [RemoteMediator] coordinating network requests to BFF and local Room database SSOT.
 */
@OptIn(ExperimentalPagingApi::class)
class GamesRemoteMediator(
    private val queryKey: String,
    private val ttlSeconds: Long,
    private val fetcher: suspend (limit: Int, offset: Int) -> List<Game>,
    private val gameDao: GameDao,
    private val searchDao: SearchDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val transactionRunner: TransactionRunner,
    private val cleanupStaleCache: suspend (staleThresholdSeconds: Long) -> Int = { 0 },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) : RemoteMediator<Int, GameEntity>() {

    override suspend fun initialize(): InitializeAction {
        val now = nowEpochSeconds()
        val remoteKey = remoteKeyDao.getRemoteKey(queryKey)
        val hasLocalRows = searchDao.countSearchResultsForQuery(queryKey) > 0
        val isCacheValid = remoteKey != null &&
            (now - remoteKey.lastUpdatedEpochSeconds) < ttlSeconds &&
            hasLocalRows

        return if (isCacheValid) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, GameEntity>
    ): MediatorResult {
        return runSuspendCatching {
            val targetOffset = when (loadType) {
                LoadType.PREPEND -> return@runSuspendCatching MediatorResult.Success(endOfPaginationReached = true)
                LoadType.REFRESH -> 0
                LoadType.APPEND -> resolveAppendOffset() ?: return@runSuspendCatching MediatorResult.Success(
                    endOfPaginationReached = isAppendEndOfPagination()
                )
            }

            if (targetOffset > GameQueryKey.MAX_BFF_OFFSET) {
                return@runSuspendCatching MediatorResult.Success(endOfPaginationReached = true)
            }

            val loadSize = resolveLoadSize(loadType, state)
            val originalRemote = fetcher(loadSize, targetOffset)
            val isEndOfList = originalRemote.size < loadSize ||
                (targetOffset + originalRemote.size) >= GameQueryKey.MAX_BFF_OFFSET
            val nextOffset = if (isEndOfList) null else targetOffset + originalRemote.size
            val distinctGames = originalRemote.distinctBy { it.id }
            val nowSeconds = nowEpochSeconds()

            persistPage(loadType, targetOffset, distinctGames, nextOffset, nowSeconds)

            if (loadType == LoadType.REFRESH) {
                runSuspendCatching { cleanupStaleCache(nowSeconds - GameQueryKey.GAME_STALE_TTL_SECONDS) }
            }

            MediatorResult.Success(endOfPaginationReached = isEndOfList)
        }.fold(
            onSuccess = { it },
            onFailure = { MediatorResult.Error(it) }
        )
    }

    private suspend fun resolveAppendOffset(): Int? {
        val remoteKey = remoteKeyDao.getRemoteKey(queryKey) ?: return null
        return remoteKey.nextOffset
    }

    private suspend fun isAppendEndOfPagination(): Boolean {
        val remoteKey = remoteKeyDao.getRemoteKey(queryKey)
        return remoteKey != null && remoteKey.nextOffset == null
    }

    private fun resolveLoadSize(loadType: LoadType, state: PagingState<Int, GameEntity>): Int {
        return when (loadType) {
            LoadType.REFRESH -> state.config.initialLoadSize
            LoadType.APPEND, LoadType.PREPEND -> state.config.pageSize
        }
    }

    private suspend fun persistPage(
        loadType: LoadType,
        targetOffset: Int,
        distinctGames: List<Game>,
        nextOffset: Int?,
        nowSeconds: Long
    ) {
        transactionRunner {
            gameDao.upsertGames(distinctGames.map { it.toEntity(nowSeconds) })

            val existingQuery = searchDao.getSearchQuery(queryKey)
            val totalCount = if (loadType == LoadType.REFRESH) {
                distinctGames.size
            } else {
                (existingQuery?.resultCount ?: targetOffset) + distinctGames.size
            }
            searchDao.upsertSearchQuery(
                SearchQueryEntity(
                    query = queryKey,
                    createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                    lastQueriedAtEpochSeconds = nowSeconds,
                    resultCount = totalCount
                )
            )

            if (loadType == LoadType.REFRESH) {
                searchDao.deleteSearchResultsForQuery(queryKey)
            } else {
                searchDao.deleteSearchResultsFromPosition(queryKey, targetOffset)
            }

            val crossRefs = distinctGames.mapIndexed { index, game ->
                SearchResultCrossRef(
                    query = queryKey,
                    gameId = game.id,
                    position = targetOffset + index
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
    }
}
