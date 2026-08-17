package com.gametracker.backend.igdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Потокобезопасный корутиновый Token Bucket ограничитель частоты запросов.
 * Гарантирует соблюдение лимитов IGDB (3.5 req/s) вокруг каждого физического сетевого вызова.
 *
 * @param ratePerSecond Скорость пополнения токенов в секунду.
 * @param capacity Максимальная емкость корзины токенов.
 */
class TokenBucketRateLimiter(
    private val ratePerSecond: Double = 3.5,
    private val capacity: Double = 4.0
) {
    private val mutex = Mutex()
    private var availableTokens: Double = capacity
    private var lastRefillTimeNanos: Long = System.nanoTime()

    suspend fun acquire() {
        while (true) {
            val waitTimeMs = mutex.withLock {
                refillTokens()
                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0
                    return // Токен успешно получен
                }
                // Расчет времени ожидания до появления следующего токена
                val missingTokens = 1.0 - availableTokens
                val waitSeconds = missingTokens / ratePerSecond
                (waitSeconds * 1000).toLong().coerceAtLeast(10L)
            }
            delay(waitTimeMs)
        }
    }

    private fun refillTokens() {
        val now = System.nanoTime()
        val elapsedSeconds = (now - lastRefillTimeNanos).toDouble() / 1_000_000_000.0
        if (elapsedSeconds > 0) {
            val tokensToAdd = elapsedSeconds * ratePerSecond
            availableTokens = (availableTokens + tokensToAdd).coerceAtMost(capacity)
            lastRefillTimeNanos = now
        }
    }
}
