package io.github.typenil.gametracker.core.network.datasource

import io.github.typenil.gametracker.core.network.api.BffApiService
import io.github.typenil.gametracker.core.network.model.GameDto
import javax.inject.Inject

/**
 * Production implementation of [BffRemoteDataSource] communicating with the Ktor BFF over HTTP.
 */
class RetrofitBffDataSource @Inject constructor(
    private val apiService: BffApiService
) : BffRemoteDataSource {

    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
        return apiService.getTopRatedGames(limit = limit, offset = offset)
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
        return apiService.searchGames(query = query, limit = limit, offset = offset)
    }

    override suspend fun getGameDetails(id: Long): GameDto {
        return apiService.getGameDetails(id = id)
    }
}
