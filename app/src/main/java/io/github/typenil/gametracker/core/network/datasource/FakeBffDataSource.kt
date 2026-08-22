package io.github.typenil.gametracker.core.network.datasource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto
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
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Failed to read fixtures/v1/games.json fixture: ${e.message}", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Failed to parse fixtures/v1/games.json fixture: ${e.message}", e)
        }
    }

    /**
     * Enriched details fixture. Deliberately a separate file: list payloads stay
     * lean exactly like the real BFF contract, and details ids must cover every
     * `similarGames` reference of both fixtures (offline navigation closure).
     */
    private val mockDetails: List<GameDetailsDto> by lazy {
        try {
            context.assets.open("fixtures/v1/game-details.json")
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    json.decodeFromString<List<GameDetailsDto>>(reader.readText())
                }
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Failed to read fixtures/v1/game-details.json fixture: ${e.message}", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalStateException("Failed to parse fixtures/v1/game-details.json fixture: ${e.message}", e)
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

    override suspend fun getGameDetails(id: Long): GameDetailsDto {
        if (id <= 0) {
            throw IllegalArgumentException("Game ID must be a positive integer")
        }
        // No fallback into mockGames: serving a skinny object would hide fixture drift.
        return mockDetails.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Game with id $id not found")
    }

    override suspend fun getRecommendationCandidates(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
    ): List<RecommendationCandidateDto> {
        if (limit <= 0) {
            throw IllegalArgumentException("Query parameter 'limit' must be a positive integer")
        }
        if (exclude.any { it <= 0L } || similarTo.any { it <= 0L }) {
            throw IllegalArgumentException("exclude and similarTo must contain positive integers")
        }
        val hasTags = genres.isNotEmpty() || themes.isNotEmpty() || platforms.isNotEmpty()
        if (!hasTags && similarTo.isEmpty()) return emptyList()

        val blocked = exclude + similarTo
        val similarOwners = linkedMapOf<Long, MutableSet<Long>>()
        similarTo.forEach { seedId ->
            val seed = mockDetails.firstOrNull { it.id == seedId } ?: return@forEach
            seed.similarGames.forEach { similar ->
                if (similar.id !in blocked) {
                    similarOwners.getOrPut(similar.id) { mutableSetOf() }.add(seedId)
                }
            }
        }

        val merged = linkedMapOf<Long, RecommendationCandidateDto>()
        similarOwners.forEach { (id, seeds) ->
            toCandidate(id, seeds.sorted())?.let { merged[id] = it }
        }
        if (hasTags) {
            mockGames.forEach { game ->
                if (game.id in blocked) return@forEach
                val details = mockDetails.firstOrNull { it.id == game.id }
                val gameGenres = details?.genres ?: game.genres
                val gameThemes = details?.themes.orEmpty()
                val gamePlatforms = details?.platforms ?: game.platforms
                val matches = genres.any { it in gameGenres } ||
                    themes.any { it in gameThemes } ||
                    platforms.any { it in gamePlatforms }
                if (!matches) return@forEach
                val existing = merged[game.id]
                if (existing == null) {
                    toCandidate(game.id, emptyList())?.let { merged[game.id] = it }
                }
            }
        }
        return merged.values.take(limit.coerceIn(1, MAX_LIMIT))
    }

    private fun toCandidate(id: Long, similarToGameIds: List<Long>): RecommendationCandidateDto? {
        val details = mockDetails.firstOrNull { it.id == id }
        if (details != null) {
            return RecommendationCandidateDto(
                id = details.id,
                name = details.name,
                coverUrl = details.coverUrl,
                rating = details.rating,
                ratingCount = null,
                releaseDateEpochSeconds = details.releaseDateEpochSeconds,
                summary = details.summary,
                genres = details.genres,
                themes = details.themes,
                platforms = details.platforms,
                similarToGameIds = similarToGameIds,
            )
        }
        val game = mockGames.firstOrNull { it.id == id } ?: return null
        return RecommendationCandidateDto(
            id = game.id,
            name = game.name,
            coverUrl = game.coverUrl,
            rating = game.rating,
            ratingCount = null,
            releaseDateEpochSeconds = game.releaseDateEpochSeconds,
            summary = game.summary,
            genres = game.genres,
            themes = emptyList(),
            platforms = game.platforms,
            similarToGameIds = similarToGameIds,
        )
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
