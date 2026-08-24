package io.github.typenil.gametracker.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenNotificationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun settingsScreen_displaysNotificationSectionAndAttribution() {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    onBackClick = {},
                    onOpenIgdb = {}
                )
            }
        }

        val context = composeTestRule.activity
        val notificationTitle = context.getString(R.string.settings_notifications_title)
        val notificationDesc = context.getString(R.string.settings_notifications_desc)
        val igdbAttribution = context.getString(R.string.settings_igdb_attribution)

        composeTestRule.onNodeWithText(notificationTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(notificationDesc).assertIsDisplayed()
        composeTestRule.onNodeWithText(igdbAttribution).assertIsDisplayed()
    }
}
