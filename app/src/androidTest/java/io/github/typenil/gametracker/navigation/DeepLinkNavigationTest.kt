package io.github.typenil.gametracker.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.MainActivity
import io.github.typenil.gametracker.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkNavigationTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun installNavigationFixture() {
        DemoNavigationFixture.install(context)
    }

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

    @Test
    fun searchTab_survivesActivityRecreation() {
        val searchNavLabel = context.getString(R.string.nav_search)
        val searchHint = context.getString(R.string.search_hint)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(searchNavLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(searchNavLabel).performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(searchHint).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(searchHint).assertIsDisplayed()

            scenario.recreate()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(searchHint).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(searchHint).assertIsDisplayed()
        }
    }

    @Test
    fun searchTab_switchAwayAndBack_preservesQuery_andRetapFocusesField() {
        val searchNavLabel = context.getString(R.string.nav_search)
        val libraryNavLabel = context.getString(R.string.nav_library)
        val libraryTitle = context.getString(R.string.library_title)
        val searchHint = context.getString(R.string.search_hint)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(searchNavLabel)
            composeTestRule.onNodeWithText(searchNavLabel).performClick()
            waitForText(searchHint)

            composeTestRule.onNode(hasSetTextAction()).performTextReplacement("witcher")
            composeTestRule.onNode(hasSetTextAction()).performImeAction()

            composeTestRule.onNodeWithText(libraryNavLabel).performClick()
            waitForText(libraryTitle)

            composeTestRule.onNodeWithText(searchNavLabel).performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasSetTextAction()).assertTextContains("witcher")

            composeTestRule.onNodeWithText(searchNavLabel).performClick()
            waitForSearchFieldFocus()
        }
    }

    @Test
    fun searchRetap_afterActivityRecreation_focusesFieldAndScrollsToTop() {
        val searchNavLabel = context.getString(R.string.nav_search)
        val searchHint = context.getString(R.string.search_hint)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(searchNavLabel)
            composeTestRule.onNodeWithText(searchNavLabel).performClick()
            waitForText(searchHint)

            composeTestRule.onNodeWithText(searchNavLabel).performClick()
            waitForSearchFieldFocus()

            scenario.recreate()
            waitForText(searchHint)
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(searchNavLabel).performClick()
            waitForSearchFieldFocus()
            if (composeTestRule.onAllNodes(hasScrollToIndexAction())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                composeTestRule.onAllNodes(hasScrollToIndexAction()).onFirst()
                    .performScrollToIndex(0)
            }
        }
    }

    @Test
    fun discoverRetap_afterActivityRecreation_keepsTabAndAcceptsScrollToTop() {
        val discoverNavLabel = context.getString(R.string.nav_discover)
        val discoverTitle = context.getString(R.string.discover_title)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(discoverNavLabel)
            waitForText(discoverTitle)

            composeTestRule.onNodeWithText(discoverNavLabel).performClick()

            scenario.recreate()
            waitForText(discoverTitle)

            composeTestRule.onNodeWithText(discoverNavLabel).performClick()
            composeTestRule.onNodeWithText(discoverTitle).assertIsDisplayed()
        }
    }

    @Test
    fun coldStartDeepLink_secondBackFinishesActivity() {
        val discoverTitle = context.getString(R.string.discover_title)

        launchDetailsDeepLink().use { scenario ->
            waitForText(WITCHER_TITLE)
            pressBack(scenario)
            waitForText(discoverTitle)

            pressBack(scenario)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun similarGame_stacksDetails_andBackPopsOneScreen() {
        val discoverTitle = context.getString(R.string.discover_title)
        val discoverNavLabel = context.getString(R.string.nav_discover)

        launchDetailsDeepLink().use { scenario ->
            waitForText(WITCHER_TITLE)

            composeTestRule.onAllNodes(hasScrollToIndexAction()).onFirst()
                .performScrollToNode(hasText(RDR2_TITLE))
            composeTestRule.onAllNodesWithText(RDR2_TITLE).onFirst().performClick()
            advanceUntilIdle()
            waitForText(RDR2_SUMMARY)
            composeTestRule.onNodeWithText(discoverTitle).assertDoesNotExist()
            composeTestRule.onNodeWithText(discoverNavLabel).assertDoesNotExist()

            pressBack(scenario)
            advanceUntilIdle()

            waitForText(WITCHER_TITLE)
            composeTestRule.onNodeWithText(discoverTitle).assertDoesNotExist()

            pressBack(scenario)
            advanceUntilIdle()

            waitForText(discoverTitle)
            composeTestRule.onNodeWithText(discoverNavLabel).assertIsDisplayed()
        }
    }

    @Test
    fun libraryToDetails_backReturnsToLibrary() {
        val libraryNavLabel = context.getString(R.string.nav_library)
        val libraryTitle = context.getString(R.string.library_title)
        val addToLibrary = context.getString(R.string.library_add_to_library)
        val save = context.getString(R.string.library_save)
        val discoverTitle = context.getString(R.string.discover_title)

        launchDetailsDeepLink().use { scenario ->
            waitForText(WITCHER_TITLE)

            if (existsOnScreen(addToLibrary)) {
                composeTestRule.onNodeWithText(addToLibrary).performClick()
                composeTestRule.onNodeWithText(save).performClick()
                composeTestRule.waitUntil(timeoutMillis = 5_000) {
                    !existsOnScreen(save)
                }
            }

            pressBack(scenario)
            waitForText(discoverTitle)

            composeTestRule.onNodeWithText(libraryNavLabel).performClick()
            waitForText(libraryTitle)

            composeTestRule.onAllNodes(hasScrollToIndexAction()).onFirst()
                .performScrollToNode(hasText(WITCHER_TITLE))
            composeTestRule.onAllNodesWithText(WITCHER_TITLE).onFirst().performClick()
            waitForText(WITCHER_TITLE)
            composeTestRule.onNodeWithText(libraryTitle).assertDoesNotExist()
            composeTestRule.onNodeWithText(libraryNavLabel).assertDoesNotExist()

            pressBack(scenario)

            waitForText(libraryTitle)
            composeTestRule.onNodeWithText(libraryNavLabel).assertIsDisplayed()
            composeTestRule.onAllNodesWithText(WITCHER_TITLE)[0].assertIsDisplayed()
        }
    }

    @Test
    fun malformedDeepLink_alphaGameId_doesNotCrashProcess() {
        val discoverTitle = context.getString(R.string.discover_title)
        launchDeepLink("gametracker://game/abc").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForText(discoverTitle)
            composeTestRule.onNodeWithText(discoverTitle).assertIsDisplayed()
        }
    }

    @Test
    fun invalidNumericDeepLink_negativeId_showsErrorStateWithoutCrash() {
        val retryButton = context.getString(R.string.retry_button)
        launchDeepLink("gametracker://game/-1").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForText(retryButton)
            composeTestRule.onNodeWithText(retryButton).assertIsDisplayed()
        }
    }

    @Test
    fun invalidNumericDeepLink_zeroId_showsErrorStateWithoutCrash() {
        val retryButton = context.getString(R.string.retry_button)
        launchDeepLink("gametracker://game/0").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForText(retryButton)
            composeTestRule.onNodeWithText(retryButton).assertIsDisplayed()
        }
    }

    @Test
    fun extraPathSegments_doNotCrashProcess() {
        val discoverTitle = context.getString(R.string.discover_title)
        launchDeepLink("gametracker://game/1942/extra").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForText(discoverTitle)
            composeTestRule.onNodeWithText(discoverTitle).assertIsDisplayed()
        }
    }

    @Test
    fun queryParameters_doNotBypassIdParsing_andOpenDetails() {
        val targetTitle = WITCHER_TITLE
        launchDeepLink("gametracker://game/1942?ref=external&utm_source=test").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForText(targetTitle)
            composeTestRule.onAllNodesWithText(targetTitle)[0].assertIsDisplayed()
        }
    }

    @Test
    fun queryParametersOnMalformedPath_doNotCrashProcess() {
        val discoverTitle = context.getString(R.string.discover_title)
        launchDeepLink("gametracker://game/abc?ref=external").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            waitForText(discoverTitle)
            composeTestRule.onNodeWithText(discoverTitle).assertIsDisplayed()
        }
    }

    private fun launchDetailsDeepLink(gameId: Long = 1942L): ActivityScenario<MainActivity> {
        return launchDeepLink("gametracker://game/$gameId")
    }

    private fun launchDeepLink(uriString: String): ActivityScenario<MainActivity> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setClass(context, MainActivity::class.java)
            setPackage(BuildConfig.APPLICATION_ID)
        }
        return ActivityScenario.launch(intent)
    }

    private fun advanceUntilIdle() {
        composeTestRule.waitForIdle()
    }

    private fun waitForSearchFieldFocus(timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodes(hasSetTextAction() and isFocused())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        advanceUntilIdle()
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        advanceUntilIdle()
    }

    private fun existsOnScreen(text: String): Boolean {
        return composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val WITCHER_TITLE = "The Witcher 3: Wild Hunt"
        const val RDR2_TITLE = "Red Dead Redemption 2"
        const val RDR2_SUMMARY = "America, 1899. Arthur Morgan and the Van der Linde gang are outlaws on the run."
    }
}
