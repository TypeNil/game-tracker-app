package com.gametracker.backend.routes

import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.cache.CachePolicy
import com.gametracker.backend.error.UpstreamException
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.models.GameDetailsDto
import com.gametracker.backend.models.GameDetailsRequest
import com.gametracker.backend.models.GameDto
import com.gametracker.backend.models.GamePageDto
import com.gametracker.backend.models.PopularityRailRequest
import com.gametracker.backend.models.RecommendationCandidateDto
import com.gametracker.backend.models.RecommendationCandidatePageDto
import com.gametracker.backend.models.RecommendationCandidatesRequest
import com.gametracker.backend.models.SearchRequest
import com.gametracker.backend.models.TopRatedRequest
import com.gametracker.backend.models.TrendingRequest
import com.gametracker.backend.models.toCandidateDto
import com.gametracker.backend.models.toDetailsDto
import com.gametracker.backend.models.toGameDto
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GamesRoutes")

@Suppress("LongMethod")
fun Route.gamesRoutes(igdbService: IgdbService, cache: BffCache) {
    rateLimit(RateLimitName("api_v1")) {
        route("/v1") {
            get("/discover/top-rated") {
                val request = TopRatedRequest(
                    parseIntegerParam(call.request.queryParameters["limit"], "limit"),
                    parseIntegerParam(call.request.queryParameters["offset"], "offset"),
                )
                logger.info("Fetching top rated games (limit={}, offset={})", request.limit, request.offset)
                val games = cache.getOrPut(request.cacheKey, CachePolicy.POPULAR) {
                    igdbService.queryGames(request.toApicalypseQuery()).mapNotNull { it.toGameDto() }
                }
                call.respond<List<GameDto>>(games)
            }

            get("/discover/trending") {
                val request = TrendingRequest(
                    limitParam = parseIntegerParam(call.request.queryParameters["limit"], "limit"),
                    offsetParam = parseIntegerParam(call.request.queryParameters["offset"], "offset"),
                )
                logger.info("Fetching trending games (limit={}, offset={})", request.limit, request.offset)
                val games = cache.getOrPut(request.cacheKey, CachePolicy.POPULAR) {
                    loadPopularityRail(igdbService, request).items
                }
                call.respond<List<GameDto>>(games)
            }

            get("/discover/popular/page") {
                val request = PopularityRailRequest(
                    typeParam = call.request.queryParameters["type"],
                    limitParam = parseIntegerParam(call.request.queryParameters["limit"], "limit"),
                    offsetParam = parseIntegerParam(call.request.queryParameters["offset"], "offset"),
                )
                logger.info(
                    "Fetching popularity rail (type={}, limit={}, offset={})",
                    request.popularityType,
                    request.limit,
                    request.offset,
                )
                val page = cache.getOrPut("${request.cacheKey}_page", CachePolicy.POPULAR) {
                    loadPopularityRail(igdbService, request)
                }
                call.respond(page)
            }

            get("/games/search") {
                val query = call.request.queryParameters["q"]
                val limit = parseIntegerParam(call.request.queryParameters["limit"], "limit")
                val offset = parseIntegerParam(call.request.queryParameters["offset"], "offset")
                val request = SearchRequest(query, limit, offset)
                logger.info(
                    "Searching games (queryLength={}, limit={}, offset={})",
                    request.canonicalQuery.length,
                    limit,
                    offset,
                )
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
                    coroutineScope {
                        val details = async { igdbService.queryGames(request.toApicalypseQuery()) }
                        val ttb = async {
                            try {
                                igdbService.queryGameTimeToBeats(id).firstOrNull()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: UpstreamException) {
                                logger.warn(
                                    "Time-to-beat unavailable for game id={} ({})",
                                    id,
                                    e::class.simpleName,
                                )
                                null
                            }
                        }
                        val results = details.await().firstOrNull()
                            ?: throw NoSuchElementException("Game with id $id not found")
                        results.toDetailsDto(ttb.await())
                    }
                }
                call.respond<GameDetailsDto>(game)
            }

            recommendationCandidatesRoute(igdbService, cache)
            pagedRecommendationCandidatesRoute(igdbService, cache)
        }
    }
}

private fun Route.recommendationCandidatesRoute(igdbService: IgdbService, cache: BffCache) {
    get("/recommendations/candidates") {
        val request = recommendationRequest(call)
        if (!request.hasTags && request.similarTo.isEmpty()) {
            call.respond<List<RecommendationCandidateDto>>(emptyList())
            return@get
        }
        val page = cache.getOrPut(request.cacheKey, CachePolicy.RECOMMEND) {
            loadCandidates(igdbService, request)
        }
        call.respond(page.items)
    }
}

private fun Route.pagedRecommendationCandidatesRoute(igdbService: IgdbService, cache: BffCache) {
    get("/recommendations/candidates/page") {
        val request = recommendationRequest(call)
        if (!request.hasTags && request.similarTo.isEmpty()) {
            call.respond(RecommendationCandidatePageDto())
            return@get
        }
        val page = cache.getOrPut("${request.cacheKey}_page", CachePolicy.RECOMMEND) {
            loadCandidates(igdbService, request)
        }
        call.respond(page)
    }
}

private fun recommendationRequest(call: io.ktor.server.application.ApplicationCall) =
    RecommendationCandidatesRequest(
        genresParam = call.request.queryParameters["genres"],
        themesParam = call.request.queryParameters["themes"],
        platformsParam = call.request.queryParameters["platforms"],
        excludeParam = call.request.queryParameters["exclude"],
        similarToParam = call.request.queryParameters["similarTo"],
        limitParam = parseIntegerParam(call.request.queryParameters["limit"], "limit"),
        offsetParam = parseIntegerParam(call.request.queryParameters["offset"], "offset"),
        sortParam = call.request.queryParameters["sort"],
    )

private suspend fun loadPopularityRail(
    igdbService: IgdbService,
    request: PopularityRailRequest,
): GamePageDto {
    val primitives = igdbService.queryPopularityPrimitives(request.toPrimitivesApicalypseQuery())
    val orderedIds = primitives.mapNotNull { it.gameId?.takeIf { id -> id > 0L } }.distinct()
    val pageIds = orderedIds.drop(request.offset).take(request.limit)
    if (pageIds.isEmpty()) return GamePageDto()
    val byId = igdbService.queryGames(request.toHydrateApicalypseQuery(pageIds))
        .mapNotNull { it.toGameDto() }
        .associateBy { it.id }
    val items = pageIds.mapNotNull { byId[it] }
    val endReached = primitives.size < request.primitiveFetchLimit ||
        request.offset + request.limit >= PopularityRailRequest.MAX_PRIMITIVE_FETCH
    return GamePageDto(
        items = items,
        nextOffset = if (endReached) null else request.offset + request.limit,
        endReached = endReached,
    )
}

private suspend fun loadCandidates(
    igdbService: IgdbService,
    request: RecommendationCandidatesRequest,
): RecommendationCandidatePageDto {
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
            if (game.id !in merged) merged[game.id] = game.toCandidateDto()
        }
    }
    val all = merged.values.filterNot { it.id in request.blockedIds }
    val items = all.drop(request.offset).take(request.limit)
    val endReached = request.offset + items.size >= all.size || items.size < request.limit
    return RecommendationCandidatePageDto(
        items = items,
        nextOffset = if (endReached) null else request.offset + request.limit,
        endReached = endReached,
    )
}

private fun parseIntegerParam(raw: String?, paramName: String): Int? {
    if (raw == null) return null
    return raw.toIntOrNull()
        ?: throw IllegalArgumentException("Query parameter '$paramName' must be a valid integer")
}

private fun parseLongParam(raw: String?, paramName: String): Long {
    if (raw == null) throw IllegalArgumentException("Path parameter '$paramName' is required")
    return raw.toLongOrNull()
        ?: throw IllegalArgumentException("Parameter '$paramName' must be a valid integer")
}
