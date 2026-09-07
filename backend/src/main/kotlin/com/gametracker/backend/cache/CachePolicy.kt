package com.gametracker.backend.cache

/**
 * Cache TTL policies for the different BFF request types.
 *
 * Rationale:
 * - [POPULAR]: 60 min TTL. Top games and discovery lists change infrequently in IGDB;
 *   a 1-hour cache protects IGDB quota under heavy home-screen traffic.
 * - [SEARCH]: 15 min TTL. Balances freshness of search results against absorbing
 *   repeated load from identical search queries.
 * - [GAME_DETAILS]: 120 min TTL. Detailed game info (genres, platforms, descriptions)
 *   is practically immutable after release.
 * - [RECOMMEND]: 15 min TTL. The candidate pool depends on the user's tags;
 *   a search-like short TTL avoids keeping personal keys for hours.
 *
 * Each cache region is capped at 1,000 entries (MAX_CACHE_SIZE), keeping the estimated
 * heap footprint in the low megabytes (assuming an average DTO size of ~1-3 KB).
 */
enum class CachePolicy(val ttlMinutes: Long) {
    POPULAR(ttlMinutes = 60),
    SEARCH(ttlMinutes = 15),
    GAME_DETAILS(ttlMinutes = 120),
    RECOMMEND(ttlMinutes = 15)
}
