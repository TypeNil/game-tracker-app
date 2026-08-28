package io.github.typenil.gametracker.feature.discover

import app.cash.turbine.test
import io.github.typenil.gametracker.R

import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.recommendations.RoomRecommendationSignalCollector
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryEntry

import io.github.typenil.gametracker.core.model.LibrarySnapshot

import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationCandidatePage
import io.github.typenil.gametracker.core.model.RecommendationSignal
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

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
        every { gameRepository.getPopularGamesFlow(any()) } returns MutableStateFlow(emptyList())
        every { libraryRepository.getLibraryGamesFlow() } returns libraryFlow
        coEvery { librarySeeder.seedIfEmpty() } returns Unit
        coEvery { signalCollector.collect() } returns emptyList()
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
            trendingFlow.value = trendingGames
            AppResult.Success(Unit)
        }
        coEvery { gameRepository.refreshPopular(any(), any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(RecommendationCandidatePage(items = emptyList(), nextOffset = null, endReached = true))
        coEvery { libraryRepository.addToWishlist(any()) } returns AppResult.Success(Unit)
        coEvery {
            libraryRepository.upsertUserEdits(any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(Unit)
        coEvery { libraryRepository.removeGameFromLibrary(any()) } returns AppResult.Success(Unit)
        coEvery { gameRepository.refreshGameDetails(any(), any()) } returns AppResult.Success(Unit)
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
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
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
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(
            RecommendationCandidatePage(
                items = listOf(
                    RecommendationCandidate(
                        gameId = 11L,
                        name = "Also Trending",
                        genres = listOf("RPG"),
                        rating = 90.0,
                        ratingCount = 200L,
                    )
                ),
                nextOffset = 30,
                endReached = false,
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
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(
            RecommendationCandidatePage(
                items = listOf(
                    RecommendationCandidate(
                        gameId = 11L,
                        name = "Candidate",
                        genres = listOf("RPG"),
                        rating = 90.0,
                        ratingCount = 200L,
                    )
                ),
                nextOffset = 30,
                endReached = false,
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
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
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
    fun `selectRail updates selectedRail in uiState and triggers load if empty`() = runTest {
        val wantedGames = listOf(Game(id = 201L, name = "Wanted Game"))
        every { gameRepository.getPopularGamesFlow(DiscoverRail.WANTED_NOW.type) } returns MutableStateFlow(wantedGames)
        coEvery { gameRepository.refreshPopular(DiscoverRail.WANTED_NOW.type, any(), any(), any()) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val initial = awaitItemUntil { !it.isLoading }
            assertEquals(DiscoverRail.POPULAR_NOW, initial.selectedRail)
            viewModel.selectRail(DiscoverRail.WANTED_NOW)
            val updated = awaitItemUntil {
                it.selectedRail == DiscoverRail.WANTED_NOW &&
                    it.rails.first { r -> r.rail == DiscoverRail.WANTED_NOW }.games.isNotEmpty()
            }
            assertEquals(DiscoverRail.WANTED_NOW, updated.selectedRail)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { gameRepository.refreshPopular(DiscoverRail.WANTED_NOW.type, 20, 0, false) }
    }

    @Test
    fun `loadMoreForYou appends paged candidates and filters duplicates`() = runTest {
        val c1 = RecommendationCandidate(101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L)
        val c2 = RecommendationCandidate(102L, "Rec 102", genres = listOf("RPG"), rating = 88.0, ratingCount = 150L)
        coEvery { signalCollector.collect() } returns listOf(
            RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))
        )
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val offset = args[6] as Int
            val items = if (offset == 0) listOf(c1) else listOf(c1, c2)
            AppResult.Success(RecommendationCandidatePage(items = items, nextOffset = offset + 20, endReached = false))
        }

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


    @Test
    fun addToWishlist_whenEntryExistsButUiMapEmpty_doesNotOverwriteStatus() = runTest {
        val game = Game(id = 11L, name = "Trending Game")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToWishlist(game)
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryRepository.addToWishlist(game) }
        coVerify(exactly = 0) { gameRepository.refreshGameDetails(any(), any()) }
        coVerify(exactly = 0) { libraryRepository.setGameStatus(any(), any()) }
    }

    @Test
    fun addToWishlist_onError_setsUserMessage() = runTest {
        coEvery { libraryRepository.addToWishlist(any()) } returns
            AppResult.Error(AppError.UnknownError(IllegalStateException("fail")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addToWishlist(Game(id = 11L, name = "Trending Game"))
        viewModel.uiState.test {
            val state = awaitItemUntil { it.userMessageRes != null }
            assertEquals(R.string.error_library_update_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onSaveLibraryEntry_delegatesToUpsertUserEdits() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSaveLibraryEntry(11L, LibraryStatus.PLAYING, 8, 12, "fun", true)
        advanceUntilIdle()
        coVerify {
            libraryRepository.upsertUserEdits(11L, LibraryStatus.PLAYING, 8, 12, "fun", true)
        }
        coVerify(exactly = 0) { libraryRepository.saveLibraryEntry(any()) }
    }

    @Test
    fun libraryFlow_setsReadySnapshot() = runTest {
        libraryFlow.value = listOf(libraryGame(11L, LibraryStatus.PLAYING, "Trending Game"))
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItemUntil {
                val snapshot = it.librarySnapshot
                snapshot is LibrarySnapshot.Ready && snapshot.entries.containsKey(11L)
            }
            val ready = state.librarySnapshot as LibrarySnapshot.Ready
            assertEquals(LibraryStatus.PLAYING, ready.entries[11L]?.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveFailure_keepsEditingGameId() = runTest {
        libraryFlow.value = listOf(libraryGame(11L, LibraryStatus.WISHLIST))
        coEvery {
            libraryRepository.upsertUserEdits(any(), any(), any(), any(), any(), any())
        } returns AppResult.Error(AppError.UnknownError(IllegalStateException("fail")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onLibraryCardAction(Game(id = 11L, name = "Trending Game"))
        advanceUntilIdle()
        viewModel.onSaveLibraryEntry(11L, LibraryStatus.PLAYING, 8, 12, "fun", true)
        viewModel.uiState.test {
            val state = awaitItemUntil { it.userMessageRes != null }
            assertEquals(11L, state.editingGameId)
            assertEquals(R.string.error_library_update_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeFailure_keepsEditingGameId() = runTest {
        libraryFlow.value = listOf(libraryGame(11L, LibraryStatus.WISHLIST))
        coEvery { libraryRepository.removeGameFromLibrary(any()) } returns
            AppResult.Error(AppError.UnknownError(IllegalStateException("fail")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onLibraryCardAction(Game(id = 11L, name = "Trending Game"))
        advanceUntilIdle()
        viewModel.onRemoveFromLibrary(11L)
        viewModel.uiState.test {
            val state = awaitItemUntil { it.userMessageRes != null }
            assertEquals(11L, state.editingGameId)
            assertEquals(R.string.error_library_remove_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun libraryFlowFailure_exposesFailedSnapshot() = runTest {
        every { libraryRepository.getLibraryGamesFlow() } returns flow {
            throw IllegalStateException("room down")
        }
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItemUntil { it.librarySnapshot is LibrarySnapshot.Failed }
            assertEquals(R.string.error_library_load_failed, state.userMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun libraryGame(
        id: Long,
        status: LibraryStatus,
        name: String = "Game $id",
    ) = LibraryGame(
        game = Game(id = id, name = name),
        entry = LibraryEntry(
            gameId = id,
            status = status,
            addedAtEpochSeconds = 1L,
            updatedAtEpochSeconds = 1L,
        ),
    )

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
