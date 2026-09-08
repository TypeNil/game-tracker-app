package io.github.typenil.gametracker

import android.os.SystemClock
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the Android 12+ splash until start-destination content is ready
 * (or [TIMEOUT_MS] elapses). Released once; later calls are no-ops.
 * The deadline is retained across Activity recreation.
 */
@ActivityRetainedScoped
class SplashHold internal constructor(
    private val elapsedRealtime: () -> Long,
    timeoutMs: Long,
) {
    @Inject
    constructor() : this({ SystemClock.elapsedRealtime() }, TIMEOUT_MS)

    private val deadlineElapsedRealtime = elapsedRealtime() + timeoutMs
    private val _hold = MutableStateFlow(true)
    val hold: StateFlow<Boolean> = _hold.asStateFlow()

    fun release() {
        _hold.value = false
    }

    suspend fun releaseAtDeadline() {
        delay((deadlineElapsedRealtime - elapsedRealtime()).coerceAtLeast(0L))
        release()
    }

    companion object {
        const val TIMEOUT_MS = 2_000L
    }
}
