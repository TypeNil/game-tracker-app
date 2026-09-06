package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkConnectivityPillAndroidTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun restoredState_navigatingDuringTimeout_stillHidesPillAfterDuration() {
        val networkState = mutableStateOf(NetworkStatus.Unavailable)
        val offlineEnabled = mutableStateOf(true)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(
                    networkStatus = networkState.value,
                    isOfflinePillEnabled = offlineEnabled.value
                )
            }
        }

        // Initially offline with enabled suppression -> Offline pill visible
        composeTestRule.onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline — showing cached data").assertIsDisplayed()

        // Transition to Available -> enters Restored state ("Back online")
        networkState.value = NetworkStatus.Available
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Back online").assertIsDisplayed()

        // Simulate user navigation to Library / Settings during the 2.5s timer:
        // isOfflinePillEnabled flips to false
        composeTestRule.mainClock.advanceTimeBy(1000L)
        offlineEnabled.value = false
        composeTestRule.waitForIdle()

        // Pill must still be showing "Back online" (timer not canceled)
        composeTestRule.onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Back online").assertIsDisplayed()

        // Advance beyond the remaining 1.5s + animation transition
        composeTestRule.mainClock.advanceTimeBy(2000L)
        composeTestRule.waitForIdle()

        // Pill must now be hidden!
        composeTestRule.onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG).assertDoesNotExist()
    }
}
