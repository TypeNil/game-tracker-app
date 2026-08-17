package com.gametracker.backend.cache

/**
 * Политика кэширования для различных типов запросов BFF.
 */
enum class CachePolicy(val ttlMinutes: Long) {
    POPULAR(ttlMinutes = 60),
    SEARCH(ttlMinutes = 15),
    GAME_DETAILS(ttlMinutes = 120)
}
