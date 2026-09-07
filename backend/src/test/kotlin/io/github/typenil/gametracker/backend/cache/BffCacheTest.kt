package io.github.typenil.gametracker.backend.cache

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

        // The next request must recompute and end successfully
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

    @Test
    fun snapshot_countsHitAndMissPerRegion() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)
        var computeCount = 0

        val first = cache.getOrPut("k", CachePolicy.SEARCH) {
            computeCount++
            "v"
        }
        val second = cache.getOrPut("k", CachePolicy.SEARCH) {
            computeCount++
            "v2"
        }

        assertEquals("v", first)
        assertEquals("v", second)
        assertEquals(1, computeCount)

        val search = cache.snapshot().single { it.policy == "SEARCH" }
        assertEquals(1L, search.estimatedSize)
        assertEquals(1L, search.hitCount)
        assertEquals(1L, search.missCount)
        assertEquals(0L, search.evictionCount)
        cache.close()
    }

    @Test
    fun `all CachePolicy regions expire entries after their configured TTL`() = runTest {
        val fakeTicker = FakeTicker()
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(ticker = fakeTicker, cacheScope = testScope)

        for (policy in CachePolicy.entries) {
            var computeCount = 0
            val key = "ttl_test_${policy.name}"

            val val1 = cache.getOrPut(key, policy) {
                computeCount++
                "initial_${policy.name}"
            }
            assertEquals("initial_${policy.name}", val1)
            assertEquals(1, computeCount)

            // Before TTL expires: 1 minute before TTL
            fakeTicker.advanceMinutes(policy.ttlMinutes - 1)
            val valHit = cache.getOrPut(key, policy) {
                computeCount++
                "should_not_compute"
            }
            assertEquals("initial_${policy.name}", valHit)
            assertEquals(1, computeCount)

            // After TTL expires: 2 more minutes (total = ttlMinutes + 1)
            fakeTicker.advanceMinutes(2)
            val valRefreshed = cache.getOrPut(key, policy) {
                computeCount++
                "refreshed_${policy.name}"
            }
            assertEquals("refreshed_${policy.name}", valRefreshed)
            assertEquals(2, computeCount)
        }

        cache.close()
    }

    @Test
    fun `cache regions enforce entry-count limit of MAX_CACHE_SIZE`() = runTest {
        val testScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = BffCache(cacheScope = testScope)

        val totalInserts = 1_050
        for (i in 1..totalInserts) {
            cache.getOrPut("key_$i", CachePolicy.SEARCH) { "val_$i" }
        }

        cache.cleanUp()

        val searchStats = cache.snapshot().single { it.policy == "SEARCH" }
        assertTrue(
            "Estimated size (${searchStats.estimatedSize}) must be <= MAX_CACHE_SIZE ($MAX_CACHE_SIZE)",
            searchStats.estimatedSize <= MAX_CACHE_SIZE
        )
        assertTrue(
            "Eviction count (${searchStats.evictionCount}) must be >= 50",
            searchStats.evictionCount >= (totalInserts - MAX_CACHE_SIZE)
        )
        cache.close()
    }
}
