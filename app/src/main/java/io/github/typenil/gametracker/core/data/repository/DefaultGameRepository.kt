package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun searchGames(queryFlow: Flow<String>, limit: Int): Flow<AppResult<List<Game>>> {
        return queryFlow
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow {
                    if (query.isBlank()) {
                        emit(AppResult.Success(emptyList()))
                    } else {
                        emit(searchGames(query = query, limit = limit, offset = 0))
                    }
                }
            }
            .flowOn(ioDispatcher)
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

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
