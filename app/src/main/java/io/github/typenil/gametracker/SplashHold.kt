package io.github.typenil.gametracker

import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the Android 12+ splash until start-destination Room content is ready
 * (or [TIMEOUT_MS] elapses). Released once; later calls are no-ops.
 */
@ActivityRetainedScoped
class SplashHold @Inject constructor() {
    private val _hold = MutableStateFlow(true)
    val hold: StateFlow<Boolean> = _hold.asStateFlow()

    fun release() {
        _hold.value = false
    }

    companion object {
        const val TIMEOUT_MS = 2_000L
    }
}
