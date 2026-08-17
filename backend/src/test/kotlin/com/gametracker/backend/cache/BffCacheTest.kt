package com.gametracker.backend.cache

import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

private const val NANOS_PER_MINUTE = 60_000_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class BffCacheTest {

    private class FakeTicker : Ticker {
        private val nanos = AtomicLong(0L)
        override fun read(): Long = nanos.get()
        fun advanceMinutes(minutes: Long) {
            nanos.addAndGet(minutes * NANOS_PER_MINUTE)
        }
    }

    @Test
    fun `getOrPut returns computed value on cache miss and cached value on cache hit`() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)
        var computeCount = 0

        val result1 = cache.getOrPut("key_1", CachePolicy.POPULAR) {
            computeCount++
            "value_1"
        }
        val result2 = cache.getOrPut("key_1", CachePolicy.POPULAR) {
            computeCount++
            "value_2"
        }

        assertEquals("value_1", result1)
        assertEquals("value_1", result2)
        assertEquals(1, computeCount)
        cache.close()
    }

    @Test
    fun `same-key concurrent requests execute compute exactly once (single-flight)`() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)
        var computeCount = 0

        val deferreds = (1..50).map {
            async {
                cache.getOrPut("concurrent_key", CachePolicy.SEARCH) {
                    delay(50)
                    computeCount++
                    "computed_result"
                }
            }
        }

        val results = deferreds.awaitAll()

        results.forEach { assertEquals("computed_result", it) }
        assertEquals(1, computeCount)
        cache.close()
    }

    @Test
    fun `cancelling a waiter does not cancel leader computation or other waiters`() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)
        var computeCompleted = false

        // Waiter 1 (gets cancelled)
        val job1 = launch {
            cache.getOrPut<String>("cancellation_key", CachePolicy.SEARCH) {
                delay(100)
                computeCompleted = true
                "shared_val"
            }
        }

        // Waiter 2 (runs to completion)
        val waiter2 = async {
            cache.getOrPut<String>("cancellation_key", CachePolicy.SEARCH) {
                "fallback_val"
            }
        }

        // Cancel waiter 1 while computation is in flight
        testScheduler.advanceTimeBy(30)
        job1.cancelAndJoin()

        // Advance time for leader computation to finish
        testScheduler.advanceTimeBy(100)
        val result2 = waiter2.await()

        assertTrue(computeCompleted)
        assertEquals("shared_val", result2)
        cache.close()
    }

    @Test
    fun `failed computation does not poison cache and allows subsequent retry`() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)
        var attemptCount = 0

        val result1 = runCatching {
            cache.getOrPut("failing_key", CachePolicy.SEARCH) {
                attemptCount++
                throw IllegalStateException("Compute failed")
            }
        }

        assertTrue(result1.exceptionOrNull() is IllegalStateException)
        assertEquals(1, attemptCount)

        // Следующий запрос должен выполнить вычисление заново и завершиться успехом
        val recoveryResult = cache.getOrPut("failing_key", CachePolicy.SEARCH) {
            attemptCount++
            "recovered_value"
        }

        assertEquals("recovered_value", recoveryResult)
        assertEquals(2, attemptCount)
        cache.close()
    }

    @Test
    fun `entries expire after TTL configured in CachePolicy`() = runTest {
        val fakeTicker = FakeTicker()
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(ticker = fakeTicker, cacheScope = testScope)
        var computeCount = 0

        val val1 = cache.getOrPut("ttl_key", CachePolicy.SEARCH) {
            computeCount++
            "initial_value"
        }
        assertEquals("initial_value", val1)
        assertEquals(1, computeCount)

        // SEARCH TTL is 15 minutes. Advance 16 minutes:
        fakeTicker.advanceMinutes(16)

        val val2 = cache.getOrPut("ttl_key", CachePolicy.SEARCH) {
            computeCount++
            "refreshed_value"
        }
        assertEquals("refreshed_value", val2)
        assertEquals(2, computeCount)
        cache.close()
    }

    @Test
    fun `distinct keys are cached separately across policies`() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)

        val searchResult = cache.getOrPut("shared_key", CachePolicy.SEARCH) { "search_data" }
        val gameResult = cache.getOrPut("shared_key", CachePolicy.GAME_DETAILS) { "game_data" }

        assertEquals("search_data", searchResult)
        assertEquals("game_data", gameResult)
        cache.close()
    }
}
