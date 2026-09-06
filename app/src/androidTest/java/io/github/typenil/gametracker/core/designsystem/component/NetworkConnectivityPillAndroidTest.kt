package io.github.typenil.gametracker.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
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
    fun restoredState_navigatingDuringTimeout_stillHidesPillAfterDuration() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Unavailable)
        val offlineEnabled = mutableStateOf(true)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(
                    networkStatus = networkState.value,
                    isOfflinePillEnabled = offlineEnabled.value,
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_RECOVERY_DEBOUNCE_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.runOnIdle {
            offlineEnabled.value = false
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText(restoredText).assertIsDisplayed()

        // Remaining timeout plus exit animation.
        composeTestRule.mainClock.advanceTimeBy(2_000L)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(NETWORK_CONNECTIVITY_PILL_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun availableBlipShorterThanDebounce_doesNotShowRestoredState() {
        composeTestRule.mainClock.autoAdvance = false

        val networkState = mutableStateOf(NetworkStatus.Unavailable)

        composeTestRule.setContent {
            GameTrackerTheme {
                NetworkConnectivityPill(networkStatus = networkState.value)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val offlineText = composeTestRule.activity.getString(R.string.connectivity_offline)
        val restoredText = composeTestRule.activity.getString(R.string.connectivity_restored)

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Available
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_RECOVERY_DEBOUNCE_MILLIS / 2)

        composeTestRule.onNodeWithText(restoredText).assertDoesNotExist()

        composeTestRule.runOnIdle {
            networkState.value = NetworkStatus.Unavailable
        }
        composeTestRule.mainClock.advanceTimeBy(NETWORK_RECOVERY_DEBOUNCE_MILLIS * 2)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText(offlineText).assertIsDisplayed()
        composeTestRule.onNodeWithText(restoredText).assertDoesNotExist()
    }
}
