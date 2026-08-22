package com.gametracker.backend.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_CACHE_SIZE = 1_000L

/**
 * Потокобезопасный асинхронный кэш с защитой от эффекта Thundering Herd (Single-Flight).
 *
 * Особенности:
 * - Все параллельные запросы с одинаковым [CacheKey] ожидают единого вычисления (single-flight).
 * - Вычисление выполняется в `cacheScope` приложения ([SupervisorJob]): отмена одного клиента не отменяет других подписчиков.
 * - При ошибке вычисления запись атомарно удаляется из очереди ожидающих, не отравляя кэш.
 * - Поддерживает внедрение Caffeine [Ticker] для детерминированного тестирования TTL.
 */
class BffCache(
    ticker: Ticker = Ticker.systemTicker(),
    private val cacheScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger("BffCache")
    private val isClosed = AtomicBoolean(false)

    private val popularCache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CachePolicy.POPULAR.ttlMinutes, TimeUnit.MINUTES)
        .ticker(ticker)
        .build<String, Any>()

    private val searchCache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CachePolicy.SEARCH.ttlMinutes, TimeUnit.MINUTES)
        .ticker(ticker)
        .build<String, Any>()

    private val gameDetailsCache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CachePolicy.GAME_DETAILS.ttlMinutes, TimeUnit.MINUTES)
        .ticker(ticker)
        .build<String, Any>()

    private val recommendCache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CachePolicy.RECOMMEND.ttlMinutes, TimeUnit.MINUTES)
        .ticker(ticker)
        .build<String, Any>()

    private val pendingComputations = ConcurrentHashMap<CacheKey, CompletableDeferred<Any>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getOrPut(
        key: String,
        policy: CachePolicy,
        compute: suspend () -> T
    ): T {
        if (isClosed.get()) {
            return compute()
        }

        val caffeineCache = getCacheForPolicy(policy)
        val cached = caffeineCache.getIfPresent(key)
        if (cached != null) {
            return cached as T
        }

        val cacheKey = CacheKey(policy, key)
        var isLeader = false
        val deferred = pendingComputations.computeIfAbsent(cacheKey) {
            isLeader = true
            CompletableDeferred()
        }

        if (isLeader) {
            cacheScope.launch {
                try {
                    val computed = compute()
                    caffeineCache.put(key, computed)
                    deferred.complete(computed)
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                } finally {
                    pendingComputations.remove(cacheKey, deferred)
                }
            }
        }

        return deferred.await() as T
    }

    private fun getCacheForPolicy(policy: CachePolicy) = when (policy) {
        CachePolicy.POPULAR -> popularCache
        CachePolicy.SEARCH -> searchCache
        CachePolicy.GAME_DETAILS -> gameDetailsCache
        CachePolicy.RECOMMEND -> recommendCache
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            logger.info("Closing BffCache and cancelling background compute scope...")
            cacheScope.cancel()
            popularCache.invalidateAll()
            searchCache.invalidateAll()
            gameDetailsCache.invalidateAll()
            recommendCache.invalidateAll()
            pendingComputations.clear()
        }
    }

    data class CacheKey(val policy: CachePolicy, val key: String)
}
