package io.github.typenil.gametracker.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedSkeletonTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactHeight_collapsesToSingleRow() {
        composeTestRule.setContent {
            GameTrackerTheme {
                Box(Modifier.height(300.dp)) {
                    FeedSkeleton(label = "Searching games…", modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeTestRule.onNodeWithText("Searching games…").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(FEED_SKELETON_ROW_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun tallContainer_showsAllRows() {
        composeTestRule.setContent {
            GameTrackerTheme {
                Box(Modifier.height(900.dp)) {
                    FeedSkeleton(label = "Searching games…", modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeTestRule.onAllNodesWithTag(FEED_SKELETON_ROW_TEST_TAG).assertCountEquals(3)
    }
}
