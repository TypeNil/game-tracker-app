package io.github.typenil.gametracker.feature.details
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.GameCompany
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertTrue
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
        releaseDateEpochSeconds = null,
        screenshots = emptyList(),
        videos = emptyList(),
    )
    private fun setContent(
        uiState: GameDetailsUiState,
        fontScale: Float = 1f,
        onGameClick: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = fontScale,
                ),
            ) {
                GameTrackerTheme {
                    GameDetailsScreen(
                        uiState = uiState,
                        onGameClick = onGameClick,
                        onBackClick = {},
                        onRefresh = {},
                        onRetry = {},
                        onUserMessageShown = {},
                    )
                }
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
    fun longFactValuesAreTruncatedWithEllipsisInFactCards() {
        val longGameMode = "Single player, Multiplayer, Co-op, Competitive online, Massively Multiplayer Online"
        val factsDetails = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf(longGameMode),
            platforms = listOf("PC"),
        )
        setContent(GameDetailsUiState(game = factsDetails, isHydrated = true))

        val gameModesResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithText(longGameMode, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(gameModesResults)
            }
        val gameModesLayout = gameModesResults.single()
        assertEquals(1, gameModesLayout.lineCount)
        assertTrue(gameModesLayout.isLineEllipsized(0))
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
        val collapsedHeight = composeTestRule
            .onNodeWithText(longSummary, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .run { bottom - top }

        composeTestRule
            .onNodeWithContentDescription(showMore)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        val expandedHeight = composeTestRule
            .onNodeWithText(longSummary, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .run { bottom - top }
        assertTrue(
            "Expanded height ($expandedHeight) must be greater than collapsed ($collapsedHeight)",
            expandedHeight > collapsedHeight,
        )

        composeTestRule
            .onNodeWithContentDescription(showLess)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(showMore).assertIsDisplayed()
    }
    @Test
    fun collapsedAboutShowsAtMostTwoLines() {
        val longSummary = List(12) { "Long about paragraph $it." }.joinToString(" ")
        setContent(
            GameDetailsUiState(
                game = compactDetails.copy(summary = longSummary),
                isHydrated = true,
            )
        )

        val results = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithText(longSummary, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(results)
            }
        val layout = results.single()
        assertTrue("Collapsed About must show at most 2 lines", layout.lineCount <= 2)
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

        composeTestRule.onNodeWithContentDescription(
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
        val fixture = compactDetails.copy(
            similarGames = listOf(
                GameSummary(
                    id = 25076L,
                    name = "Red Dead Redemption 2",

                    totalRating = 93.6,
                )
            )
        )
        setContent(GameDetailsUiState(game = fixture, isHydrated = true)) { id -> clickedGameId = id }

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
            releaseDateEpochSeconds = null,
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

    @Test
    fun undatedPlatformReleaseFallsBackToGeneralReleaseDate() {
        val game = compactDetails.copy(
            releaseDateEpochSeconds = 1431993600L, // 2015-05-19
            releaseDates = listOf(
                GameReleaseDate(
                    platform = "PC",
                    dateEpochSeconds = null,
                    year = null,
                )
            ),
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        val releaseTitle = composeTestRule.activity.getString(R.string.details_card_release)
        val unknownDate = composeTestRule.activity.getString(R.string.details_date_unknown)
        val expectedDate = GameReleaseDate(platform = "", dateEpochSeconds = 1431993600L).displayDate(unknownDate)

        composeTestRule.onNodeWithText(releaseTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
        composeTestRule.onNodeWithText("PC").assertIsDisplayed()
    }

    @Test
    fun videosCollapseToShowAllToggleRevealsRest() {
        val videoDetails = compactDetails.copy(
            videos = listOf(
                GameVideo(videoId = "vid1", name = "Trailer One"),
                GameVideo(videoId = "vid2", name = "Trailer Two"),
                GameVideo(videoId = "vid3", name = "Trailer Three"),
            ),
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = videoDetails, isHydrated = true))

        composeTestRule.onNodeWithText("Trailer One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trailer Two").assertIsDisplayed()
        val showAll = composeTestRule.activity.getString(R.string.details_videos_show_all, 3)
        composeTestRule.onNodeWithText("Trailer Three").assertDoesNotExist()
        composeTestRule.onNodeWithText(showAll).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Trailer Three").assertIsDisplayed()
        val showLess = composeTestRule.activity.getString(R.string.details_videos_show_less)
        composeTestRule.onNodeWithText(showLess).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Trailer Three").assertDoesNotExist()
    }

    @Test
    fun twoVideosShowNoToggle() {
        val videoDetails = compactDetails.copy(
            videos = listOf(
                GameVideo(videoId = "vid1", name = "Trailer One"),
                GameVideo(videoId = "vid2", name = "Trailer Two"),
            ),
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = videoDetails, isHydrated = true))

        val showAll = composeTestRule.activity.getString(R.string.details_videos_show_all, 2)
        composeTestRule.onNodeWithText(showAll).assertDoesNotExist()
    }

    @Test
    fun loneLeftoverFactCardSpansFullRow() {
        val threeCards = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf("Single player"),
            platforms = listOf("PC", "PlayStation 5"),
        )
        setContent(GameDetailsUiState(game = threeCards, isHydrated = true))

        val releaseWidth = composeTestRule
            .onNodeWithTag("details-fact-card-release")
            .getUnclippedBoundsInRoot().run { right - left }
        val modesWidth = composeTestRule
            .onNodeWithTag("details-fact-card-modes")
            .getUnclippedBoundsInRoot().run { right - left }
        val platformsWidth = composeTestRule
            .onNodeWithTag("details-fact-card-platforms")
            .getUnclippedBoundsInRoot().run { right - left }

        assertEquals(
            releaseWidth + modesWidth + 12.dp,
            platformsWidth
        )
    }

    @Test
    fun twoFactCardsShareRowEqually() {
        val twoCards = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf("Single player"),
        )
        setContent(GameDetailsUiState(game = twoCards, isHydrated = true))

        val releaseWidth = composeTestRule
            .onNodeWithTag("details-fact-card-release")
            .getUnclippedBoundsInRoot()
            .run { right - left }
        val modesWidth = composeTestRule
            .onNodeWithTag("details-fact-card-modes")
            .getUnclippedBoundsInRoot()
            .run { right - left }
        assertEquals(releaseWidth, modesWidth)
    }

    @Test
    fun inLibraryCardShowsStatusWithoutPlatform() {
        val game = compactDetails.copy(
            platforms = listOf("PC", "PlayStation 5"),
            releaseDates = details.releaseDates,
        )
        val entry = LibraryEntry(
            gameId = game.id,
            status = LibraryStatus.PLAYING,
            addedAtEpochSeconds = 0L,
            updatedAtEpochSeconds = 0L,
        )
        setContent(GameDetailsUiState(game = game, libraryEntry = entry, isHydrated = true))

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_in_library)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.library_status_playing)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Playing · PC").assertDoesNotExist()
    }

    @Test
    fun nullGameRendersHeaderSkeletonInsteadOfSpinner() {
        setContent(GameDetailsUiState(game = null, isLoading = true))

        composeTestRule.onNodeWithTag("details-skeleton").assertIsDisplayed()
    }

    @Test
    fun nullSummaryHidesAboutSection() {
        val game = compactDetails.copy(summary = null)
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        val aboutHeader = composeTestRule.activity.getString(R.string.details_section_about)
        composeTestRule.onNodeWithText(aboutHeader).assertDoesNotExist()
    }

    @Test
    fun nonNullSummaryShowsAboutSection() {
        val game = compactDetails.copy(summary = "An epic open world RPG adventure.")
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        val aboutHeader = composeTestRule.activity.getString(R.string.details_section_about)
        composeTestRule.onNodeWithText(aboutHeader).assertIsDisplayed()
    }

    @Test
    fun releaseDateEpochSecondsFallbackShowsReleaseCard() {
        val game = compactDetails.copy(
            releaseDates = emptyList(),
            releaseDateEpochSeconds = 1431993600L, // May 19, 2015
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        val releaseTitle = composeTestRule.activity.getString(R.string.details_card_release)
        val unknownDate = composeTestRule.activity.getString(R.string.details_date_unknown)
        val expectedDate = GameReleaseDate(platform = "", dateEpochSeconds = 1431993600L).displayDate(unknownDate)

        composeTestRule.onNodeWithText(releaseTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
    }

    @Test
    fun aboutHeaderHasAtLeast48dpTouchBoundsAndExpandsOnClick() {
        val longSummary = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8"
        val game = compactDetails.copy(summary = longSummary, similarGames = emptyList())
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        val showMoreDesc = composeTestRule.activity.getString(R.string.details_about_show_more)
        val showLessDesc = composeTestRule.activity.getString(R.string.details_about_show_less)

        val bounds = composeTestRule
            .onNodeWithContentDescription(showMoreDesc)
            .getUnclippedBoundsInRoot()
        assertTrue((bounds.bottom - bounds.top) >= 48.dp)

        composeTestRule.onNodeWithContentDescription(showMoreDesc).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(showLessDesc).assertIsDisplayed()
    }

    @Test
    fun tagOverloadShowsPreviewAndOpensBottomSheet() {
        val genres = listOf("RPG", "Adventure", "Shooter", "Platform", "Puzzle")
        val themes = listOf("Fantasy", "Sci-Fi", "Horror", "Survival", "Historical")
        val game = compactDetails.copy(genres = genres, themes = themes, similarGames = emptyList())
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        // First tag shown in single-line preview
        composeTestRule.onNodeWithText("RPG", useUnmergedTree = true).assertIsDisplayed()

        // Overflow "+9 more" chip is shown with a11y content description and is clickable
        val overflowDesc = composeTestRule.activity.getString(
            R.string.details_more_tags_desc,
            genres.size + themes.size,
        )
        val bounds = composeTestRule
            .onNodeWithContentDescription(overflowDesc)
            .getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left) >= 48.dp)
        assertTrue((bounds.bottom - bounds.top) >= 48.dp)

        composeTestRule.onNodeWithContentDescription(overflowDesc).assertIsDisplayed().performClick()
        genres.forEach { genre ->
            composeTestRule.onAllNodesWithText(genre, useUnmergedTree = true)[0].assertIsDisplayed()
        }
        themes.forEach { theme ->
            composeTestRule.onAllNodesWithText(theme, useUnmergedTree = true)[0].assertIsDisplayed()
        }
    }

    @Test
    fun tagPreview_withTwoTags_showsBothTagsWithoutOverflow() {
        val genres = listOf("Role-playing (RPG)", "Adventure")
        val game = compactDetails.copy(genres = genres, themes = emptyList(), similarGames = emptyList())
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        val rpgNode = composeTestRule.onNodeWithText("RPG", useUnmergedTree = true)
        val adventureNode = composeTestRule.onNodeWithText("Adventure", useUnmergedTree = true)

        rpgNode.assertIsDisplayed()
        adventureNode.assertIsDisplayed()

        // No overflow chip is displayed when all 2 tags fit in preview
        val zeroOverflowText = composeTestRule.activity.getString(R.string.details_more_count, 0)
        composeTestRule.onNodeWithText(zeroOverflowText).assertDoesNotExist()
        val overflowDesc = composeTestRule.activity.getString(R.string.details_more_tags_desc, 2)
        composeTestRule.onNodeWithContentDescription(overflowDesc).assertDoesNotExist()
    }

    @Test
    fun tagPreview_atTwoXFontScale_staysOnOneRowAndKeepsOverflowTouchTarget() {
        val genres = listOf("Hack and slash/Beat 'em up", "Role-playing (RPG)", "Adventure")
        val themes = listOf("Fantasy", "Sci-Fi")
        val game = compactDetails.copy(genres = genres, themes = themes, similarGames = emptyList())
        setContent(
            uiState = GameDetailsUiState(game = game, isHydrated = true),
            fontScale = 2f,
        )

        val tagNode = composeTestRule.onNodeWithText("Hack & Slash / Beat 'em up", useUnmergedTree = true)
        tagNode.assertIsDisplayed()

        val totalTags = genres.size + themes.size
        val overflowDesc = composeTestRule.activity.getString(
            R.string.details_more_tags_desc,
            totalTags,
        )
        val overflowNode = composeTestRule.onNodeWithContentDescription(overflowDesc)
        overflowNode.assertIsDisplayed()

        val tagBounds = tagNode.getUnclippedBoundsInRoot()
        val overflowBounds = overflowNode.getUnclippedBoundsInRoot()

        // The tag preview and overflow chip sit side-by-side on the same row (tag is to the left of overflow chip)
        assertTrue(tagBounds.right <= overflowBounds.left)
        // Tag vertical bounds are centered within the row height of the overflow chip
        assertTrue(tagBounds.top >= overflowBounds.top)
        assertTrue(tagBounds.bottom <= overflowBounds.bottom)

        // Overflow chip maintains 48x48dp hit box
        assertTrue((overflowBounds.right - overflowBounds.left) >= 48.dp)
        assertTrue((overflowBounds.bottom - overflowBounds.top) >= 48.dp)
    }

    @Test
    fun platformsFactCard_whenFullWidth_showsUpToFourPlatforms() {
        val platforms = listOf("PC", "PlayStation 5", "Xbox Series X", "Nintendo Switch")
        val game = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf("Single player"),
            platforms = platforms,
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        // 3 cards total (Release, Modes, Platforms) -> Platforms spans full width and displays 4 platforms
        val platformsText = "PC, PlayStation 5, Xbox Series X, Nintendo Switch"
        composeTestRule
            .onNodeWithText(platformsText, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertNotEllipsized(maxLines = 2)

        // No overflow text since all 4 fit in full width limit
        val zeroOverflowText = composeTestRule.activity.getString(R.string.details_more_count, 0)
        composeTestRule.onNodeWithText(zeroOverflowText).assertDoesNotExist()
    }

    @Test
    fun platformsFactCard_whenHalfWidth_showsTwoPlatformsAndOverflow() {
        val platforms = listOf("PC", "PlayStation 5", "Xbox Series X", "Nintendo Switch")
        val game = compactDetails.copy(
            releaseDates = details.releaseDates,
            gameModes = listOf("Single player"),
            platforms = platforms,
            timeToBeatMainSeconds = 36000L,
            similarGames = emptyList(),
        )
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        // 4 cards total (Release, Modes, Platforms, Time) -> Platforms is half width and displays 2 platforms
        val platformsText = "PC, PlayStation 5"
        composeTestRule
            .onNodeWithText(platformsText, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertNotEllipsized(maxLines = 1)

        val overflowText = composeTestRule.activity.getString(R.string.details_more_count, 2)
        composeTestRule.onNodeWithText(overflowText).assertIsDisplayed()
    }
    @Test
    fun gameModesFactCardWithMultipleModesOpensBottomSheet() {
        val modes = listOf("Single player", "Multiplayer", "Co-operative", "Split screen")
        val game = compactDetails.copy(gameModes = modes, similarGames = emptyList())
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        // Fact card shows preview and overflow "+2 more"
        composeTestRule.onNodeWithTag("details-fact-card-modes").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Bottom sheet opens and displays all game modes
        composeTestRule.onAllNodesWithText("Single-player")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Co-op")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Split screen")[0].assertIsDisplayed()
    }
    @Test
    fun platformsFactCardWithMultiplePlatformsOpensBottomSheet() {
        val platforms = listOf("PC", "PlayStation 5", "Xbox Series X", "Nintendo Switch")
        val releaseDates = listOf(
            GameReleaseDate(platform = "PC", dateEpochSeconds = 1700000000L, year = 2023),
            GameReleaseDate(platform = "PlayStation 5", dateEpochSeconds = 1700000000L, year = 2023),
            GameReleaseDate(platform = "Xbox Series X", dateEpochSeconds = 1700000000L, year = 2023),
            GameReleaseDate(platform = "Nintendo Switch", dateEpochSeconds = 1700000000L, year = 2023),
        )
        val game = compactDetails.copy(platforms = platforms, releaseDates = releaseDates, similarGames = emptyList())
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        // Platforms fact card shows preview and overflow "+2 more"
        composeTestRule.onNodeWithTag("details-fact-card-platforms").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Bottom sheet opens and displays all platforms with release dates
        composeTestRule.onAllNodesWithText("PlayStation 5")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Nintendo Switch")[0].assertIsDisplayed()
    }
    @Test
    fun yearOnlyReleaseDateDisplaysYear() {
        val releaseDates = listOf(
            GameReleaseDate(platform = "PlayStation 5", dateEpochSeconds = null, year = 2026)
        )
        val game = compactDetails.copy(releaseDates = releaseDates)
        setContent(GameDetailsUiState(game = game, isHydrated = true))

        composeTestRule.onNodeWithText("2026", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun noMediaDisplaysGracefullyWithoutCrashing() {
        val noMediaGame = compactDetails.copy(
            coverUrl = null,
            artworkUrl = null,
            screenshots = emptyList(),
            videos = emptyList(),
            url = null,
            summary = "A game without media assets."
        )
        setContent(GameDetailsUiState(game = noMediaGame, isHydrated = true))

        composeTestRule.onAllNodesWithText(noMediaGame.name)[0].assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.details_section_screenshots)).assertDoesNotExist()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.details_section_videos)).assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.assertNotEllipsized(
        maxLines: Int,
    ): SemanticsNodeInteraction = apply {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(results)
        }
        val layout = results.single()
        assertTrue(layout.lineCount <= maxLines)
        assertFalse(layout.hasVisualOverflow)
        assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized))
    }
}
