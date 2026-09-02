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
import io.github.typenil.gametracker.core.model.AppErrorException
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.network.mapper.toAppError

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
        val metadata = searchDao.getSearchQuery(queryKey)
        val actualCount = searchDao.countSearchResultsForQuery(queryKey)
        // Legacy windows can be sparse (positions were taken from the server offset while
        // the persisted list was intra-page distinctBy-compacted). Appending dense ordinals
        // onto a sparse window would violate the unique (query, position) index forever,
        // so cache reuse additionally requires a dense window.
        val hasDensePositions = searchDao.hasDenseSearchResultPositions(queryKey)

        // A zero-row window is a valid cache only when the server reported a terminal list
        // (nextOffset == null). Zero rows with non-terminal or non-zero metadata mean the cache
        // is damaged or incomplete and must be refetched.
        val isStructurallyValid = remoteKey != null &&
            metadata != null &&
            actualCount == metadata.resultCount &&
            hasDensePositions &&
            (actualCount > 0 || remoteKey.nextOffset == null)
        val ageSeconds = remoteKey?.let { now - it.lastUpdatedEpochSeconds }
        // A future timestamp (device clock skew) must count as expired, never as fresh.
        val isFresh = ageSeconds != null && ageSeconds in 0 until ttlSeconds

        return if (isStructurallyValid && isFresh) {
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
                    endOfPaginationReached = true
                )
            }

            if (targetOffset > GameQueryKey.MAX_BFF_OFFSET) {
                return@runSuspendCatching MediatorResult.Success(endOfPaginationReached = true)
            }

            val loadSize = resolveLoadSize(loadType, state)
            val originalRemote = fetcher(loadSize, targetOffset)
            val isEndOfList = originalRemote.size < loadSize ||
                (targetOffset + originalRemote.size) > GameQueryKey.MAX_BFF_OFFSET
            val nextOffset = if (isEndOfList) null else targetOffset + originalRemote.size
            // Server cursor (nextOffset above, advanced by raw response size) and local display
            // positions (dense ordinals, computed in persistPage after cross-page deduplication)
            // are deliberately separate quantities.
            val pageGames = originalRemote.distinctBy { it.id }
            val nowSeconds = nowEpochSeconds()

            persistPage(loadType, pageGames, nextOffset, nowSeconds)

            if (loadType == LoadType.REFRESH) {
                runSuspendCatching { cleanupStaleCache(nowSeconds - GameQueryKey.GAME_STALE_TTL_SECONDS) }
            }

            MediatorResult.Success(endOfPaginationReached = isEndOfList)
        }.fold(
            onSuccess = { it },
            // Classify failures at the data boundary: presentation renders MediatorResult.Error
            // through AppErrorException without importing transport-specific mapping.
            onFailure = { throwable ->
                MediatorResult.Error(AppErrorException(throwable.toAppError(), throwable))
            }
        )
    }

    private suspend fun resolveAppendOffset(): Int? {
        val remoteKey = remoteKeyDao.getRemoteKey(queryKey) ?: return null
        return remoteKey.nextOffset
    }

    private fun resolveLoadSize(loadType: LoadType, state: PagingState<Int, GameEntity>): Int {
        return when (loadType) {
            LoadType.REFRESH -> state.config.initialLoadSize
            LoadType.APPEND, LoadType.PREPEND -> state.config.pageSize
        }
    }

    private suspend fun persistPage(
        loadType: LoadType,
        pageGames: List<Game>,
        nextOffset: Int?,
        nowSeconds: Long
    ) {
        transactionRunner {
            // Shared game payloads are refreshed for ALL distinct games of the page,
            // including ids already present in this query window (cross-page duplicates
            // still carry updated fields).
            gameDao.upsertGames(pageGames.map { it.toEntity(nowSeconds) })

            val existingQuery = searchDao.getSearchQuery(queryKey)
            // Exactly one pre-insert count per load: for APPEND its value is both the
            // dense local start ordinal and the provisional metadata count.
            val preInsertCount = searchDao.countSearchResultsForQuery(queryKey)

            // Guarantee the FK parent row before any cross-reference insert.
            searchDao.upsertSearchQuery(
                SearchQueryEntity(
                    query = queryKey,
                    createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                    lastQueriedAtEpochSeconds = nowSeconds,
                    resultCount = if (loadType == LoadType.REFRESH) 0 else preInsertCount
                )
            )

            if (loadType == LoadType.REFRESH) {
                searchDao.deleteSearchResultsForQuery(queryKey)
            }

            // Append-only window: previously presented rows are never moved or duplicated;
            // ids already persisted for this query are dropped from cross-reference insertion
            // (the server cursor nextOffset is unaffected by this filtering).
            val persistedIds = if (loadType == LoadType.APPEND) {
                searchDao.getSearchResultGameIds(queryKey).toSet()
            } else {
                emptySet()
            }
            val newGames = pageGames.filterNot { it.id in persistedIds }
            val localStart = if (loadType == LoadType.REFRESH) 0 else preInsertCount

            val crossRefs = newGames.mapIndexed { index, game ->
                SearchResultCrossRef(
                    query = queryKey,
                    gameId = game.id,
                    position = localStart + index
                )
            }
            searchDao.insertSearchResults(crossRefs)

            // Final integrity metadata: actual persisted window size after insertion.
            searchDao.upsertSearchQuery(
                SearchQueryEntity(
                    query = queryKey,
                    createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                    lastQueriedAtEpochSeconds = nowSeconds,
                    resultCount = searchDao.countSearchResultsForQuery(queryKey)
                )
            )

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
