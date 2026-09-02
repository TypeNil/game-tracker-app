package io.github.typenil.gametracker.feature.library

import app.cash.turbine.test
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.mockk.coVerify
import io.mockk.mockk
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val fakeLibraryRepository = FakeLibraryRepository()
    private val fakeGameRepository: GameRepository = mockk(relaxed = true)
    @Before
    fun setUp() {
        io.mockk.coEvery { fakeGameRepository.refreshGameDetails(any(), any()) } returns AppResult.Success(Unit)
    }

    private val hades = LibraryGame(
        game = Game(id = 1L, name = "Hades", coverUrl = null, rating = 93.0, releaseDateEpochSeconds = 100L),
        entry = LibraryEntry(
            gameId = 1L,
            status = LibraryStatus.PLAYING,
            userRating = 10,
            isFavorite = true,
            addedAtEpochSeconds = 100L,
            updatedAtEpochSeconds = 500L,
            hoursPlayed = 60
        )
    )

    private val eldenRing = LibraryGame(
        game = Game(id = 2L, name = "Elden Ring", coverUrl = null, rating = 96.0, releaseDateEpochSeconds = 200L),
        entry = LibraryEntry(
            gameId = 2L,
            status = LibraryStatus.COMPLETED,
            userRating = 9,
            isFavorite = true,
            addedAtEpochSeconds = 200L,
            updatedAtEpochSeconds = 400L,
            hoursPlayed = 150
        )
    )

    private val hollowKnight = LibraryGame(
        game = Game(id = 3L, name = "Hollow Knight", coverUrl = null, rating = 90.0, releaseDateEpochSeconds = 300L),
        entry = LibraryEntry(
            gameId = 3L,
            status = LibraryStatus.WISHLIST,
            userRating = null,
            isFavorite = false,
            addedAtEpochSeconds = 300L,
            updatedAtEpochSeconds = 300L,
            hoursPlayed = 0
        )
    )

    private val badGame = LibraryGame(
        game = Game(id = 4L, name = "Bad Game", coverUrl = null, rating = 40.0, releaseDateEpochSeconds = 400L),
        entry = LibraryEntry(
            gameId = 4L,
            status = LibraryStatus.NOT_INTERESTED,
            userRating = 1,
            isFavorite = false,
            addedAtEpochSeconds = 400L,
            updatedAtEpochSeconds = 200L,
            hoursPlayed = 1
        )
    )

    private fun createViewModel(): LibraryViewModel = LibraryViewModel(fakeLibraryRepository, fakeGameRepository)

    @Test
    fun `onCardVisible deduplicates in-flight requests and permits later retry`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)

        io.mockk.coEvery { fakeGameRepository.refreshGameDetails(1L, force = false) } coAnswers {
            callCount.incrementAndGet()
            gate.await()
            AppResult.Success(Unit)
        }

        val viewModel = createViewModel()
        viewModel.onCardVisible(hades.copy(bannerUrl = null))
        viewModel.onCardVisible(hades.copy(bannerUrl = null))

        assertEquals(1, callCount.get())

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        viewModel.onCardVisible(hades.copy(bannerUrl = null))
        testScheduler.advanceUntilIdle()

        assertEquals(2, callCount.get())
    }

    @Test
    fun `onCardVisible does not refresh already hydrated banner`() = runTest {
        val viewModel = createViewModel()
        viewModel.onCardVisible(hades.copy(bannerUrl = "https://example.com/banner.jpg"))
        coVerify(exactly = 0) {
            fakeGameRepository.refreshGameDetails(any(), any())
        }
    }

    @Test
    fun `onCardVisible retries after failed hydration`() = runTest {
        io.mockk.coEvery { fakeGameRepository.refreshGameDetails(1L, force = false) } returns
            AppResult.Error(AppError.NetworkError) andThen AppResult.Success(Unit)

        val viewModel = createViewModel()
        viewModel.onCardVisible(hades.copy(bannerUrl = null))
        viewModel.onCardVisible(hades.copy(bannerUrl = null))

        coVerify(exactly = 2) {
            fakeGameRepository.refreshGameDetails(1L, force = false)
        }
    }

    @Test
    fun `onCardVisible caps queue and serializes detail requests`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val requestedIds = mutableListOf<Long>()
        var inFlight = 0
        var peakInFlight = 0

        io.mockk.coEvery { fakeGameRepository.refreshGameDetails(any(), any()) } coAnswers {
            requestedIds.add(firstArg())
            inFlight++
            peakInFlight = maxOf(peakInFlight, inFlight)
            try {
                gate.await()
                AppResult.Success(Unit)
            } finally {
                inFlight--
            }
        }

        val viewModel = createViewModel()
        for (id in 1L..50L) {
            val game = hades.copy(game = hades.game.copy(id = id), bannerUrl = null)
            viewModel.onCardVisible(game)
        }

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, peakInFlight)
        assertEquals(0, inFlight)
        assertEquals(17, requestedIds.size)
        assertEquals(1L, requestedIds.first())
        assertEquals((35L..50L).toList(), requestedIds.drop(1))
    }

    @Test
    fun `init computes tab counts excluding NOT_INTERESTED from ALL`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, eldenRing, hollowKnight, badGame)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.tabCounts[LibraryTab.ALL])
            assertEquals(1, state.tabCounts[LibraryTab.PLAYING])
            assertEquals(1, state.tabCounts[LibraryTab.WISHLIST])
            assertEquals(1, state.tabCounts[LibraryTab.COMPLETED])
            assertEquals(0, state.tabCounts[LibraryTab.DROPPED])
            assertEquals(1, state.tabCounts[LibraryTab.NOT_INTERESTED])
            assertEquals(3, state.filteredGames.size) // ALL tab shows Hades, Elden Ring, Hollow Knight
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting tab filters games accordingly`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, eldenRing, hollowKnight, badGame)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTabSelected(LibraryTab.PLAYING)
            val playingState = awaitItem()
            assertEquals(listOf(hades), playingState.filteredGames)

            viewModel.onTabSelected(LibraryTab.NOT_INTERESTED)
            val notInterestedState = awaitItem()
            assertEquals(listOf(badGame), notInterestedState.filteredGames)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorites filter isolates favorite games`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, eldenRing, hollowKnight, badGame)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onToggleFavoritesOnly()
            val favState = awaitItem()
            assertTrue(favState.filterFavoritesOnly)
            assertEquals(listOf(eldenRing, hades), favState.filteredGames)

            viewModel.onToggleFavoritesOnly()
            val allState = awaitItem()
            assertFalse(allState.filterFavoritesOnly)
            assertEquals(3, allState.filteredGames.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query filters games by name`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, eldenRing, hollowKnight, badGame)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onSearchQueryChanged("knight")
            val searchState = awaitItem()
            assertEquals(listOf(hollowKnight), searchState.filteredGames)

            viewModel.onClearSearch()
            val clearedState = awaitItem()
            assertEquals(3, clearedState.filteredGames.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sorting options sort games by expected properties`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, eldenRing, hollowKnight)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial sort is ADDED_DESC: hollowKnight (300), eldenRing (200), hades (100)
            val initial = awaitItem()
            assertEquals(listOf(hollowKnight, eldenRing, hades), initial.filteredGames)

            // UPDATED_DESC: hades (500), eldenRing (400), hollowKnight (300)
            viewModel.onSortOptionSelected(LibrarySortOption.UPDATED_DESC)
            val updatedState = awaitItem()
            assertEquals(listOf(hades, eldenRing, hollowKnight), updatedState.filteredGames)
            // USER_RATING_DESC: hades (10), eldenRing (9), hollowKnight (null)
            viewModel.onSortOptionSelected(LibrarySortOption.USER_RATING_DESC)
            val ratingState = awaitItem()
            assertEquals(listOf(hades, eldenRing, hollowKnight), ratingState.filteredGames)

            // TITLE_ASC: Elden Ring, Hades, Hollow Knight
            viewModel.onSortOptionSelected(LibrarySortOption.TITLE_ASC)
            val titleState = awaitItem()
            assertEquals(listOf(eldenRing, hades, hollowKnight), titleState.filteredGames)

            // HOURS_PLAYED_DESC: Elden Ring (150), Hades (60), Hollow Knight (0)
            viewModel.onSortOptionSelected(LibrarySortOption.HOURS_PLAYED_DESC)
            val hoursState = awaitItem()
            assertEquals(listOf(eldenRing, hades, hollowKnight), hoursState.filteredGames)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleFavorite calls toggleFavorite with game id`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.onToggleFavorite(hades.game.id)
        assertEquals(1L, fakeLibraryRepository.lastToggleGameId)
    }

    @Test
    fun `onToggleFavorite error exposes update failure message`() = runTest {
        fakeLibraryRepository.toggleResult = AppResult.Error(
            AppError.UnknownError(IllegalStateException("missing")),
        )
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertNull(awaitItem().userMessageRes)
            viewModel.onToggleFavorite(hades.game.id)
            assertEquals(
                R.string.error_library_update_failed,
                awaitItem().userMessageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStatusSelected calls setGameStatus with game id`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.onStatusSelected(hades.game.id, LibraryStatus.COMPLETED)
        assertEquals(1L, fakeLibraryRepository.lastStatusGameId)
        assertEquals(LibraryStatus.COMPLETED, fakeLibraryRepository.lastStatus)
    }

    @Test
    fun `onStatusSelected error exposes update failure message`() = runTest {
        fakeLibraryRepository.setStatusResult = AppResult.Error(
            AppError.UnknownError(IllegalStateException("missing")),
        )
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertNull(awaitItem().userMessageRes)
            viewModel.onStatusSelected(hades.game.id, LibraryStatus.DROPPED)
            assertEquals(
                R.string.error_library_update_failed,
                awaitItem().userMessageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onHoursUpdated emits Saving then Saved on success`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeLibraryRepository.delayUpdateHours = gate
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(HoursSaveState.Idle, awaitItem().hoursSaveState)
            viewModel.onHoursUpdated(hades.game.id, 120)
            assertEquals(HoursSaveState.Saving(hades.game.id), awaitItem().hoursSaveState)
            gate.complete(Unit)
            assertEquals(HoursSaveState.Saved(hades.game.id), awaitItem().hoursSaveState)
            assertEquals(1L, fakeLibraryRepository.lastUpdateHoursGameId)
            assertEquals(120, fakeLibraryRepository.lastUpdateHours)
            viewModel.onHoursSaveHandled()
            assertEquals(HoursSaveState.Idle, awaitItem().hoursSaveState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onHoursUpdated emits Saving then Failed on error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeLibraryRepository.delayUpdateHours = gate
        fakeLibraryRepository.updateHoursResult = AppResult.Error(
            AppError.UnknownError(IllegalStateException("missing")),
        )
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(HoursSaveState.Idle, awaitItem().hoursSaveState)
            viewModel.onHoursUpdated(hades.game.id, 120)
            assertEquals(HoursSaveState.Saving(hades.game.id), awaitItem().hoursSaveState)
            gate.complete(Unit)
            val failedState = awaitItem()
            assertEquals(HoursSaveState.Failed(hades.game.id), failedState.hoursSaveState)
            assertEquals(
                R.string.error_library_update_failed,
                failedState.userMessageRes,
            )
            viewModel.onHoursSaveHandled()
            assertEquals(HoursSaveState.Idle, awaitItem().hoursSaveState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onHoursUpdated ignores a second request while saving`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeLibraryRepository.delayUpdateHours = gate
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades)
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(HoursSaveState.Idle, awaitItem().hoursSaveState)
            viewModel.onHoursUpdated(hades.game.id, 120)
            assertEquals(HoursSaveState.Saving(hades.game.id), awaitItem().hoursSaveState)

            viewModel.onHoursUpdated(hades.game.id, 999)
            assertEquals(1, fakeLibraryRepository.updateHoursCallCount)
            assertEquals(120, fakeLibraryRepository.lastUpdateHours)

            gate.complete(Unit)
            assertEquals(HoursSaveState.Saved(hades.game.id), awaitItem().hoursSaveState)
            assertEquals(1, fakeLibraryRepository.updateHoursCallCount)
            assertEquals(120, fakeLibraryRepository.lastUpdateHours)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updated sort reorders when updated timestamp changes`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, eldenRing)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Consume initial state with default ADDED_DESC sort
            awaitItem()

            viewModel.onSortOptionSelected(LibrarySortOption.UPDATED_DESC)
            assertEquals(listOf(hades, eldenRing), awaitItem().filteredGames)

            // Elden Ring is 2nd (updatedAt=400L). Update its status with newest updatedAt (900L).
            val updatedEldenRing = eldenRing.copy(
                entry = eldenRing.entry.copy(
                    status = LibraryStatus.PLAYING,
                    updatedAtEpochSeconds = 900L,
                ),
            )
            viewModel.onStatusSelected(eldenRing.game.id, LibraryStatus.PLAYING)
            testScheduler.advanceUntilIdle()
            fakeLibraryRepository.libraryGamesFlow.value = listOf(hades, updatedEldenRing)

            // Reorders immediately to put newest updated at top
            assertEquals(listOf(updatedEldenRing, hades), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default added sort preserves item position when status or hours change`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(eldenRing, hades)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Default sort is ADDED_DESC: eldenRing (addedAt=200L), hades (addedAt=100L)
            assertEquals(listOf(eldenRing, hades), awaitItem().filteredGames)

            // Hades is 2nd. Update status and hours which update updatedAtEpochSeconds to 900L.
            val updatedHades = hades.copy(
                entry = hades.entry.copy(
                    status = LibraryStatus.COMPLETED,
                    updatedAtEpochSeconds = 900L,
                    hoursPlayed = 120,
                ),
            )
            viewModel.onStatusSelected(hades.game.id, LibraryStatus.COMPLETED)
            testScheduler.advanceUntilIdle()
            fakeLibraryRepository.libraryGamesFlow.value = listOf(eldenRing, updatedHades)

            // Position is preserved: eldenRing remains 1st, hades remains 2nd
            assertEquals(listOf(eldenRing, updatedHades), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default added sort places newly added game at top`() = runTest {
        fakeLibraryRepository.libraryGamesFlow.value = listOf(eldenRing, hades)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(listOf(eldenRing, hades), awaitItem().filteredGames)

            // New game added with newer addedAt = 400L
            val newlyAddedGame = hollowKnight.copy(
                entry = hollowKnight.entry.copy(addedAtEpochSeconds = 400L),
            )
            fakeLibraryRepository.libraryGamesFlow.value = listOf(newlyAddedGame, eldenRing, hades)

            assertEquals(listOf(newlyAddedGame, eldenRing, hades), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rating sort reorders when user rating changes`() = runTest {
        val lowRated = hades.copy(entry = hades.entry.copy(userRating = 5))
        val highRated = eldenRing.copy(entry = eldenRing.entry.copy(userRating = 8))
        fakeLibraryRepository.libraryGamesFlow.value = listOf(highRated, lowRated)
        val viewModel = createViewModel()
        viewModel.onSortOptionSelected(LibrarySortOption.USER_RATING_DESC)

        viewModel.uiState.test {
            assertEquals(listOf(highRated, lowRated), awaitItem().filteredGames)

            // Hades rating updated to 10
            val topRatedHades = lowRated.copy(entry = lowRated.entry.copy(userRating = 10))
            fakeLibraryRepository.libraryGamesFlow.value = listOf(highRated, topRatedHades)

            assertEquals(listOf(topRatedHades, highRated), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hours sort reorders when hours change`() = runTest {
        val moreHours = hades.copy(entry = hades.entry.copy(hoursPlayed = 50))
        val lessHours = eldenRing.copy(entry = eldenRing.entry.copy(hoursPlayed = 10))
        fakeLibraryRepository.libraryGamesFlow.value = listOf(moreHours, lessHours)
        val viewModel = createViewModel()
        viewModel.onSortOptionSelected(LibrarySortOption.HOURS_PLAYED_DESC)

        viewModel.uiState.test {
            assertEquals(listOf(moreHours, lessHours), awaitItem().filteredGames)

            // Elden Ring hours updated to 100
            val grindEldenRing = lessHours.copy(entry = lessHours.entry.copy(hoursPlayed = 100))
            fakeLibraryRepository.libraryGamesFlow.value = listOf(moreHours, grindEldenRing)

            assertEquals(listOf(grindEldenRing, moreHours), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `details hydration preserves deterministic order when sort keys are tied`() = runTest {
        // Both games have same addedAtEpochSeconds (500L), ordered deterministically by ID (1L < 2L)
        val game1 = hades.copy(entry = hades.entry.copy(addedAtEpochSeconds = 500L))
        val game2 = eldenRing.copy(entry = eldenRing.entry.copy(addedAtEpochSeconds = 500L))
        fakeLibraryRepository.libraryGamesFlow.value = listOf(game1, game2)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(listOf(game1, game2), awaitItem().filteredGames)

            // Hydrating game2 with banner without changing sort key preserves deterministic order
            val hydratedGame2 = game2.copy(bannerUrl = "https://example.com/banner.jpg")
            fakeLibraryRepository.libraryGamesFlow.value = listOf(game1, hydratedGame2)

            assertEquals(listOf(game1, hydratedGame2), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `user rating sort places unrated games at bottom and sorts rated games descending`() = runTest {
        val rated10 = hades.copy(entry = hades.entry.copy(userRating = 10))
        val unrated = eldenRing.copy(entry = eldenRing.entry.copy(userRating = null))
        val rated8 = hollowKnight.copy(entry = hollowKnight.entry.copy(userRating = 8))

        fakeLibraryRepository.libraryGamesFlow.value = listOf(rated10, unrated, rated8)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Default sort is ADDED_DESC
            awaitItem()

            viewModel.onSortOptionSelected(LibrarySortOption.USER_RATING_DESC)
            assertEquals(listOf(rated10, rated8, unrated), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `same title games are both preserved and search matches both`() = runTest {
        val doom1993 = hades.copy(
            game = hades.game.copy(id = 900005L, name = "Doom"),
            entry = hades.entry.copy(gameId = 900005L, addedAtEpochSeconds = 200L),
        )
        val doom2016 = eldenRing.copy(
            game = eldenRing.game.copy(id = 900006L, name = "Doom"),
            entry = eldenRing.entry.copy(gameId = 900006L, addedAtEpochSeconds = 100L),
        )

        fakeLibraryRepository.libraryGamesFlow.value = listOf(doom1993, doom2016)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(listOf(doom1993, doom2016), awaitItem().filteredGames)

            viewModel.onSearchQueryChanged("Doom")
            assertEquals(listOf(doom1993, doom2016), awaitItem().filteredGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeLibraryRepository : LibraryRepository {
        val libraryGamesFlow = MutableStateFlow<List<LibraryGame>>(emptyList())

        override fun getLibraryGamesFlow(): Flow<List<LibraryGame>> = libraryGamesFlow
        override fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntry?> = flowOf(null)
        override suspend fun setGameStatus(
            gameId: Long,
            status: LibraryStatus
        ): AppResult<Unit> {
            lastStatusGameId = gameId
            lastStatus = status
            return setStatusResult
        }
        override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun addToWishlist(game: Game): AppResult<Unit> = AppResult.Success(Unit)
        var lastToggleGameId: Long? = null
        var toggleResult: AppResult<Unit> = AppResult.Success(Unit)
        var lastStatusGameId: Long? = null
        var lastStatus: LibraryStatus? = null
        var setStatusResult: AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun upsertUserEdits(
            gameId: Long,
            status: LibraryStatus,
            userRating: Int?,
            hoursPlayed: Int,
            userNotes: String?,
            isFavorite: Boolean,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun toggleFavorite(gameId: Long): AppResult<Unit> {
            lastToggleGameId = gameId
            return toggleResult
        }


        var updateHoursCallCount: Int = 0
        var lastUpdateHoursGameId: Long? = null
        var lastUpdateHours: Int? = null
        var updateHoursResult: AppResult<Unit> = AppResult.Success(Unit)
        var delayUpdateHours: CompletableDeferred<Unit>? = null

        override suspend fun updateHoursPlayed(gameId: Long, hoursPlayed: Int): AppResult<Unit> {
            updateHoursCallCount++
            lastUpdateHoursGameId = gameId
            lastUpdateHours = hoursPlayed
            delayUpdateHours?.await()
            return updateHoursResult
        }
        override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
    }
}
