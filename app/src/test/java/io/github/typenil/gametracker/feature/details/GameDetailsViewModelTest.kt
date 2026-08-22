package io.github.typenil.gametracker.feature.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameCompany
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val fakeRepository = FakeDetailsRepository()

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
            gameRepository = fakeRepository,
            savedStateHandle = SavedStateHandle(mapOf(GameDetailsViewModel.KEY_GAME_ID to gameId))
        )
    }

    @Test
    fun `init triggers non-forced refresh and emits hydrated details`() = runTest {
        fakeRepository.detailsFlow.value = hydratedDetails
        fakeRepository.hydratedFlow.value = true

        val viewModel = createViewModel()

        assertEquals(listOf(1942L to false), fakeRepository.refreshCalls)

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
        fakeRepository.delayRefresh = gate
        fakeRepository.detailsFlow.value = catalogSkeleton

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
    fun `error without cache surfaces error state and retry forces network`() = runTest {
        fakeRepository.refreshResult = AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.game)
            assertEquals(AppError.NetworkError, state.error)
            assertFalse(state.isInitialLoading)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.retry()
        assertEquals(listOf(1942L to false, 1942L to true), fakeRepository.refreshCalls)
    }

    @Test
    fun `error with cached data keeps content and raises snackbar message`() = runTest {
        fakeRepository.detailsFlow.value = catalogSkeleton
        fakeRepository.refreshResult = AppResult.Error(AppError.NetworkError)

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
    fun `pull-to-refresh shows isRefreshing and forces network refresh`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val gate = CompletableDeferred<Unit>()
        fakeRepository.delayRefresh = gate

        viewModel.refresh()
        assertEquals(listOf(1942L to false, 1942L to true), fakeRepository.refreshCalls)

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
        fakeRepository.detailsFlow.value = hydratedDetails
        fakeRepository.hydratedFlow.value = true
        val viewModel = createViewModel()
        assertEquals(listOf(1942L to false), fakeRepository.refreshCalls)

        // Stale-cache eviction from another screen: hydrated -> skeleton
        fakeRepository.hydratedFlow.value = false
        fakeRepository.detailsFlow.value = catalogSkeleton

        assertEquals(
            "Eviction must trigger exactly one forced refetch",
            listOf(1942L to false, 1942L to true),
            fakeRepository.refreshCalls
        )
    }

    @Test
    fun `single-flight swallows concurrent refresh triggers`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeRepository.delayRefresh = gate

        val viewModel = createViewModel()

        // Pull-to-refresh while the initial refresh is still in flight
        viewModel.refresh()
        assertEquals(listOf(1942L to false), fakeRepository.refreshCalls)

        gate.complete(Unit)
    }

    @Test
    fun `state survives re-subscription after back-stack pop navigation`() = runTest {
        fakeRepository.detailsFlow.value = hydratedDetails
        fakeRepository.hydratedFlow.value = true
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val content = awaitItem()
            assertNotNull(content.game)
            cancelAndIgnoreRemainingEvents()
        }

        // Return from a stacked similar-game details screen: the Lazily pipeline
        // stayed alive, so the first emission is the retained content, not Loading.
        viewModel.uiState.test {
            val retained = awaitItem()
            assertNotNull("Content must be retained across re-subscription", retained.game)
            assertFalse(retained.isInitialLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun GameDetailsUiState.similarGamesShown(): Boolean = game?.similarGames?.isNotEmpty() == true

    private class FakeDetailsRepository : GameRepository {
        val detailsFlow = MutableStateFlow<GameDetails?>(null)
        val hydratedFlow = MutableStateFlow(false)
        val refreshCalls = mutableListOf<Pair<Long, Boolean>>()
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
        var delayRefresh: CompletableDeferred<Unit>? = null

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

        override fun getSearchResultsFlow(query: String): Flow<List<Game>> = flowOf(emptyList())

        override fun getPagedSearchResults(query: String, pageSize: Int) =
            flowOf(androidx.paging.PagingData.empty<Game>())

        override suspend fun searchGames(query: String, limit: Int, offset: Int): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun clearStaleCache(staleThresholdSeconds: Long): Int = 0
    }
}
