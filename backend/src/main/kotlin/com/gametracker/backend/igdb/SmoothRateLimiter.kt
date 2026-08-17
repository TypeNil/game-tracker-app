package com.gametracker.backend.igdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ограничитель частоты вызовов с гарантированным равномерным интервалом (Smooth Rate Limiter).
 *
 * Гарантирует, что между последовательными запросами к IGDB API проходит не менее [intervalNanos] наносекунд.
 * При [intervalNanos] = 300_000_000L (300 мс) максимальная пропускная способность составляет 3.33 req/s,
 * что строго ниже жесткого лимита IGDB (4 req/s) и полностью исключает всплески в любом скользящем 1-секундном окне.
 *
 * @param intervalNanos Минимальный интервал между запросами в наносекундах (по умолчанию 300 мс).
 * @param timeSource Источник монотонного времени в наносекундах (внедряется для детерминированного тестирования).
 * @param delayFn Функция задержки (внедряется для тестирования без реального ожидания).
 */
class SmoothRateLimiter(
    val intervalNanos: Long = DEFAULT_INTERVAL_NANOS,
    private val timeSource: () -> Long = { System.nanoTime() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var lastAllowedNanos: Long = 0L

    /**
     * Запрашивает разрешение на выполнение запроса. При необходимости приостанавливает корутину.
     * Если корутина отменяется во время ожидания, `lastAllowedNanos` не обновляется, а мьютекс корректно освобождается.
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
