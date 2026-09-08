package io.github.typenil.gametracker.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import dagger.hilt.android.EntryPointAccessors
import io.github.typenil.gametracker.core.network.DebugBffUrlStore
import io.github.typenil.gametracker.core.network.DebugNetworkGraphEntryPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun setContent(
        hasNotificationPermission: Boolean = false,
        onOpenIgdb: () -> Unit = {},
        onOpenGitHub: () -> Unit = {},
        onManageNotifications: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestPermission = {},
                    onManageNotifications = onManageNotifications,
                    onBackClick = {},
                    onOpenIgdb = onOpenIgdb,
                    onOpenGitHub = onOpenGitHub,
                )
            }
        }
    }

    @Test
    fun attributionCopy_isVisible() {
        setContent()

        composeTestRule.onNodeWithText("Game data provided by IGDB").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun igdbLink_invokesCallback() {
        var clicked = false
        setContent(onOpenIgdb = { clicked = true })

        composeTestRule.onNodeWithText("IGDB").performScrollTo().performClick()

        composeTestRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun notificationsEnabled_showsManageNotAbout() {
        setContent(hasNotificationPermission = true)

        val manage = composeTestRule.activity.getString(R.string.settings_notifications_manage)
        composeTestRule.onNodeWithText(manage).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(
            composeTestRule.activity.getString(R.string.settings_title),
        ).assertCountEquals(1)
    }

    @Test
    fun notificationsEnabled_showsCheckReleasesNow_andInvokesCallback() {
        var checkClicked = false
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = true,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    onOpenGitHub = {},
                    onCheckReleasesNow = { checkClicked = true }
                )
            }
        }

        val checkNowLabel = composeTestRule.activity.getString(R.string.settings_notifications_check_now)
        composeTestRule.onNodeWithText(checkNowLabel).performScrollTo().performClick()

        composeTestRule.runOnIdle { assertTrue(checkClicked) }
    }

    @Test
    fun testNotificationHidden_keepsCheckNowVisible() {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = true,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    onOpenGitHub = {},
                    onCheckReleasesNow = {},
                    onSendTestNotification = {},
                    isSendTestNotificationVisible = false
                )
            }
        }

        val checkNowLabel = composeTestRule.activity.getString(R.string.settings_notifications_check_now)
        val sendTestLabel = composeTestRule.activity.getString(R.string.settings_notifications_send_test)

        composeTestRule.onNodeWithText(checkNowLabel).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(sendTestLabel).assertDoesNotExist()
    }

    @Test
    fun notificationsEnabled_debugShowsBothCheckNowAndTestNotification_andInvokesSendTestOnly() {
        var checkNowClicked = false
        var sendTestClicked = false

        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = true,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    onOpenGitHub = {},
                    onCheckReleasesNow = { checkNowClicked = true },
                    onSendTestNotification = { sendTestClicked = true },
                    isSendTestNotificationVisible = true
                )
            }
        }

        val checkNowLabel = composeTestRule.activity.getString(R.string.settings_notifications_check_now)
        val sendTestLabel = composeTestRule.activity.getString(R.string.settings_notifications_send_test)

        composeTestRule.onNodeWithText(checkNowLabel).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(sendTestLabel).performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertTrue(sendTestClicked)
            assertFalse(checkNowClicked)
        }
    }

    @Test
    fun appInfoCard_andGitHubLink_areDisplayedAndClickable() {
        var githubClicked = false
        setContent(onOpenGitHub = { githubClicked = true })

        val appInfoTitle = composeTestRule.activity.getString(R.string.settings_app_info_title)
        val appName = composeTestRule.activity.getString(R.string.settings_app_name)
        val githubLabel = composeTestRule.activity.getString(R.string.settings_github_link)

        composeTestRule.onNodeWithText(appInfoTitle).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(appName).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(githubLabel).performScrollTo().performClick()

        composeTestRule.runOnIdle { assertTrue(githubClicked) }
    }

    @Test
    fun debugBffUrl_visibleWhenDebugVisible() {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    debugBffUrl = "http://10.0.2.2:8080",
                    isDebugBffUrlVisible = true
                )
            }
        }

        val title = composeTestRule.activity.getString(R.string.settings_debug_bff_title)
        val label = composeTestRule.activity.getString(R.string.settings_debug_bff_label)
        val save = composeTestRule.activity.getString(R.string.settings_debug_bff_save)
        val reset = composeTestRule.activity.getString(R.string.settings_debug_bff_reset)

        composeTestRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
        composeTestRule.onNodeWithText("http://10.0.2.2:8080").assertIsDisplayed()
        composeTestRule.onNodeWithText(save).assertIsDisplayed()
        composeTestRule.onNodeWithText(reset).assertIsDisplayed()
    }

    @Test
    fun debugBffUrl_hiddenWhenNotVisible() {
        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    isDebugBffUrlVisible = false
                )
            }
        }

        val title = composeTestRule.activity.getString(R.string.settings_debug_bff_title)
        val save = composeTestRule.activity.getString(R.string.settings_debug_bff_save)
        val reset = composeTestRule.activity.getString(R.string.settings_debug_bff_reset)

        composeTestRule.onNodeWithText(title).assertDoesNotExist()
        composeTestRule.onNodeWithText(save).assertDoesNotExist()
        composeTestRule.onNodeWithText(reset).assertDoesNotExist()
    }

    @Test
    fun debugBffUrl_showsErrorWhenInvalid() {
        val errorMessage = "Invalid URL: must be http(s) origin with no path or query"

        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    debugBffUrl = "invalid://url/path",
                    debugBffUrlError = errorMessage,
                    isDebugBffUrlVisible = true
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun debugBffUrl_saveInvokesCallback() {
        var saveClicked = false

        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    debugBffUrl = "http://10.0.2.2:8080",
                    isDebugBffUrlVisible = true,
                    onSaveDebugBffUrl = { saveClicked = true }
                )
            }
        }

        val save = composeTestRule.activity.getString(R.string.settings_debug_bff_save)
        composeTestRule.onNodeWithText(save).performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertTrue(saveClicked)
        }
    }

    @Test
    fun debugBffUrl_resetInvokesCallback() {
        var resetClicked = false

        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    debugBffUrl = "http://10.0.2.2:8080",
                    isDebugBffUrlVisible = true,
                    onResetDebugBffUrl = { resetClicked = true }
                )
            }
        }

        val reset = composeTestRule.activity.getString(R.string.settings_debug_bff_reset)
        composeTestRule.onNodeWithText(reset).performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertTrue(resetClicked)
        }
    }

    @Test
    fun debugBffUrl_changeInvokesCallback() {
        var changedValue = ""

        composeTestRule.setContent {
            GameTrackerTheme {
                SettingsScreen(
                    hasNotificationPermission = false,
                    onRequestPermission = {},
                    onManageNotifications = {},
                    onBackClick = {},
                    onOpenIgdb = {},
                    debugBffUrl = "",
                    isDebugBffUrlVisible = true,
                    onDebugBffUrlChange = { changedValue = it }
                )
            }
        }

        val label = composeTestRule.activity.getString(R.string.settings_debug_bff_label)
        composeTestRule.onNodeWithText(label).performScrollTo().performTextInput("http://localhost:8080")

        composeTestRule.runOnIdle {
            assertEquals("http://localhost:8080", changedValue)
        }
    }

    @After
    fun tearDown() {
        graphStore().setUrl(urlString = null)
    }

    @Test
    fun debugBffUrl_routeSaveValidUrl_updatesHiltStore() {
        graphStore().setUrl(urlString = null)
        composeTestRule.setContent {
            GameTrackerTheme { SettingsRoute(onBackClick = {}) }
        }

        val label = composeTestRule.activity.getString(R.string.settings_debug_bff_label)
        val save = composeTestRule.activity.getString(R.string.settings_debug_bff_save)
        composeTestRule.onNodeWithText(label).performScrollTo().performTextInput(OVERRIDE_HOST)
        composeTestRule.onNodeWithText(save).performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals("$OVERRIDE_HOST/", graphStore().currentUrl().toString())
        }
    }

    @Test
    fun debugBffUrl_routeInvalidUrl_showsErrorAndKeepsStoreEmpty() {
        graphStore().setUrl(urlString = null)
        composeTestRule.setContent {
            GameTrackerTheme { SettingsRoute(onBackClick = {}) }
        }

        val label = composeTestRule.activity.getString(R.string.settings_debug_bff_label)
        val save = composeTestRule.activity.getString(R.string.settings_debug_bff_save)
        val error = composeTestRule.activity.getString(R.string.settings_debug_bff_invalid_url)
        composeTestRule.onNodeWithText(label).performScrollTo().performTextInput("notaurl")
        composeTestRule.onNodeWithText(save).performScrollTo().performClick()

        composeTestRule.onNodeWithText(error).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertNull(graphStore().currentUrl())
        }
    }

    private fun graphStore(): DebugBffUrlStore =
        EntryPointAccessors.fromApplication(
            composeTestRule.activity.application,
            DebugNetworkGraphEntryPoint::class.java,
        ).debugBffUrlStore()

    private companion object {
        const val OVERRIDE_HOST = "http://192.168.1.200:8888"
    }
}
