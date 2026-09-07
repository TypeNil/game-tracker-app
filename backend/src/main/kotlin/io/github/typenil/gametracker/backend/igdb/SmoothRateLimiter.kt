package io.github.typenil.gametracker.backend.igdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate limiter with a guaranteed uniform interval (smooth rate limiting).
 *
 * Guarantees at least [intervalNanos] nanoseconds between consecutive requests to the IGDB API.
 * With [intervalNanos] = 300_000_000L (300 ms) the maximum throughput is 3.33 req/s,
 * strictly below the hard IGDB limit (4 req/s), excluding any burst in any sliding 1-second window.
 *
 * @param timeSource Monotonic time source in nanoseconds (injected for deterministic testing).
 * @param delayFn Suspension function (injected to test without real waiting).
 */
class SmoothRateLimiter(
    val intervalNanos: Long = DEFAULT_INTERVAL_NANOS,
    private val timeSource: () -> Long = { System.nanoTime() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var lastAllowedNanos: Long = 0L

    /**
     * Grants permission for the next request, suspending the coroutine if needed.
     * If the coroutine is cancelled while waiting, `lastAllowedNanos` is not advanced
     * and the mutex is released correctly.
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = timeSource()
            if (lastAllowedNanos != 0L) {
                val timeSinceLast = now - lastAllowedNanos
                if (timeSinceLast < intervalNanos) {
                    val waitNanos = intervalNanos - timeSinceLast
                    val waitMillis = (waitNanos + NANOS_PER_MILLI - 1L) / NANOS_PER_MILLI
                    delayFn(waitMillis)
                }
            }
            lastAllowedNanos = timeSource()
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_NANOS = 300_000_000L // 300 ms -> 3.33 req/s
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
