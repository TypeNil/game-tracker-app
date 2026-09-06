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
    fun unavailableBlipShorterThan1500ms_doesNotShowOfflinePill() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Available)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        // Blip shorter than 1500ms debounce
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS / 2)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()

        // Reconnects before debounce expires
        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(offlineText).assertDoesNotExist()
    }

    @Test
    fun unavailableFor1500ms_showsOfflinePill() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Available)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS + 400L)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
    }

    @Test
    fun restoredPill_activityRecreation_preservesVisiblePillForRemainingDuration() {
        composeTestRule.mainClock.autoAdvance = false
        val restorationTester = StateRestorationTester(composeTestRule)

        val networkState = mutableStateOf(NetworkStatus.Unavailable)

        restorationTester.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_OFFLINE_DEBOUNCE_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()

        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        // Advance past enter animation of restored pill
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Advance by 1 second of the remaining 2.1s duration
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Emulate saved instance state restore (activity / composition recreation)
        restorationTester.emulateSavedInstanceStateRestore()

        // Still visible immediately after restoration
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Advance remaining duration plus exit transition
        composeTestRule.mainClock.advanceTimeBy(2_000L)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG)
            .assertDoesNotExist()
    }
}
