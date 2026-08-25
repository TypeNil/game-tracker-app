package io.github.typenil.gametracker.feature.discover

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.recommendations.RoomRecommendationSignalCollector
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationSignal
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gameRepository: GameRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk()
    private val librarySeeder: LibrarySeeder = mockk()
    private val signalCollector: RoomRecommendationSignalCollector = mockk()

    private val trendingFlow = MutableStateFlow<List<Game>>(emptyList())
    private val libraryFlow = MutableStateFlow<List<LibraryGame>>(emptyList())

    private val trendingGames = listOf(Game(id = 11L, name = "Trending Game"))

    @Before
    fun setUp() {
        every { gameRepository.getTrendingGamesFlow() } returns trendingFlow
        every { libraryRepository.getLibraryGamesFlow() } returns libraryFlow
        coEvery { librarySeeder.seedIfEmpty() } returns Unit
        coEvery { signalCollector.collect() } returns emptyList()
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
            trendingFlow.value = trendingGames
            AppResult.Success(Unit)
        }
        coEvery {
            gameRepository.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(emptyList())
    }

    @Test
    fun init_hydratesTrendingSilently_andDoesNotFlipRefreshing() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItemUntil { it.trending.isNotEmpty() && !it.isLoading }
            assertEquals(listOf(11L), state.trending.map { it.id })
            assertTrue(state.recommendations.isEmpty())
            assertFalse(state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { librarySeeder.seedIfEmpty() }
        coVerify(exactly = 1) { gameRepository.refreshTrendingGames(any(), any(), any()) }
    }

    @Test
    fun init_whenTrendingFailsAndEmpty_emitsError() = runTest {
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } returns AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItemUntil { it.error != null && !it.isLoading }
            assertEquals(AppError.NetworkError, state.error)
            assertFalse(state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recsError_keepsTrendingVisible() = runTest {
        coEvery { signalCollector.collect() } returns listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        )
        coEvery {
            gameRepository.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        } returns AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItemUntil { it.trending.isNotEmpty() && !it.isLoading }
            assertTrue(state.recommendations.isEmpty())
            assertEquals(listOf(11L), state.trending.map { it.id })
            assertEquals(null, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refresh_setsRefreshingOnlyForPullToRefresh() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
            gate.await()
            AppResult.Success(Unit)
        }

        viewModel.uiState.test {
            viewModel.refresh()
            val refreshing = awaitItemUntil { it.isRefreshing }
            assertTrue(refreshing.isRefreshing)
            gate.complete(Unit)
            val done = awaitItemUntil { !it.isRefreshing }
            assertFalse(done.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun positiveLibrary_buildsForYouAndDropsRecFromTrending() = runTest {
        coEvery { signalCollector.collect() } returns listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        )
        coEvery {
            gameRepository.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(
            listOf(
                RecommendationCandidate(
                    gameId = 11L,
                    name = "Also Trending",
                    genres = listOf("RPG"),
                    rating = 90.0,
                    ratingCount = 200L,
                )
            )
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }
            assertEquals(listOf(11L), state.recommendations.map { it.game.id })
            assertTrue(state.trending.none { it.id == 11L })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMoreTrending_appendsWithoutRefreshing() = runTest {
        val pageOne = (1L..20L).map { Game(id = it, name = "T$it") }
        val pageTwo = (21L..40L).map { Game(id = it, name = "T$it") }
        trendingFlow.value = pageOne
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
            val offset = args[1] as Int
            val append = args[2] as Boolean
            trendingFlow.value = if (append && offset == 20) pageOne + pageTwo else pageOne
            AppResult.Success(Unit)
        }


        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.trending.size == 20 && !it.isLoading }
            viewModel.loadMoreTrending()
            val appended = awaitItemUntil { it.trending.size == 40 }
            assertFalse(appended.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { gameRepository.refreshTrendingGames(20, 20, true) }

    }
    @Test
    fun `ui state exposes rail sections without changing pull refresh flag`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItemUntil { it.rails.size == DiscoverRail.entries.size }
            assertFalse(state.isRefreshing)
            assertEquals(DiscoverRail.entries.toList(), state.rails.map { it.rail })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library flow emission without entry change does not refetch candidates`() = runTest {
        val entry = io.github.typenil.gametracker.core.model.LibraryEntry(
            gameId = 1942L,
            status = LibraryStatus.COMPLETED,
            isFavorite = true,
            addedAtEpochSeconds = 1000L,
            updatedAtEpochSeconds = 1000L,
        )
        val game = Game(id = 1942L, name = "Game 1942")
        libraryFlow.value = listOf(LibraryGame(game = game, entry = entry))
        coEvery { signalCollector.collect() } returns listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        )
        coEvery {
            gameRepository.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(
            listOf(
                RecommendationCandidate(
                    gameId = 11L,
                    name = "Candidate",
                    genres = listOf("RPG"),
                    rating = 90.0,
                    ratingCount = 200L,
                )
            )
        )

        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }

            // Simulate Room re-emitting after games table upsert (same entry, game name updated)
            libraryFlow.value = listOf(LibraryGame(game = game.copy(name = "Updated Game 1942"), entry = entry))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // getRecommendationCandidates should only have been called ONCE (on init), not on duplicate library emissions
        coVerify(exactly = 1) {
            gameRepository.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `selectTab updates selectedTab in uiState`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val initial = awaitItemUntil { !it.isLoading }
            assertEquals(DiscoverTab.FOR_YOU, initial.selectedTab)
            viewModel.selectTab(DiscoverTab.CHARTS)
            val updated = awaitItemUntil { it.selectedTab == DiscoverTab.CHARTS }
            assertEquals(DiscoverTab.CHARTS, updated.selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadMoreForYou appends paged candidates and filters duplicates`() = runTest {
        val initialCandidate = RecommendationCandidate(
            gameId = 101L,
            name = "Rec 101",
            genres = listOf("RPG"),
            rating = 90.0,
            ratingCount = 200L,
        )
        val pageCandidate = RecommendationCandidate(
            gameId = 102L,
            name = "Rec 102",
            genres = listOf("RPG"),
            rating = 88.0,
            ratingCount = 150L,
        )
        coEvery { signalCollector.collect() } returns listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        )
        coEvery {
            gameRepository.getRecommendationCandidates(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(listOf(initialCandidate))

        coEvery {
            gameRepository.getRecommendationCandidatesPage(
                genres = any(),
                themes = any(),
                platforms = any(),
                exclude = any(),
                similarTo = any(),
                limit = any(),
                offset = any(),
                sort = any(),
            )
        } returns AppResult.Success(
            io.github.typenil.gametracker.core.model.RecommendationCandidatePage(
                items = listOf(initialCandidate, pageCandidate), // duplicate 101 + new 102
                nextOffset = 20,
                endReached = false,
            )
        )

        val viewModel = createViewModel()
        viewModel.uiState.test {
            val initial = awaitItemUntil { it.recommendations.size == 1 && !it.isLoading }
            assertEquals(listOf(101L), initial.recommendations.map { it.game.id })

            viewModel.loadMoreForYou()
            val appended = awaitItemUntil { it.recommendations.size == 2 }
            assertEquals(listOf(101L, 102L), appended.recommendations.map { it.game.id })
            assertFalse(appended.forYouEndReached)
            cancelAndIgnoreRemainingEvents()
        }
    }


    private fun createViewModel(): DiscoverViewModel {
        return DiscoverViewModel(
            gameRepository = gameRepository,
            libraryRepository = libraryRepository,
            librarySeeder = librarySeeder,
            signalCollector = signalCollector,
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<DiscoverUiState>.awaitItemUntil(
        predicate: (DiscoverUiState) -> Boolean,
    ): DiscoverUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
