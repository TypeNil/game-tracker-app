package com.gametracker.backend.routes

import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.cache.CachePolicy
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.models.GameDetailsDto
import com.gametracker.backend.models.GameDetailsRequest
import com.gametracker.backend.models.GameDto
import com.gametracker.backend.models.RecommendationCandidateDto
import com.gametracker.backend.models.RecommendationCandidatesRequest
import com.gametracker.backend.models.toCandidateDto
import com.gametracker.backend.models.SearchRequest
import com.gametracker.backend.models.TopRatedRequest
import com.gametracker.backend.models.toDetailsDto
import com.gametracker.backend.models.toGameDto
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GamesRoutes")

/**
 * Регистрация публичных эндпоинтов мобильного API с ограничением частоты вызовов.
 */
fun Route.gamesRoutes(igdbService: IgdbService, cache: BffCache) {
    rateLimit(RateLimitName("api_v1")) {
        route("/v1") {
            get("/discover/top-rated") {
                val limit = parseIntegerParam(call.request.queryParameters["limit"], "limit")
                val offset = parseIntegerParam(call.request.queryParameters["offset"], "offset")
                val request = TopRatedRequest(limit, offset)

                logger.info("Fetching top rated games (limit={}, offset={})", limit, offset)
                val games = cache.getOrPut(request.cacheKey, CachePolicy.POPULAR) {
                    igdbService.queryGames(request.toApicalypseQuery()).mapNotNull { it.toGameDto() }
                }

                call.respond<List<GameDto>>(games)
            }

            get("/games/search") {
                val query = call.request.queryParameters["q"]
                val limit = parseIntegerParam(call.request.queryParameters["limit"], "limit")
                val offset = parseIntegerParam(call.request.queryParameters["offset"], "offset")
                val request = SearchRequest(query, limit, offset)

                logger.info("Searching games (queryLength={}, limit={}, offset={})", request.canonicalQuery.length, limit, offset)
                val games = cache.getOrPut(request.cacheKey, CachePolicy.SEARCH) {
                    igdbService.queryGames(request.toApicalypseQuery()).mapNotNull { it.toGameDto() }
                }

                call.respond<List<GameDto>>(games)
            }

            get("/games/{id}") {
                val id = parseLongParam(call.parameters["id"], "id")
                val request = GameDetailsRequest(id)

                logger.info("Fetching game details (id={})", id)
                val game = cache.getOrPut(request.cacheKey, CachePolicy.GAME_DETAILS) {
                    val results = igdbService.queryGames(request.toApicalypseQuery())
                    val match = results.firstOrNull()?.toDetailsDto()
                        ?: throw NoSuchElementException("Game with id $id not found")
                    match
                }

                call.respond<GameDetailsDto>(game)
            }

            recommendationCandidatesRoute(igdbService, cache)
        }
    }
}

private fun parseIntegerParam(raw: String?, paramName: String): Int? {
    if (raw == null) return null
    return raw.toIntOrNull() ?: throw IllegalArgumentException("Query parameter '$paramName' must be a valid integer")
}

private fun parseLongParam(raw: String?, paramName: String): Long {
    if (raw == null) throw IllegalArgumentException("Path parameter '$paramName' is required")
    return raw.toLongOrNull() ?: throw IllegalArgumentException("Parameter '$paramName' must be a valid integer")
}

private fun Route.recommendationCandidatesRoute(igdbService: IgdbService, cache: BffCache) {
    get("/recommendations/candidates") {
        val request = RecommendationCandidatesRequest(
            genresParam = call.request.queryParameters["genres"],
            themesParam = call.request.queryParameters["themes"],
            platformsParam = call.request.queryParameters["platforms"],
            excludeParam = call.request.queryParameters["exclude"],
            similarToParam = call.request.queryParameters["similarTo"],
            limitParam = parseIntegerParam(call.request.queryParameters["limit"], "limit"),
        )
        logger.info(
            "Fetching recommendation candidates (genres={}, themes={}, platforms={}, exclude={}, similarTo={}, limit={})",
            request.genres.size,
            request.themes.size,
            request.platforms.size,
            request.exclude.size,
            request.similarTo.size,
            request.limit,
        )
        if (!request.hasTags && request.similarTo.isEmpty()) {
            call.respond<List<RecommendationCandidateDto>>(emptyList())
            return@get
        }
        val games = cache.getOrPut(request.cacheKey, CachePolicy.RECOMMEND) {
            loadCandidates(igdbService, request)
        }
        call.respond(games)
    }
}

private suspend fun loadCandidates(
    igdbService: IgdbService,
    request: RecommendationCandidatesRequest,
): List<RecommendationCandidateDto> {
    val similarOwners = linkedMapOf<Long, MutableSet<Long>>()
    if (request.similarTo.isNotEmpty()) {
        igdbService.queryGames(request.toSimilarSeedsApicalypseQuery()).forEach { seed ->
            seed.similarGames.orEmpty().forEach { similar ->
                val similarId = similar.id ?: return@forEach
                if (similarId == seed.id || similarId in request.blockedIds) return@forEach
                similarOwners.getOrPut(similarId) { mutableSetOf() }.add(seed.id)
            }
        }
    }

    val merged = linkedMapOf<Long, RecommendationCandidateDto>()
    if (similarOwners.isNotEmpty()) {
        igdbService.queryGames(request.toHydrateApicalypseQuery(similarOwners.keys.toList())).forEach { game ->
            merged[game.id] = game.toCandidateDto(similarOwners[game.id].orEmpty().sorted())
        }
    }
    if (request.hasTags) {
        igdbService.queryGames(request.toTagApicalypseQuery()).forEach { game ->
            val existing = merged[game.id]
            if (existing == null) {
                merged[game.id] = game.toCandidateDto()
            } else {
                merged[game.id] = existing
            }
        }
    }
    return merged.values
        .filterNot { it.id in request.blockedIds }
        .take(request.limit)
}
