package io.github.typenil.gametracker.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.MainActivity
import io.github.typenil.gametracker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkNavigationTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun coldStartDeepLink_opensGameDetails_andBackReturnsToDiscover() {
        val targetGameId = 1942L
        val targetTitle = "The Witcher 3: Wild Hunt"
        val discoverTitle = context.getString(R.string.discover_title)
        val discoverNavLabel = context.getString(R.string.nav_discover)
        val deepLinkUri = Uri.parse("gametracker://game/$targetGameId")

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setClass(context, MainActivity::class.java)
            setPackage(BuildConfig.APPLICATION_ID)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // 1. Assert: Details screen is rendered with title from fixture (The Witcher 3: Wild Hunt)
            // Title exists in TopAppBar and in Header; pick first matching node to avoid ambiguity.
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText(targetTitle)[0].assertIsDisplayed()

            // 2. Assert: Discover screen and bottom navigation bar are not visible on details screen
            composeTestRule.onNodeWithText(discoverTitle).assertDoesNotExist()
            composeTestRule.onNodeWithText(discoverNavLabel).assertDoesNotExist()

            // 3. Press Back -> Synthetic back stack returns to Discover
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            // 4. Assert: Discover screen and bottom navigation bar are displayed
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(discoverTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(discoverTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(discoverNavLabel).assertIsDisplayed()
        }
    }

    @Test
    fun deepLinkDetails_survivesActivityRecreation() {
        val targetGameId = 1942L
        val targetTitle = "The Witcher 3: Wild Hunt"
        val deepLinkUri = Uri.parse("gametracker://game/$targetGameId")

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setClass(context, MainActivity::class.java)
            setPackage(BuildConfig.APPLICATION_ID)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            }

            // Recreate activity (simulating config change / orientation change)
            scenario.recreate()

            // Assert: Game Details is still the active destination
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText(targetTitle)[0].assertIsDisplayed()
        }
    }

    @Test
    fun libraryTab_survivesActivityRecreation() {
        val libraryNavLabel = context.getString(R.string.nav_library)
        val libraryTitle = context.getString(R.string.library_title)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Switch to Library tab
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(libraryNavLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(libraryNavLabel).performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(libraryTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(libraryTitle).assertIsDisplayed()

            // Recreate activity
            scenario.recreate()

            // Assert: Library tab is still selected
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(libraryTitle).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(libraryTitle).assertIsDisplayed()
        }
    }
}
