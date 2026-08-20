package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Default implementation of [GameRepository] with Room database write-through caching (SSOT).
 */
class DefaultGameRepository @Inject constructor(
    private val remoteDataSource: BffRemoteDataSource,
    private val gameDao: GameDao,
    private val searchDao: SearchDao,
    private val transactionRunner: TransactionRunner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : GameRepository {

    override suspend fun getTopRatedGames(limit: Int, offset: Int): AppResult<List<Game>> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val remoteGames = remoteDataSource.getTopRatedGames(limit = limit, offset = offset).toDomain()
                val nowSeconds = System.currentTimeMillis() / 1000
                gameDao.upsertGames(remoteGames.map { it.toEntity(nowSeconds) })
                remoteGames
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): AppResult<List<Game>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return AppResult.Success(emptyList())
        }

        return withContext(ioDispatcher) {
            runSuspendCatching {
                val normalizedQuery = normalizeSearchQuery(trimmed)
                val remoteGames = remoteDataSource.searchGames(
                    query = trimmed,
                    limit = limit,
                    offset = offset
                ).toDomain()
                val nowSeconds = System.currentTimeMillis() / 1000

                transactionRunner {
                    gameDao.upsertGames(remoteGames.map { it.toEntity(nowSeconds) })
                    val existingQuery = searchDao.getSearchQuery(normalizedQuery)
                    searchDao.upsertSearchQuery(
                        SearchQueryEntity(
                            query = normalizedQuery,
                            createdAtEpochSeconds = existingQuery?.createdAtEpochSeconds ?: nowSeconds,
                            lastQueriedAtEpochSeconds = nowSeconds,
                            resultCount = remoteGames.size
                        )
                    )
                    searchDao.deleteSearchResultsForQuery(normalizedQuery)
                    val crossRefs = remoteGames.mapIndexed { index, game ->
                        SearchResultCrossRef(
                            query = normalizedQuery,
                            gameId = game.id,
                            position = offset + index
                        )
                    }
                    searchDao.insertSearchResults(crossRefs)
                }

                remoteGames
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    override suspend fun getGameDetails(id: Long): AppResult<Game> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val remoteGame = remoteDataSource.getGameDetails(id = id).toDomain()
                val nowSeconds = System.currentTimeMillis() / 1000
                gameDao.upsertGame(remoteGame.toEntity(nowSeconds))
                remoteGame
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    private fun normalizeSearchQuery(query: String): String {
        return query.trim().lowercase(Locale.ROOT)
    }
}
