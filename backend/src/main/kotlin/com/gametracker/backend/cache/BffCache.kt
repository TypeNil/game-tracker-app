package com.gametracker.backend.cache

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * In-memory кэш для BFF, адаптированный под Kotlin Coroutines.
 * Использует Mutex для предотвращения race conditions (состояние гонки), 
 * когда несколько параллельных запросов пытаются получить данные, которых еще нет в кэше.
 */
class BffCache {
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(500)
        .build<String, Any>()
        
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T : Any> getOrPut(key: String, compute: suspend () -> T): T {
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
            // Удаляем mutex только если в мапе лежит именно наш экземпляр, 
            // чтобы не удалить чужой при edge-кейсах.
            // Предотвращает утечку памяти для бесконечного множества ключей (например, поисковых запросов).
            mutexes.remove(key, mutex)
        }
    }
}
