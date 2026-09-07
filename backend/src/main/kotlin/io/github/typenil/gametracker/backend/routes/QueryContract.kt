package io.github.typenil.gametracker.backend.routes

import io.ktor.server.application.ApplicationCall

internal val topRatedQueryParams = setOf("limit", "offset")
internal val trendingQueryParams = setOf("limit", "offset")
internal val popularPageQueryParams = setOf("type", "limit", "offset")
internal val searchQueryParams = setOf(
    "q",
    "genres",
    "platforms",
    "minRating",
    "minYear",
    "maxYear",
    "sort",
    "limit",
    "offset",
)
internal val gameDetailsQueryParams = emptySet<String>()
internal val recommendationsCandidatesQueryParams = setOf(
    "genres",
    "themes",
    "platforms",
    "exclude",
    "similarTo",
    "limit",
)
internal val recommendationsCandidatesPageQueryParams = setOf(
    "genres",
    "themes",
    "platforms",
    "exclude",
    "similarTo",
    "limit",
    "offset",
    "sort",
)

/**
 * Validates that all query parameters present in the request are part of the [allowed] set.
 * Throws [IllegalArgumentException] naming the offending key(s) if unsupported parameters are present.
 */
fun requireKnownQueryParams(call: ApplicationCall, allowed: Set<String>) {
    val unknown = (call.request.queryParameters.names() - allowed).sorted()
    if (unknown.isNotEmpty()) {
        val formatted = unknown.joinToString(", ") { "'$it'" }
        val message = if (unknown.size == 1) {
            "Unsupported query parameter: $formatted"
        } else {
            "Unsupported query parameters: $formatted"
        }
        throw IllegalArgumentException(message)
    }
}

/**
 * Validates that all query parameters present in the request are part of the [allowed] vararg arguments.
 */
fun requireKnownQueryParams(call: ApplicationCall, vararg allowed: String) {
    requireKnownQueryParams(call, allowed.toSet())
}
