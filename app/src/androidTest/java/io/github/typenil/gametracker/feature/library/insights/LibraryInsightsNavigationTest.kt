package io.github.typenil.gametracker.feature.library.insights

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.feature.library.navigation.LibraryInsightsKey
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.navigation.GameTrackerAppState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryInsightsNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun insights_isNestedUnderLibrary_andBackReturnsToLibrary() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            val appState = remember(navController) { GameTrackerAppState(navController) }
            NavHost(
                navController = navController,
                startDestination = LibraryKey,
            ) {
                composable<LibraryKey> {
                    Box(modifier = Modifier.testTag("library-root")) {
                        Button(
                            onClick = appState::navigateToLibraryInsights,
                            modifier = Modifier.testTag(LIBRARY_INSIGHTS_ACTION_TEST_TAG),
                        ) {
                            Text("insights")
                        }
                    }
                }
                composable<LibraryInsightsKey> {
                    LibraryInsightsScreen(
                        uiState = LibraryInsightsUiState.Empty,
                        onBackClick = appState::navigateBack,
                        onGameClick = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("library-root").assertIsDisplayed()
        composeTestRule.onNodeWithTag(LIBRARY_INSIGHTS_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LIBRARY_INSIGHTS_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.onNodeWithTag("library-root").assertIsDisplayed()
    }
}
