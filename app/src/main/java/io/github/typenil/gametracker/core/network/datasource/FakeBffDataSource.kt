package io.github.typenil.gametracker.core.network.datasource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.typenil.gametracker.core.network.model.GameDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of [BffRemoteDataSource] used in the `demo` flavor.
 * Fully offline: loads local asset covers and enforces the same parameter validations as the Ktor BFF.
 */
@Singleton
class FakeBffDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json
) : BffRemoteDataSource {

    private val mockGames: List<GameDto> by lazy {
        try {
            context.assets.open("fixtures/v1/games.json")
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    json.decodeFromString<List<GameDto>>(reader.readText())
                }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse games.json fixture: ${e.message}", e)
        }
    }

    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
        validatePagination(limit, offset)
        if (offset >= mockGames.size) return emptyList()
        return mockGames.drop(offset).take(limit.coerceIn(1, MAX_LIMIT))
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
        if (query.isBlank()) {
            throw IllegalArgumentException("Search query 'q' parameter cannot be blank")
        }
        validatePagination(limit, offset)

        val trimmed = query.trim()
        val filtered = mockGames.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.genres.any { genre -> genre.contains(trimmed, ignoreCase = true) }
        }
        if (offset >= filtered.size) return emptyList()
        return filtered.drop(offset).take(limit.coerceIn(1, MAX_LIMIT))
    }

    override suspend fun getGameDetails(id: Long): GameDto {
        if (id <= 0) {
            throw IllegalArgumentException("Game ID must be a positive integer")
        }
        return mockGames.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Game with id $id not found")
    }

    private fun validatePagination(limit: Int, offset: Int) {
        if (limit <= 0) {
            throw IllegalArgumentException("Query parameter 'limit' must be a positive integer")
        }
        if (offset < 0) {
            throw IllegalArgumentException("Query parameter 'offset' cannot be negative")
        }
    }

    companion object {
        private const val MAX_LIMIT = 30
    }
}
