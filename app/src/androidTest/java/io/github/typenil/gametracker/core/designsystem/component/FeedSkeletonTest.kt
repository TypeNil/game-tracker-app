package io.github.typenil.gametracker.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun compactHeight_labelStillDisplayed() {
        val label = "Searching games…"
        composeTestRule.setContent {
            GameTrackerTheme {
                Box(Modifier.height(300.dp)) {
                    FeedSkeleton(label = label, modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }
}
