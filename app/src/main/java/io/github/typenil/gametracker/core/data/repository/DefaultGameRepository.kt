package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
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
import java.util.Locale
import javax.inject.Inject

/**
 * Default implementation of [GameRepository] with Room database Single Source of Truth (SSOT).
 */
class DefaultGameRepository @Inject constructor(
    private val remoteDataSource: BffRemoteDataSource,
    private val gameDao: GameDao,
    private val searchDao: SearchDao,
    private val transactionRunner: TransactionRunner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : GameRepository {

    override fun getTopRatedGamesFlow(): Flow<List<Game>> {
        return searchDao.getSearchResultsFlow(KEY_DISCOVER_TOP_RATED)
            .map { it.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshTopRatedGames(limit: Int, offset: Int): AppResult<Unit> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val remoteGames = remoteDataSource.getTopRatedGames(limit = limit, offset = offset).toDomain()
                val nowSeconds = System.currentTimeMillis() / 1000

                transactionRunner {
                    gameDao.upsertGames(remoteGames.map { it.toEntity(nowSeconds) })
                    val existingQuery = searchDao.getSearchQuery(KEY_DISCOVER_TOP_RATED)
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = KEY_DISCOVER_TOP_RATED,
                            createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                            lastQueriedAtEpochSeconds = nowSeconds,
                            resultCount = remoteGames.size
                        )
                    )
                    searchDao.deleteSearchResultsForQuery(KEY_DISCOVER_TOP_RATED)
                    val crossRefs = remoteGames.mapIndexed { index, game ->
                        SearchResultCrossRef(
                            query = KEY_DISCOVER_TOP_RATED,
                            gameId = game.id,
                            position = offset + index
                        )
                    }
                    searchDao.insertSearchResults(crossRefs)
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
        val cacheKey = searchCacheKey(trimmed)
        return searchDao.getSearchResultsFlow(cacheKey)
            .map { it.toDomain() }
            .flowOn(ioDispatcher)
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): AppResult<Unit> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return AppResult.Success(Unit)
        }

        return withContext(ioDispatcher) {
            runSuspendCatching {
                val cacheKey = searchCacheKey(trimmed)
                val remoteGames = remoteDataSource.searchGames(
                    query = trimmed,
                    limit = limit,
                    offset = offset
                ).toDomain()
                val nowSeconds = System.currentTimeMillis() / 1000

                transactionRunner {
                    gameDao.upsertGames(remoteGames.map { it.toEntity(nowSeconds) })
                    val existingQuery = searchDao.getSearchQuery(cacheKey)
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = cacheKey,
                            createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                            lastQueriedAtEpochSeconds = nowSeconds,
                            resultCount = remoteGames.size
                        )
                    )
                    searchDao.deleteSearchResultsForQuery(cacheKey)
                    val crossRefs = remoteGames.mapIndexed { index, game ->
                        SearchResultCrossRef(
                            query = cacheKey,
                            gameId = game.id,
                            position = offset + index
                        )
                    }
                    searchDao.insertSearchResults(crossRefs)
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
                val nowSeconds = System.currentTimeMillis() / 1000
                gameDao.upsertGame(remoteGame.toEntity(nowSeconds))
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int {
        return withContext(ioDispatcher) {
            gameDao.deleteStaleUnsavedGames(staleThresholdSeconds)
        }
    }

    companion object {
        const val KEY_DISCOVER_TOP_RATED = "discover:top-rated"
        const val KEY_PREFIX_SEARCH = "q:"

        fun searchCacheKey(rawQuery: String): String {
            return KEY_PREFIX_SEARCH + rawQuery.trim().lowercase(Locale.ROOT)
        }
    }
}

