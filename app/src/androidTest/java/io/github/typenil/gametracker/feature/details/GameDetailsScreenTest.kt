package io.github.typenil.gametracker.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameCompany
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.GameVideo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDetailsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val details = GameDetails(
        id = 1942L,
        name = "The Witcher 3: Wild Hunt",
        coverUrl = null,
        rating = 93.7,
        totalRating = 92.7,
        totalRatingCount = 5451L,
        releaseDateEpochSeconds = 1431993600L,
        summary = "RPG masterpiece set in a dark fantasy world",
        genres = listOf("Role-playing (RPG)"),
        themes = listOf("Fantasy"),
        gameModes = listOf("Single player"),
        platforms = listOf("PC", "PlayStation 5"),
        releaseDates = listOf(GameReleaseDate(platform = "PC", dateEpochSeconds = 1431993600L, year = 2015)),
        companies = listOf(GameCompany(name = "CD Projekt RED", isDeveloper = true)),
        screenshots = listOf("https://example.com/shot1.jpg"),
        videos = listOf(GameVideo(videoId = "abc123", name = "Killing Monsters")),
        similarGames = listOf(GameSummary(id = 25076L, name = "Red Dead Redemption 2", totalRating = 93.6)),
        url = "https://www.igdb.com/games/the-witcher-3-wild-hunt"
    )

    /**
     * Compact hydrated fixture: header + videos + similar only, so every asserted
     * section composes inside the first viewport without scrolling (lazy rails
     * below the fold are covered by the manual emulator smoke; injected test
     * swipes proved unreliable against this layout).
     */
    private val compactDetails = details.copy(
        summary = null,
        genres = emptyList(),
        themes = emptyList(),
        gameModes = emptyList(),
        platforms = emptyList(),
        releaseDates = emptyList(),
        screenshots = emptyList()
    )

    private fun setContent(uiState: GameDetailsUiState, onGameClick: (Long) -> Unit = {}) {
        composeTestRule.setContent {
            GameTrackerTheme {
                GameDetailsScreen(
                    uiState = uiState,
                    onGameClick = onGameClick,
                    onBackClick = {},
                    onRefresh = {},
                    onRetry = {},
                    onUserMessageShown = {}
                )
            }
        }
    }

    @Test
    fun hydratedContentRendersAllSections() {
        setContent(GameDetailsUiState(game = compactDetails, isHydrated = true))

        // The title exists both in the TopAppBar and the header - assert one of them
        composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("5451 votes").assertIsDisplayed()
        val companies = composeTestRule.activity.getString(
            R.string.details_developed_by_format, "CD Projekt RED"
        )
        composeTestRule.onNodeWithText(companies).assertIsDisplayed()
        composeTestRule.onNodeWithText("Killing Monsters").assertIsDisplayed()
        composeTestRule.onNodeWithText("Red Dead Redemption 2").assertIsDisplayed()
    }

    @Test
    fun skeletonHidesEmptySections() {
        val skeleton = details.copy(
            totalRating = null,
            totalRatingCount = null,
            themes = emptyList(),
            gameModes = emptyList(),
            releaseDates = emptyList(),
            companies = emptyList(),
            screenshots = emptyList(),
            videos = emptyList(),
            similarGames = emptyList()
        )
        setContent(GameDetailsUiState(game = skeleton, isHydrated = false, isRefreshing = true))

        composeTestRule.onAllNodesWithText("The Witcher 3: Wild Hunt")[0].assertIsDisplayed()
        val videosHeader = composeTestRule.activity.getString(R.string.details_section_videos)
        composeTestRule.onNodeWithText(videosHeader).assertDoesNotExist()
        val similarHeader = composeTestRule.activity.getString(R.string.details_section_similar)
        composeTestRule.onNodeWithText(similarHeader).assertDoesNotExist()
    }

    @Test
    fun similarGameClickInvokesNavigationCallback() {
        var clickedGameId: Long? = null
        setContent(GameDetailsUiState(game = compactDetails, isHydrated = true)) { id -> clickedGameId = id }

        composeTestRule.onNodeWithText("Red Dead Redemption 2").performClick()

        composeTestRule.runOnIdle { assertEquals(25076L, clickedGameId) }
    }

    @Test
    fun screenshotClickOpensFullscreenViewerAndCanBeDismissed() {
        val screenshotsDetails = compactDetails.copy(
            screenshots = listOf("https://example.com/shot1.jpg", "https://example.com/shot2.jpg")
        )
        setContent(GameDetailsUiState(game = screenshotsDetails, isHydrated = true))

        val screenshotDesc = composeTestRule.activity.getString(R.string.details_screenshot_desc)
        composeTestRule.onAllNodesWithContentDescription(screenshotDesc)[0].performClick()

        val page1Text = composeTestRule.activity.getString(R.string.details_viewer_page_format, 1, 2)
        composeTestRule.onNodeWithText(page1Text).assertIsDisplayed()

        val closeDesc = composeTestRule.activity.getString(R.string.details_viewer_close_desc)
        composeTestRule.onNodeWithContentDescription(closeDesc).performClick()

        composeTestRule.onNodeWithText(page1Text).assertDoesNotExist()
    }

    @Test
    fun errorWithoutContentShowsRetry() {
        setContent(GameDetailsUiState(error = AppError.NetworkError, isLoading = false))

        val retryLabel = composeTestRule.activity.getString(R.string.retry_button)
        composeTestRule.onNodeWithText(retryLabel).assertIsDisplayed()
    }
}
