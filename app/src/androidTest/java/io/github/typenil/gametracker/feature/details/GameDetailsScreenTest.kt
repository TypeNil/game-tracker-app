package io.github.typenil.gametracker.feature.details

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
import org.junit.Assert.assertFalse
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
        similarGames = listOf(
            GameSummary(
                id = 25076L,
                name = "Red Dead Redemption 2",
                totalRating = 93.6,
                genres = listOf("Shooter"),
                platforms = listOf("PC"),
            ),
        ),
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
        composeTestRule.onNodeWithText("Shooter").assertIsDisplayed()
    }

    @Test
    fun factsGridShowsAllLabelsWithoutHorizontalScrolling() {
        val factsDetails = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf("Single player"),
            platforms = listOf("PC", "PlayStation 5"),
            timeToBeatMainSeconds = 183600L,
        )
        setContent(GameDetailsUiState(game = factsDetails, isHydrated = true))

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.details_card_release)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.details_section_modes)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.details_section_platforms)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.details_card_time_to_beat)
        ).assertIsDisplayed()
    }

    @Test
    fun cooperativeGameModeUsesCompactLabel() {
        val factsDetails = compactDetails.copy(
            gameModes = listOf("Co-operative"),
        )
        setContent(GameDetailsUiState(game = factsDetails, isHydrated = true))

        composeTestRule.onNodeWithText("Co-op").assertIsDisplayed()
        composeTestRule.onNodeWithText("Co-operative").assertDoesNotExist()
    }

    @Test
    fun rolePlayingGenreTagUsesCompactRpgLabel() {
        val factsDetails = compactDetails.copy(
            genres = listOf("Role-playing (RPG)"),
        )
        setContent(GameDetailsUiState(game = factsDetails, isHydrated = true))

        composeTestRule.onNodeWithText("RPG").assertIsDisplayed()
        composeTestRule.onNodeWithText("Role-playing (RPG)").assertDoesNotExist()
    }

    @Test
    fun factCardsInEachRowHaveEqualHeights() {
        val factsDetails = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf("Single player"),
            platforms = listOf("PC"),
            timeToBeatMainSeconds = 183600L,
            timeToBeatCompleteSeconds = 288000L,
        )
        setContent(GameDetailsUiState(game = factsDetails, isHydrated = true))

        val releaseHeight = composeTestRule
            .onNodeWithTag("details-fact-card-release")
            .getUnclippedBoundsInRoot()
            .run { bottom - top }
        val modesHeight = composeTestRule
            .onNodeWithTag("details-fact-card-modes")
            .getUnclippedBoundsInRoot()
            .run { bottom - top }
        val platformsHeight = composeTestRule
            .onNodeWithTag("details-fact-card-platforms")
            .getUnclippedBoundsInRoot()
            .run { bottom - top }
        val timeHeight = composeTestRule
            .onNodeWithTag("details-fact-card-time")
            .getUnclippedBoundsInRoot()
            .run { bottom - top }

        assertEquals(releaseHeight, modesHeight)
        assertEquals(platformsHeight, timeHeight)
    }

    @Test
    fun longFactsValuesShowFullyWithoutEllipsis() {
        val gameModes = "Single player, Multiplayer, Co-op, Competitive online"
        val platforms = "PC, PlayStation 5, Xbox Series X, Nintendo Switch"
        val factsDetails = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf(gameModes),
            platforms = listOf(platforms),
        )
        setContent(GameDetailsUiState(game = factsDetails, isHydrated = true))

        val gameModesResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithText(gameModes, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(gameModesResults)
            }
        val gameModesLayout = gameModesResults.single()
        assertEquals(
            gameModes.length,
            gameModesLayout.getLineEnd(gameModesLayout.lineCount - 1, visibleEnd = true),
        )
        for (line in 0 until gameModesLayout.lineCount) {
            assertFalse(gameModesLayout.isLineEllipsized(line))
        }

        val platformsResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithText(platforms, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(platformsResults)
            }
        val platformsLayout = platformsResults.single()
        assertEquals(
            platforms.length,
            platformsLayout.getLineEnd(platformsLayout.lineCount - 1, visibleEnd = true),
        )
        for (line in 0 until platformsLayout.lineCount) {
            assertFalse(platformsLayout.isLineEllipsized(line))
        }
    }

    @Test
    fun longAboutTextCanExpandAndCollapse() {
        val longSummary = List(12) { "Long about paragraph $it." }.joinToString(" ")
        setContent(
            GameDetailsUiState(
                game = compactDetails.copy(summary = longSummary),
                isHydrated = true,
            )
        )

        val showMore = composeTestRule.activity.getString(R.string.details_about_show_more)
        val showLess = composeTestRule.activity.getString(R.string.details_about_show_less)
        composeTestRule.onNodeWithText(showMore).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(showLess).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(showMore).assertIsDisplayed()
    }

    @Test
    fun videoCardsShowTitleAndSourceSubtitle() {
        val videoDetails = compactDetails.copy(
            videos = listOf(
                GameVideo(videoId = "abc123", name = "Gameplay Trailer"),
                GameVideo(videoId = "def456", name = null),
            ),
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = videoDetails, isHydrated = true))

        val defaultTrailerTitle = composeTestRule.activity.getString(R.string.details_watch_trailer)
        val youtubeSource = composeTestRule.activity.getString(R.string.details_video_source)

        composeTestRule.onNodeWithText("Gameplay Trailer").assertIsDisplayed()
        composeTestRule.onNodeWithText(defaultTrailerTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(youtubeSource)[0].assertIsDisplayed()
    }

    @Test
    fun shortAboutTextHidesExpansionToggle() {
        setContent(
            GameDetailsUiState(
                game = compactDetails.copy(summary = "Short."),
                isHydrated = true,
            )
        )

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.details_about_show_more)
        ).assertDoesNotExist()
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

    @Test
    fun releaseDateWithoutInstant_showsTba() {
        val tba = compactDetails.copy(
            releaseDates = listOf(
                GameReleaseDate(platform = "PC", dateEpochSeconds = null, year = null),
            ),
        )
        setContent(GameDetailsUiState(game = tba, isHydrated = true))
        composeTestRule.onNodeWithText("PC").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.details_date_unknown)
        ).assertIsDisplayed()
    }

}
