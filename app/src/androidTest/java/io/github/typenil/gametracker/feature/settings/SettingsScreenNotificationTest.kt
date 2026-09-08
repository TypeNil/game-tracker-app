package io.github.typenil.gametracker.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SettingsScreenNotificationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun settingsScreen_displaysNotificationSectionAndAttribution() {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
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
        composeTestRule.onNodeWithText(igdbAttribution).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreen_russianLocale_narrowWidth_statusAndActionDoNotOverlap() {
        val activity = composeTestRule.activity
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru-RU"))
        }
        val localizedContext = activity.createConfigurationContext(configuration)
        val statusText = localizedContext.getString(R.string.settings_notifications_disabled)
        val actionText = localizedContext.getString(R.string.settings_notifications_enable)
        composeTestRule.setContent {
            // Large font reproduces the user-visible overlap: at 1.0x the two
            // labels still fit side by side even in Russian.
            val density = Density(
                density = LocalDensity.current.density,
                fontScale = 1.3f,
            )
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides density,
            ) {
                GameTrackerTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        SettingsScreen(
                            hasNotificationPermission = false,
                            onRequestPermission = {},
                            onManageNotifications = {},
                            onBackClick = {},
                            onOpenIgdb = {},
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val statusBounds = composeTestRule.onNodeWithText(statusText, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val actionBounds = composeTestRule.onNodeWithText(actionText, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val separated = statusBounds.right <= actionBounds.left ||
            actionBounds.right <= statusBounds.left ||
            statusBounds.bottom <= actionBounds.top ||
            actionBounds.bottom <= statusBounds.top
        assertTrue(
            "Status '$statusText' ($statusBounds) overlaps action '$actionText' ($actionBounds)",
            separated,
        )
        // Pre-fix the action collapsed into a ~24dp char-wrapped sliver instead
        // of flowing onto its own line: a healthy two-line RU button is wider.
        val actionWidth = actionBounds.right - actionBounds.left
        assertTrue("Action '$actionText' looks crushed: $actionBounds", actionWidth > 100.dp)
    }
}
