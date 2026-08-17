package com.gametracker.backend.cache

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BffCacheTest {

    @Test
    fun `getOrPut returns computed value on cache miss and cached value on cache hit`() = runTest {
        val cache = BffCache()
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
    }

    @Test
    fun `same-key concurrent requests execute compute exactly once (single-flight)`() = runTest {
        val cache = BffCache()
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
    }

    @Test
    fun `failed computation does not poison cache and allows subsequent retry`() = runTest {
        val cache = BffCache()
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
    }

    @Test
    fun `distinct keys are cached separately across policies`() = runTest {
        val cache = BffCache()

        val searchResult = cache.getOrPut("shared_key", CachePolicy.SEARCH) { "search_data" }
        val gameResult = cache.getOrPut("shared_key", CachePolicy.GAME_DETAILS) { "game_data" }

        assertEquals("search_data", searchResult)
        assertEquals("game_data", gameResult)
    }
}
