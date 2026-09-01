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
    private val trendingIds: List<Long> by lazy {
        readIds("fixtures/v1/trending.json")
    }


    private val popularIds: Map<String, List<Long>> by lazy {
        mapOf(
            "playing" to readIds("fixtures/v1/popular-playing.json"),
            "wanted" to readIds("fixtures/v1/popular-wanted.json"),
            "upcoming" to readIds("fixtures/v1/popular-upcoming.json"),
            "twitch" to readIds("fixtures/v1/popular-twitch.json"),
        )
    }

    private fun readIds(path: String): List<Long> = context.assets.open(path).bufferedReader().use { reader ->
        json.decodeFromString(reader.readText())
    }

    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
        validatePagination(limit, offset)
        if (offset >= mockGames.size) return emptyList()
        return mockGames.drop(offset).take(limit.coerceIn(1, MAX_LIMIT))
    }

    override suspend fun getTrendingGames(limit: Int, offset: Int): List<GameDto> {
        validatePagination(limit, offset)
        val byId = mockGames.associateBy { it.id }
        val ordered = trendingIds.mapNotNull { byId[it] }
        if (offset >= ordered.size) return emptyList()
        return ordered.drop(offset).take(limit.coerceIn(1, MAX_LIMIT))
    }
    override suspend fun getPopularPage(
        type: String,
        limit: Int,
        offset: Int
    ): io.github.typenil.gametracker.core.network.model.GamePageDto {
        validatePagination(limit, offset)
        val ids = if (type == "visits") trendingIds else popularIds[type].orEmpty()
        val byId = mockGames.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val items = ordered.drop(offset).take(limit.coerceIn(1, MAX_LIMIT))
        val nextOffset = offset + items.size
        val end = nextOffset >= ordered.size || items.size < limit
        return io.github.typenil.gametracker.core.network.model.GamePageDto(
            items = items,
            nextOffset = if (end) null else nextOffset,
            endReached = end,
        )
    }


    override suspend fun searchGames(
        query: String?,
        genres: List<String>,
        platforms: List<String>,
        minRating: Int?,
        minYear: Int?,
        maxYear: Int?,
        sort: String?,
        limit: Int,
        offset: Int,
    ): List<GameDto> {
        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val hasFilters = genres.isNotEmpty() || platforms.isNotEmpty() ||
            minRating != null || minYear != null || maxYear != null
        if (trimmedQuery == null && !hasFilters) {
            throw IllegalArgumentException("Search requires 'q' or at least one filter")
        }
        validatePagination(limit, offset)

        var filtered = mockGames.filter { game ->
            val matchesQuery = trimmedQuery == null ||
                game.name.contains(trimmedQuery, ignoreCase = true) ||
                game.genres.any { it.contains(trimmedQuery, ignoreCase = true) }
            val matchesGenres = genres.isEmpty() ||
                genres.all { requestedGenre ->
                    game.genres.any { it.equals(requestedGenre, ignoreCase = true) }
                }
            val matchesPlatforms = platforms.isEmpty() ||
                platforms.any { requestedPlatform ->
                    val canonicalRequested = if (requestedPlatform.equals("PC (Microsoft Windows)", ignoreCase = true)) {
                        "PC"
                    } else {
                        requestedPlatform
                    }
                    game.platforms.any {
                        it.equals(canonicalRequested, ignoreCase = true) ||
                            it.contains(canonicalRequested, ignoreCase = true) ||
                            canonicalRequested.contains(it, ignoreCase = true)
                    }
                }
            val matchesRating = minRating == null || (game.rating ?: 0.0) >= minRating
            val gameYear = game.releaseDateEpochSeconds?.let {
                java.time.Instant.ofEpochSecond(it).atOffset(java.time.ZoneOffset.UTC).year
            }
            val matchesMinYear = minYear == null || (gameYear != null && gameYear >= minYear)
            val matchesMaxYear = maxYear == null || (gameYear != null && gameYear <= maxYear)

            matchesQuery && matchesGenres && matchesPlatforms && matchesRating && matchesMinYear && matchesMaxYear
        }

        if (trimmedQuery == null && sort != null) {
            filtered = when (sort.lowercase()) {
                "rating", "rating_desc" -> filtered.sortedByDescending { it.rating ?: -1.0 }
                "first_release_date", "first_release_date_desc" ->
                    filtered.sortedByDescending { it.releaseDateEpochSeconds ?: Long.MIN_VALUE }
                "first_release_date_asc" -> filtered.sortedBy { it.releaseDateEpochSeconds ?: Long.MAX_VALUE }
                "name", "name_asc" -> filtered.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
                else -> filtered
            }
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
    override suspend fun getRecommendationCandidatesPage(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
        offset: Int,
        sort: String,
    ): io.github.typenil.gametracker.core.network.model.RecommendationCandidatePageDto {
        val items = getRecommendationCandidates(genres, themes, platforms, exclude, similarTo, MAX_LIMIT)
        val page = items.drop(offset).take(limit.coerceIn(1, MAX_LIMIT))
        val next = offset + page.size
        val end = next >= items.size || page.size < limit
        return io.github.typenil.gametracker.core.network.model.RecommendationCandidatePageDto(
            items = page,
            nextOffset = if (end) null else next,
            endReached = end,
        )
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
