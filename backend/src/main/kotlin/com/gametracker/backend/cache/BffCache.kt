package com.gametracker.backend.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class CachePolicy {
    SEARCH, POPULAR, GAME_DETAILS
}

class BffCache {
    private val popularCache = Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(100).build<String, Any>()
    private val searchCache = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(200).build<String, Any>()
    private val gameCache = Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(500).build<String, Any>()

    private fun getCacheFor(policy: CachePolicy): Cache<String, Any> = when (policy) {
        CachePolicy.POPULAR -> popularCache
        CachePolicy.SEARCH -> searchCache
        CachePolicy.GAME_DETAILS -> gameCache
    }
        
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T : Any> getOrPut(key: String, policy: CachePolicy = CachePolicy.POPULAR, compute: suspend () -> T): T {
        val cache = getCacheFor(policy)
        cache.getIfPresent(key)?.let { 
            @Suppress("UNCHECKED_CAST")
            return it as T 
        }
        
        val mutex = mutexes.getOrPut(key) { Mutex() }
        return try {
            mutex.withLock {
                cache.getIfPresent(key)?.let { 
                    @Suppress("UNCHECKED_CAST")
                    return@withLock it as T 
                }
                val result = compute()
                cache.put(key, result)
                result
            }
        } finally {
            mutexes.remove(key, mutex)
        }
    }
}
