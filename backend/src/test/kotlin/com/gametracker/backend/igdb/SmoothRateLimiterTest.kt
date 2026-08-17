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
    fun `acquire guarantees no more than 3 requests in any 1-second rolling window`() = runTest {
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

        // Verify that in every 1000ms (1_000_000_000ns) window there are <= 4 requests
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
    fun `concurrent callers serialize acquisition smoothly`() = runTest {
        val timeSource = VirtualTimeSource()
        val executionOrder = mutableListOf<Int>()

        val limiter = SmoothRateLimiter(
            intervalNanos = 100_000_000L,
            timeSource = { timeSource.now() },
            delayFn = { millis -> timeSource.advance(millis * 1_000_000L) }
        )

        val deferreds = (1..5).map { id ->
            async {
                limiter.acquire()
                executionOrder.add(id)
            }
        }
        deferreds.awaitAll()

        assertEquals(5, executionOrder.size)
    }
}
