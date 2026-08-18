package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Default implementation of [GameRepository] backed by [BffRemoteDataSource].
 */
class DefaultGameRepository @Inject constructor(
    private val remoteDataSource: BffRemoteDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : GameRepository {

    override suspend fun getTopRatedGames(limit: Int, offset: Int): AppResult<List<Game>> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                remoteDataSource.getTopRatedGames(limit = limit, offset = offset).toDomain()
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
                remoteDataSource.searchGames(query = trimmed, limit = limit, offset = offset).toDomain()
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }

    override suspend fun getGameDetails(id: Long): AppResult<Game> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                remoteDataSource.getGameDetails(id = id).toDomain()
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.toAppError()) }
            )
        }
    }
}
