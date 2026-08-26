package io.github.typenil.gametracker.feature.discover

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(uiState: DiscoverUiState) {
        composeTestRule.setContent {
            GameTrackerTheme {
                DiscoverScreen(
                    uiState = uiState,
                    onGameClick = {},
                    onSearchClick = {},
                    onAboutClick = {},
                    onRefresh = {},
                    onRetry = {},
                    onUserMessageShown = {},
                    onLoadMoreTrending = {},
                )
            }
        }
    }

    @Test
    fun initialLoading_showsFeedSkeleton() {
        setContent(DiscoverUiState(isLoading = true))
        composeTestRule.onNodeWithTag("feed_skeleton").assertIsDisplayed()
    }

    @Test
    fun emptyRailLoading_showsFeedSkeleton() {
        setContent(
            DiscoverUiState(
                selectedTab = DiscoverTab.CHARTS,
                rails = listOf(
                    DiscoverRailState(
                        rail = DiscoverRail.POPULAR_NOW,
                        games = emptyList(),
                        isLoading = true,
                    ),
                ),
            ),
        )
        composeTestRule.onNodeWithTag("feed_skeleton").assertIsDisplayed()
    }
}
