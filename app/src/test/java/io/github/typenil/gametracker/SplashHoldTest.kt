package io.github.typenil.gametracker

import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashHoldTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun releaseAtDeadline_releasesImmediatelyWhenDeadlineAlreadyPassed() = runTest {
        var now = 0L
        val hold = SplashHold(elapsedRealtime = { now }, timeoutMs = 2_000L)
        now = 5_000L
        hold.releaseAtDeadline()
        assertFalse(hold.hold.value)
    }

    @Test
    fun releaseAtDeadline_waitsRemainingTimeAcrossCalls() = runTest {
        val now = 0L
        val hold = SplashHold(elapsedRealtime = { now }, timeoutMs = 2_000L)
        val job = launch { hold.releaseAtDeadline() }
        runCurrent()
        assertTrue(hold.hold.value)
        advanceTimeBy(1_999)
        runCurrent()
        assertTrue(hold.hold.value)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(hold.hold.value)
        job.join()
    }
}
