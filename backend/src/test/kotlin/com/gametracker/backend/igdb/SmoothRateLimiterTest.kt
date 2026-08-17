package com.gametracker.backend.igdb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class SmoothRateLimiterTest {

    private class VirtualTimeSource(initialNanos: Long = 1_000_000_000L) {
        val currentNanos = AtomicLong(initialNanos)
        fun now(): Long = currentNanos.get()
        fun advance(nanos: Long) = currentNanos.addAndGet(nanos)
    }

    @Test
    fun `acquire spaces consecutive calls by exact interval`() = runTest {
        val timeSource = VirtualTimeSource()
        val delays = mutableListOf<Long>()

        val limiter = SmoothRateLimiter(
            intervalNanos = 300_000_000L, // 300 ms
            timeSource = { timeSource.now() },
            delayFn = { millis ->
                delays.add(millis)
                timeSource.advance(millis * 1_000_000L)
            }
        )

        // 1st request at T=0 (no wait)
        limiter.acquire()
        assertEquals(0, delays.size)

        // 2nd request at T=0 (must wait 300ms)
        limiter.acquire()
        assertEquals(1, delays.size)
        assertEquals(300L, delays[0])

        // 3rd request at T=300ms (must wait 300ms)
        limiter.acquire()
        assertEquals(2, delays.size)
        assertEquals(300L, delays[1])
    }

    @Test
    fun `acquire allows immediate execution after interval has passed`() = runTest {
        val timeSource = VirtualTimeSource()
        val delays = mutableListOf<Long>()

        val limiter = SmoothRateLimiter(
            intervalNanos = 300_000_000L,
            timeSource = { timeSource.now() },
            delayFn = { millis -> delays.add(millis) }
        )

        limiter.acquire()
        timeSource.advance(500_000_000L) // 500 ms passed

        limiter.acquire()
        assertEquals(0, delays.size) // No delay was needed
    }

    @Test
    fun `acquire guarantees rate does not exceed 3_33 req per sec across rolling window`() = runTest {
        val timeSource = VirtualTimeSource()
        val timestamps = mutableListOf<Long>()

        val limiter = SmoothRateLimiter(
            intervalNanos = 300_000_000L,
            timeSource = { timeSource.now() },
            delayFn = { millis -> timeSource.advance(millis * 1_000_000L) }
        )

        repeat(10) {
            limiter.acquire()
            timestamps.add(timeSource.now())
        }

        // Verify that consecutive timestamps are spaced by at least 300ms (300_000_000ns)
        for (i in 0 until timestamps.size - 1) {
            val delta = timestamps[i + 1] - timestamps[i]
            assertTrue("Expected delta >= 300ms but was ${delta / 1_000_000}ms", delta >= 300_000_000L)
        }

        // Verify that across any 1000ms window there are at most 4 requests (0ms, 300ms, 600ms, 900ms)
        for (i in timestamps.indices) {
            val windowStart = timestamps[i]
            val windowEnd = windowStart + 1_000_000_000L
            val requestsInWindow = timestamps.count { it in windowStart..windowEnd }
            assertTrue(requestsInWindow <= 4)
        }
    }

    @Test
    fun `cancelled waiter releases mutex without corrupting subsequent acquire`() = runTest {
        val timeSource = VirtualTimeSource()
        var secondCompleted = false

        val limiter = SmoothRateLimiter(
            intervalNanos = 300_000_000L,
            timeSource = { timeSource.now() },
            delayFn = { delay(it) }
        )

        limiter.acquire()

        // Launch a coroutine that starts waiting and gets cancelled
        val job = launch {
            limiter.acquire()
        }
        testScheduler.advanceTimeBy(50)
        job.cancelAndJoin()

        // Next caller should be able to acquire smoothly
        launch {
            timeSource.advance(300_000_000L)
            limiter.acquire()
            secondCompleted = true
        }.join()

        assertTrue(secondCompleted)
    }

    @Test
    fun `concurrent callers serialize acquisition smoothly with monotonic spacing`() = runTest {
        val timeSource = VirtualTimeSource()
        val executionTimestamps = mutableListOf<Long>()

        val limiter = SmoothRateLimiter(
            intervalNanos = 100_000_000L,
            timeSource = { timeSource.now() },
            delayFn = { millis -> timeSource.advance(millis * 1_000_000L) }
        )

        val deferreds = (1..5).map {
            async {
                limiter.acquire()
                synchronized(executionTimestamps) {
                    executionTimestamps.add(timeSource.now())
                }
            }
        }
        deferreds.awaitAll()

        assertEquals(5, executionTimestamps.size)
        val sorted = executionTimestamps.sorted()
        for (i in 0 until sorted.size - 1) {
            val delta = sorted[i + 1] - sorted[i]
            assertTrue(delta >= 100_000_000L)
        }
    }
}
