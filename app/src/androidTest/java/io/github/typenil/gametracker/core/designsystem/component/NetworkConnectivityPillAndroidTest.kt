package io.github.typenil.gametracker.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class NetworkConnectivityPillAndroidTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unavailableBlipShorterThan1500ms_showsNoConnectivityPill() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Available)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        // Blip shorter than 1500ms debounce
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS / 2)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()
        composeTestRule.onNodeWithText(restoredText).assertDoesNotExist()

        // Reconnects before debounce expires
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        composeTestRule.mainClock.advanceTimeBy(2_000L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()
        composeTestRule.onNodeWithText(restoredText).assertDoesNotExist()
    }

    @Test
    fun confirmedOffline_thenAvailable_showsRestoredPill() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Available)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        // Advance past debounce and enter animation
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()

        // Now reconnect
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // After 2.5s duration + exit animation, hides
        composeTestRule.mainClock.advanceTimeBy(2_500L + 500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG).assertDoesNotExist()
    }

    @Test
    fun unavailableWhileRestored_hidesRestoredImmediately() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Available)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        // Enter confirmed offline
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()

        // Transition to Available -> Restored is shown
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()
        // Network drops again while Restored is showing -> Restored must exit immediately without waiting 1500ms
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        // Advance past exit animation (500ms < 1500ms debounce)
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.onNodeWithText(restoredText).assertDoesNotExist()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // After remaining debounce duration (1000ms) plus enter animation (400ms), offline pill appears
        composeTestRule.mainClock.advanceTimeBy(1_000L + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
    }
    @Test
    fun confirmedOfflineWhileSuppressed_thenEnabled_showsOfflineWithoutAnotherDelay() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Unavailable)
        val isOfflinePillEnabled = mutableStateOf(false)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(
                    networkStatus = networkState.value,
                    isOfflinePillEnabled = isOfflinePillEnabled.value,
                )
            }
        }
        // Advance past debounce while suppressed (e.g. on Settings screen)
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Navigate to screen where offline pill is enabled (e.g. Discover)
        composeTestRule.runOnIdle {
            isOfflinePillEnabled.value = true
        }
        // Shows offline immediately (just enter animation, without another 1500ms debounce)
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
    }

    @Test
    fun initialUnavailable_doesNotShowBeforeDebounce() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Unavailable)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        // Immediately upon initial composition, offline pill must not be visible
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // After full debounce elapses, offline pill is displayed
        composeTestRule.mainClock.advanceTimeBy(1_000L + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
    }

    @Test
    fun enablingOfflinePillDuringPendingDebounce_doesNotBypassDelay() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Unavailable)
        val isOfflinePillEnabled = mutableStateOf(false)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(
                    networkStatus = networkState.value,
                    isOfflinePillEnabled = isOfflinePillEnabled.value,
                )
            }
        }
        // Advance by 500ms while disabled
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Enable offline pill during pending debounce (e.g. user navigated to Discover)
        composeTestRule.runOnIdle {
            isOfflinePillEnabled.value = true
        }
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.mainClock.advanceTimeByFrame()
        // Must still be hidden because 1500ms total has not elapsed (800ms total)
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Remaining 700ms plus enter animation completes debounce
        composeTestRule.mainClock.advanceTimeBy(700L + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
    }

    @Test
    fun restoredPill_activityRecreation_preservesVisiblePillForRemainingDuration() {
        composeTestRule.mainClock.autoAdvance = false
        val restorationTester = StateRestorationTester(composeTestRule)

        var elapsedRealtime = 10_000L
        fun advanceBy(millis: Long) {
            elapsedRealtime += millis
            composeTestRule.mainClock.advanceTimeBy(millis)
        }

        val networkState = mutableStateOf(NetworkStatus.Unavailable)

        restorationTester.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(
                    networkStatus = networkState.value,
                    elapsedRealtimeMillis = { elapsedRealtime },
                )
            }
        }
        advanceBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()

        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        // Advance past enter animation
        advanceBy(400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Advance by 1 second of the duration
        advanceBy(1_000L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Emulate saved instance state restore (activity / composition recreation)
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.mainClock.advanceTimeByFrame()

        // Still visible immediately after restoration
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Advance remaining duration (1.1s)
        advanceBy(1_100L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Advance past deadline plus exit transition
        advanceBy(1_000L)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun pendingOfflineDebounce_activityRecreation_preservesRemainingDelay() {
        composeTestRule.mainClock.autoAdvance = false
        val restorationTester = StateRestorationTester(composeTestRule)

        var elapsedRealtime = 10_000L
        fun advanceBy(millis: Long) {
            elapsedRealtime += millis
            composeTestRule.mainClock.advanceTimeBy(millis)
        }

        val networkState = mutableStateOf(NetworkStatus.Available)

        restorationTester.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(
                    networkStatus = networkState.value,
                    elapsedRealtimeMillis = { elapsedRealtime },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)

        // Drop network -> start debounce
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        // Advance 1000ms out of the 1500ms debounce
        advanceBy(1_000L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Recreate activity mid-debounce
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.mainClock.advanceTimeByFrame()

        // Still hidden immediately after recreation (500ms remaining)
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Advance 300ms (total 1300ms < 1500ms): still hidden!
        advanceBy(300L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Advance remaining 200ms + enter animation (400ms): now shown!
        advanceBy(200L + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
    }
}
