package io.github.typenil.gametracker.feature.details

import app.cash.turbine.test
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import io.mockk.every
import io.mockk.mockk
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.data.repository.toDetailsPreview
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameCompany
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.PageContinuation
import io.github.typenil.gametracker.core.model.RecommendationCandidatePage
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class GameDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val fakeGameRepository = FakeDetailsRepository()
    private val fakeLibraryRepository = FakeLibraryRepository()

    private val hydratedDetails = GameDetails(
        id = 1942L,
        name = "The Witcher 3: Wild Hunt",
        coverUrl = "https://example.com/cover.jpg",
        rating = 93.7,
        totalRating = 92.7,
        totalRatingCount = 5451L,
        releaseDateEpochSeconds = 1431993600L,
        summary = "RPG masterpiece",
        genres = listOf("RPG"),
        themes = listOf("Fantasy"),
        gameModes = listOf("Single player"),
        platforms = listOf("PC"),
        companies = listOf(GameCompany(name = "CD Projekt RED", isDeveloper = true)),
        screenshots = listOf("https://example.com/shot.jpg"),
        videos = listOf(GameVideo(videoId = "abc123", name = "Trailer")),
        similarGames = listOf(GameSummary(id = 25076L, name = "Red Dead Redemption 2", totalRating = 93.6)),
        url = "https://www.igdb.com/games/the-witcher-3-wild-hunt"
    )

    private val catalogSkeleton = GameDetails(
        id = 1942L,
        name = "The Witcher 3: Wild Hunt",
        rating = 93.7,
        releaseDateEpochSeconds = 1431993600L,
        genres = listOf("RPG"),
        platforms = listOf("PC")
    )

    private fun createViewModel(gameId: Long = 1942L): GameDetailsViewModel {
        return GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = gameId
        ).apply { onScreenStarted() }
    }

    @Test
    fun `init triggers non-forced refresh and emits hydrated details`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true

        val viewModel = createViewModel()

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("The Witcher 3: Wild Hunt", state.game?.name)
            assertTrue(state.isHydrated)
            assertTrue(state.similarGamesShown())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init over catalog skeleton does not set isRefreshing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeGameRepository.delayRefresh = gate
        fakeGameRepository.detailsFlow.value = catalogSkeleton

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.game)
            assertFalse("Init with skeleton must not show PTR spinner", state.isRefreshing)
            assertFalse(state.isInitialLoading)

            gate.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `warmPreview_isPresentBeforeUpstreamRuns`() = runTest(
        StandardTestDispatcher(),
    ) {
        fakeGameRepository.initialPreview = catalogSkeleton
        fakeGameRepository.detailsFlow.value = null

        val viewModel = createViewModel()

        assertEquals(catalogSkeleton, viewModel.uiState.value.game)
        assertFalse(viewModel.uiState.value.isInitialLoading)
    }

    @Test
    fun `warmPreview_retainedWhileFirstRoomEmissionIsNullAndLoading`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeGameRepository.delayRefresh = gate
        fakeGameRepository.initialPreview = catalogSkeleton
        fakeGameRepository.detailsFlow.value = null
        fakeGameRepository.hydratedFlow.value = false

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(catalogSkeleton, state.game)
            assertTrue(state.isLoading)

            fakeGameRepository.detailsFlow.value = hydratedDetails
            fakeGameRepository.hydratedFlow.value = true
            gate.complete(Unit)
            var updated = awaitItem()
            while (updated.isLoading) {
                updated = awaitItem()
            }
            assertEquals(hydratedDetails, updated.game)
            assertFalse(updated.isLoading)
            assertTrue(updated.isHydrated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `warmPreview_clearedOnRefreshFailureWhenNoPersistedData`() = runTest {
        fakeGameRepository.initialPreview = catalogSkeleton
        fakeGameRepository.detailsFlow.value = null
        fakeGameRepository.refreshResult = AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull("Failed refresh without Room data clears preview to reveal error", state.game)
            assertEquals(AppError.NetworkError, state.error)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `error without cache surfaces error state and retry forces network`() = runTest {
        fakeGameRepository.refreshResult = AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.game)
            assertEquals(AppError.NetworkError, state.error)
            assertFalse(state.isInitialLoading)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.retry()
        assertEquals(listOf(1942L to false, 1942L to true), fakeGameRepository.refreshCalls)
    }

    @Test
    fun `error with cached data keeps content and raises snackbar message`() = runTest {
        fakeGameRepository.detailsFlow.value = catalogSkeleton
        fakeGameRepository.refreshResult = AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull("Cached content must stay visible", state.game)
            assertNull("Error must not replace content", state.error)
            assertEquals(R.string.error_refresh_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network reconnect triggers forced refresh when game is not hydrated`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = catalogSkeleton
        fakeGameRepository.hydratedFlow.value = false

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        networkStatus.value = NetworkStatus.Available

        assertEquals(
            listOf(1942L to false, 1942L to true),
            fakeGameRepository.refreshCalls
        )
    }

    @Test
    fun `network reconnect does not trigger refresh when already hydrated without error`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        networkStatus.value = NetworkStatus.Available

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)
    }

    @Test
    fun `reconnectDuringFailedInFlightRefresh_retriesAfterRefreshCompletes`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        val gate = CompletableDeferred<Unit>()
        fakeGameRepository.delayRefresh = gate
        fakeGameRepository.refreshResult = AppResult.Error(AppError.NetworkError)
        fakeGameRepository.detailsFlow.value = catalogSkeleton
        fakeGameRepository.hydratedFlow.value = false

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        // Network recovers while initial refresh is still in-flight
        networkStatus.value = NetworkStatus.Available

        // Complete initial refresh with failure
        gate.complete(Unit)

        // Observe network reconnect joins the in-flight job and retries after failure
        assertEquals(
            listOf(1942L to false, 1942L to true),
            fakeGameRepository.refreshCalls
        )
    }

    @Test
    fun `dismissedRefreshError_stillRecoversOnReconnect`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        fakeGameRepository.refreshResult = AppResult.Error(AppError.NetworkError)

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        // Consumable UI message is dismissed by the user
        viewModel.onUserMessageShown()

        // Next refresh will succeed
        fakeGameRepository.refreshResult = AppResult.Success(Unit)

        // Reconnect happens
        networkStatus.value = NetworkStatus.Available

        // Durable lastDetailsRefreshFailed still triggers recovery even after message was dismissed
        assertEquals(
            listOf(1942L to false, 1942L to true),
            fakeGameRepository.refreshCalls
        )
    }

    @Test
    fun `libraryError_doesNotTriggerDetailsRefreshOnReconnect`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        fakeLibraryRepository.saveResult = AppResult.Error(AppError.NetworkError)

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        // Trigger a library mutation error
        viewModel.onSaveLibraryEntry(
            status = LibraryStatus.PLAYING,
            userRating = 9,
            hoursPlayed = 10,
            userNotes = "Notes",
            isFavorite = false
        )

        // Network recovers
        networkStatus.value = NetworkStatus.Available

        // Details refresh must not be triggered by unrelated library error
        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)
    }

    @Test
    fun `onlyStartedStackEntryRefreshesOnReconnect`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = catalogSkeleton
        fakeGameRepository.hydratedFlow.value = false

        // Covered screen in back stack: started then stopped
        val coveredViewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 100L,
            networkMonitor = networkMonitor,
        )
        coveredViewModel.onScreenStarted()
        coveredViewModel.onScreenStopped()

        // Active top screen: started
        val activeViewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 200L,
            networkMonitor = networkMonitor,
        )
        activeViewModel.onScreenStarted()

        assertEquals(
            listOf(100L to false, 200L to false),
            fakeGameRepository.refreshCalls
        )

        // Network recovers
        networkStatus.value = NetworkStatus.Available
        // ONLY the active screen refreshes, the covered screen does NOT refresh
        assertEquals(
            listOf(100L to false, 200L to false, 200L to true),
            fakeGameRepository.refreshCalls
        )

        // When covered screen is popped back to top and restarted:
        coveredViewModel.onScreenStarted()
        assertEquals(
            listOf(100L to false, 200L to false, 200L to true, 100L to true),
            fakeGameRepository.refreshCalls
        )
    }

    @Test
    fun `coveredEntry_recoversWhenRestartedAfterOfflineReconnect`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = catalogSkeleton
        fakeGameRepository.hydratedFlow.value = false

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 100L,
            networkMonitor = networkMonitor,
        )

        // 1. Entry starts while offline
        viewModel.onScreenStarted()
        assertEquals(listOf(100L to false), fakeGameRepository.refreshCalls)

        // 2. Entry becomes covered (stopped)
        viewModel.onScreenStopped()

        // 3. Network recovers while entry is covered in the back stack
        networkStatus.value = NetworkStatus.Available

        // Reconnect observer was inactive, so no hidden refresh occurred while covered
        assertEquals(listOf(100L to false), fakeGameRepository.refreshCalls)

        // 4. User navigates back: entry starts again
        viewModel.onScreenStarted()

        // Entry discovers network is Available and was unhydrated -> forced recovery refresh!
        assertEquals(
            listOf(100L to false, 100L to true),
            fakeGameRepository.refreshCalls
        )
    }

    @Test
    fun `hydratedCoveredEntry_doesNotReloadImagesWhenRestartedAfterOfflineReconnect`() = runTest {
        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true

        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 100L,
            networkMonitor = networkMonitor,
        )

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(0L, initial.imageReloadToken)

            // 1. Entry starts, then becomes covered
            viewModel.onScreenStarted()
            viewModel.onScreenStopped()

            // 2. Network recovers while covered
            networkStatus.value = NetworkStatus.Available

            // 3. User navigates back: entry starts again
            viewModel.onScreenStarted()

            // Already hydrated and no refresh failure: must NOT trigger unnecessary forced refresh
            assertEquals(listOf(100L to false), fakeGameRepository.refreshCalls)
            // Nor should it bump image reload token on simple screen restart
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `initialNonForcedRefresh_doesNotIncrementImageReloadToken`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(0L, initial.imageReloadToken)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hydratedReconnect_incrementsImageReloadTokenExactlyOnce`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true

        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(0L, initial.imageReloadToken)

            networkStatus.value = NetworkStatus.Available

            val updated = awaitItem()
            assertEquals(1L, updated.imageReloadToken)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unhydratedReconnectWithForcedRefresh_incrementsImageReloadTokenExactlyOnce`() = runTest {
        fakeGameRepository.detailsFlow.value = catalogSkeleton
        fakeGameRepository.hydratedFlow.value = false

        val networkStatus = MutableStateFlow(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        val viewModel = GameDetailsViewModel(
            gameRepository = fakeGameRepository,
            libraryRepository = fakeLibraryRepository,
            gameId = 1942L,
            networkMonitor = networkMonitor,
        )
        viewModel.onScreenStarted()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(0L, initial.imageReloadToken)

            networkStatus.value = NetworkStatus.Available

            val updated = awaitItem()
            assertEquals(1L, updated.imageReloadToken)
            assertEquals(listOf(1942L to false, 1942L to true), fakeGameRepository.refreshCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `pull-to-refresh shows isRefreshing and forces network refresh`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val gate = CompletableDeferred<Unit>()
        fakeGameRepository.delayRefresh = gate

        viewModel.refresh()
        assertEquals(listOf(1942L to false, 1942L to true), fakeGameRepository.refreshCalls)

        viewModel.uiState.test {
            val inFlightState = awaitItem()
            assertTrue("User refresh must activate PTR spinner", inFlightState.isRefreshing)

            gate.complete(Unit)
            val settledState = awaitItem()
            assertFalse("PTR spinner must dismiss on completion", settledState.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `eviction guard refetches once when hydrated row disappears`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        val viewModel = createViewModel()
        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        // Stale-cache eviction from another screen: hydrated -> skeleton
        fakeGameRepository.hydratedFlow.value = false
        fakeGameRepository.detailsFlow.value = catalogSkeleton

        assertEquals(
            "Eviction must trigger exactly one forced refetch",
            listOf(1942L to false, 1942L to true),
            fakeGameRepository.refreshCalls
        )
    }

    @Test
    fun `single-flight swallows concurrent refresh triggers`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeGameRepository.delayRefresh = gate

        val viewModel = createViewModel()

        // Pull-to-refresh while the initial refresh is still in flight
        viewModel.refresh()
        assertEquals(listOf(1942L to false), fakeGameRepository.refreshCalls)

        gate.complete(Unit)
    }

    @Test
    fun `state is retained when resubscribed within sharing timeout`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val content = awaitItem()
            assertNotNull(content.game)
            cancelAndIgnoreRemainingEvents()
        }

        // Re-subscription occurs within WhileSubscribed(5_000), so StateFlow returns
        // the retained content without exposing a transient Loading state.
        viewModel.uiState.test {
            val retained = awaitItem()
            assertNotNull("Content must be retained across re-subscription", retained.game)
            assertFalse(retained.isInitialLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `libraryEntry is observed reactively and editor state toggles`() = runTest {
        val initialEntry = LibraryEntry(
            gameId = 1942L,
            status = LibraryStatus.PLAYING,
            userRating = 10,
            userNotes = "Peak gaming",
            isFavorite = true,
            addedAtEpochSeconds = 100L,
            updatedAtEpochSeconds = 100L,
            hoursPlayed = 50
        )
        fakeLibraryRepository.entryFlow.value = initialEntry
        fakeGameRepository.detailsFlow.value = hydratedDetails

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(initialEntry, state.libraryEntry)
            assertFalse(state.isEditingLibrary)

            viewModel.onEditLibraryClicked()
            val editingState = awaitItem()
            assertTrue(editingState.isEditingLibrary)

            viewModel.onDismissEditLibrary()
            val dismissedState = awaitItem()
            assertFalse(dismissedState.isEditingLibrary)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving library entry calls repository and closes sheet`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        val viewModel = createViewModel()

        viewModel.onEditLibraryClicked()
        viewModel.onSaveLibraryEntry(
            status = LibraryStatus.COMPLETED,
            userRating = 9,
            hoursPlayed = 120,
            userNotes = "Finished main story",
            isFavorite = true
        )

        val saved = fakeLibraryRepository.savedEntries.lastOrNull()
        assertNotNull(saved)
        assertEquals(1942L, saved?.gameId)
        assertEquals(LibraryStatus.COMPLETED, saved?.status)
        assertEquals(9, saved?.userRating)
        assertEquals(120, saved?.hoursPlayed)
        assertEquals("Finished main story", saved?.userNotes)
        assertTrue(saved?.isFavorite == true)

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse("Sheet should close after save", state.isEditingLibrary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing from library calls repository and closes sheet`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        val viewModel = createViewModel()

        viewModel.onEditLibraryClicked()
        viewModel.onRemoveFromLibrary()

        assertEquals(listOf(1942L), fakeLibraryRepository.deletedGameIds)

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse("Sheet should close after remove", state.isEditingLibrary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving library entry failure keeps sheet open and surfaces error message`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeLibraryRepository.saveResult = AppResult.Error(AppError.UnknownError(RuntimeException("DB fail")))
        val viewModel = createViewModel()

        viewModel.onEditLibraryClicked()
        viewModel.onSaveLibraryEntry(
            status = LibraryStatus.PLAYING,
            userRating = 8,
            hoursPlayed = 10,
            userNotes = null,
            isFavorite = false
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Sheet must stay open on save failure", state.isEditingLibrary)
            assertEquals(R.string.error_library_update_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing from library failure keeps sheet open and surfaces error message`() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeLibraryRepository.removeResult = AppResult.Error(AppError.UnknownError(RuntimeException("DB fail")))
        val viewModel = createViewModel()

        viewModel.onEditLibraryClicked()
        viewModel.onRemoveFromLibrary()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Sheet must stay open on remove failure", state.isEditingLibrary)
            assertEquals(R.string.error_library_remove_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveInFlight_blocksSecondSaveAndRemove() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        val gate = CompletableDeferred<Unit>()
        fakeLibraryRepository.saveGate = gate
        val viewModel = createViewModel()

        viewModel.onEditLibraryClicked()
        viewModel.onSaveLibraryEntry(
            status = LibraryStatus.COMPLETED,
            userRating = 9,
            hoursPlayed = 10,
            userNotes = "first",
            isFavorite = true,
        )
        viewModel.onSaveLibraryEntry(
            status = LibraryStatus.PLAYING,
            userRating = 1,
            hoursPlayed = 1,
            userNotes = "second",
            isFavorite = false,
        )
        viewModel.onRemoveFromLibrary()

        assertTrue(fakeLibraryRepository.savedEntries.isEmpty())
        assertTrue(fakeLibraryRepository.deletedGameIds.isEmpty())

        gate.complete(Unit)

        assertEquals(1, fakeLibraryRepository.savedEntries.size)
        assertEquals("first", fakeLibraryRepository.savedEntries.single().userNotes)
        assertTrue(fakeLibraryRepository.deletedGameIds.isEmpty())
        assertFalse(viewModel.uiState.value.isEditingLibrary)
    }


    @Test
    fun libraryObservationFailure_exposesErrorWithoutThrowing() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        fakeLibraryRepository.entryResultFlow = flowOf(AppResult.Error(AppError.UnknownError(null)))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.libraryLoadError is AppError.UnknownError)
            assertNull(state.libraryEntry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEditLibraryClicked_whileLoadError_doesNotOpenEditor() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        fakeLibraryRepository.entryResultFlow = flowOf(AppResult.Error(AppError.UnknownError(null)))
        val viewModel = createViewModel()
        viewModel.uiState.test { awaitItem() }

        viewModel.onEditLibraryClicked()

        assertFalse(viewModel.uiState.value.isEditingLibrary)
        assertTrue(fakeLibraryRepository.savedEntries.isEmpty())
    }

    @Test
    fun saveWhileLoadError_doesNotWriteEvenIfFreshObservationSucceeds() = runTest {
        fakeGameRepository.detailsFlow.value = hydratedDetails
        fakeGameRepository.hydratedFlow.value = true
        val existing = LibraryEntry(
            gameId = 1942L,
            status = LibraryStatus.PLAYING,
            userRating = 10,
            userNotes = "keep these notes",
            isFavorite = true,
            hoursPlayed = 40,
            addedAtEpochSeconds = 100L,
            updatedAtEpochSeconds = 200L,
        )
        var collections = 0
        fakeLibraryRepository.entryResultFlow = flow {
            collections += 1
            if (collections == 1) {
                emit(AppResult.Error(AppError.UnknownError(null)))
                awaitCancellation()
            } else {
                emit(AppResult.Success(existing))
            }
        }
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.libraryLoadError is AppError.UnknownError)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEditLibraryClicked()
        viewModel.onSaveLibraryEntry(
            status = LibraryStatus.WISHLIST,
            userRating = null,
            hoursPlayed = 0,
            userNotes = null,
            isFavorite = false,
        )

        assertTrue(fakeLibraryRepository.savedEntries.isEmpty())
        assertFalse(viewModel.uiState.value.isEditingLibrary)
        assertEquals(R.string.error_library_load_failed, viewModel.uiState.value.userMessageRes)
    }



    private fun GameDetailsUiState.similarGamesShown(): Boolean = game?.similarGames?.isNotEmpty() == true

    @Suppress("TooManyFunctions")
    private class FakeDetailsRepository : GameRepository {
        val detailsFlow = MutableStateFlow<GameDetails?>(null)
        val hydratedFlow = MutableStateFlow(false)
        val refreshCalls = mutableListOf<Pair<Long, Boolean>>()
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
        var delayRefresh: CompletableDeferred<Unit>? = null

        var initialPreview: GameDetails? = null
        override fun getInitialGameDetails(id: Long): GameDetails? = initialPreview
        override fun recordPreview(details: GameDetails) {
            initialPreview = details
        }
        override fun recordPreview(game: Game) {
            initialPreview = game.toDetailsPreview()
        }

        override fun getGameDetailsFlow(id: Long): Flow<GameDetails?> = detailsFlow

        override fun isGameDetailsHydratedFlow(id: Long): Flow<Boolean> = hydratedFlow

        override suspend fun refreshGameDetails(id: Long, force: Boolean): AppResult<Unit> {
            refreshCalls += id to force
            delayRefresh?.await()
            return refreshResult
        }

        override fun getTopRatedGamesFlow(): Flow<List<Game>> = flowOf(emptyList())

        override fun getPagedTopRatedGames(pageSize: Int) = flowOf(androidx.paging.PagingData.empty<Game>())

        override suspend fun refreshTopRatedGames(limit: Int, offset: Int): AppResult<Unit> =
            AppResult.Success(Unit)

        override fun getTrendingGamesFlow(): Flow<List<Game>> = flowOf(emptyList())

        override suspend fun refreshTrendingGames(limit: Int, offset: Int, append: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun refreshPopular(
            type: String,
            limit: Int,
            offset: Int,
            append: Boolean,
        ): AppResult<PageContinuation> =
            AppResult.Success(PageContinuation(nextOffset = null, endReached = true))

        override fun getPopularGamesFlow(type: String): Flow<List<Game>> = flowOf(emptyList())

        override suspend fun getRecommendationCandidatesPage(
            genres: List<String>,
            themes: List<String>,
            platforms: List<String>,
            exclude: Set<Long>,
            similarTo: List<Long>,
            limit: Int,
            offset: Int,
            sort: String,
        ): AppResult<RecommendationCandidatePage> =
            AppResult.Success(RecommendationCandidatePage(emptyList(), null, true))


        override suspend fun getRecommendationCandidates(
            genres: List<String>,
            themes: List<String>,
            platforms: List<String>,
            exclude: Set<Long>,
            similarTo: List<Long>,
            limit: Int,
        ) = AppResult.Success(emptyList<io.github.typenil.gametracker.core.model.RecommendationCandidate>())


        override fun getSearchResultsFlow(
            query: io.github.typenil.gametracker.core.model.GameSearchQuery,
        ): Flow<List<Game>> = flowOf(emptyList())

        override fun getPagedSearchResults(
            query: io.github.typenil.gametracker.core.model.GameSearchQuery,
            pageSize: Int,
        ) = flowOf(androidx.paging.PagingData.empty<Game>())

        override suspend fun recordSearchHistory(rawQuery: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun searchGames(
            query: io.github.typenil.gametracker.core.model.GameSearchQuery,
            limit: Int,
            force: Boolean,
        ): AppResult<Unit> = AppResult.Success(Unit)
        override fun getRecentSearchQueriesFlow(limit: Int): Flow<List<String>> = flowOf(emptyList())

        override suspend fun deleteSearchQuery(query: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun clearSearchHistory(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int = 0
    }

    private class FakeLibraryRepository : LibraryRepository {
        val entryFlow = MutableStateFlow<LibraryEntry?>(null)
        var entryResultFlow: Flow<AppResult<LibraryEntry?>>? = null
        val savedEntries = mutableListOf<LibraryEntry>()
        val deletedGameIds = mutableListOf<Long>()
        var saveResult: AppResult<Unit> = AppResult.Success(Unit)
        var removeResult: AppResult<Unit> = AppResult.Success(Unit)
        var saveGate: CompletableDeferred<Unit>? = null

        override fun getLibraryGamesFlow(): Flow<AppResult<List<LibraryGame>>> =
            flowOf(AppResult.Success(emptyList()))

        override fun getLibraryEntryFlow(gameId: Long): Flow<AppResult<LibraryEntry?>> =
            entryResultFlow ?: entryFlow.map { AppResult.Success(it) }



        override suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit> {
            return AppResult.Success(Unit)
        }

        override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> {
            saveGate?.await()
            savedEntries += entry
            if (saveResult is AppResult.Success) {
                entryFlow.value = entry
            }
            return saveResult
        }


        override suspend fun addToWishlist(game: Game): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun upsertUserEdits(
            gameId: Long,
            status: LibraryStatus,
            userRating: Int?,
            hoursPlayed: Int,
            userNotes: String?,
            isFavorite: Boolean,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun toggleFavorite(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun updateHoursPlayed(
            gameId: Long,
            hoursPlayed: Int
        ): AppResult<Unit> = AppResult.Success(Unit)


        override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> {
            deletedGameIds += gameId
            if (removeResult is AppResult.Success) {
                entryFlow.value = null
            }
            return removeResult
        }
    }
}
