package io.github.typenil.gametracker.feature.details.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.typenil.gametracker.core.data.notification.ReleaseEventDetector
import kotlinx.coroutines.delay
import java.time.Instant

private const val SECONDS_PER_DAY = 86_400L
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Whether a release notification can still fire for [releaseDateEpochSeconds].
 *
 * The clock is re-read when the screen resumes and when the current UTC day ends, so a
 * composition that outlives midnight (or a backgrounded app returning days later) stops
 * claiming a pending release. Delivery itself is decided by the worker, which re-reads the
 * clock on every run.
 */
@Composable
internal fun rememberReleasePending(releaseDateEpochSeconds: Long?): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowEpochSeconds by remember { mutableLongStateOf(Instant.now().epochSecond) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowEpochSeconds = Instant.now().epochSecond
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Epoch seconds align with UTC day starts, so this is the beginning of the current UTC day.
    val utcDayStart = nowEpochSeconds - nowEpochSeconds % SECONDS_PER_DAY
    LaunchedEffect(utcDayStart) {
        val secondsUntilNextDay = utcDayStart + SECONDS_PER_DAY - Instant.now().epochSecond
        delay(secondsUntilNextDay.coerceAtLeast(1L) * MILLIS_PER_SECOND)
        nowEpochSeconds = Instant.now().epochSecond
    }

    return ReleaseEventDetector.isReleasePending(nowEpochSeconds, releaseDateEpochSeconds)
}
