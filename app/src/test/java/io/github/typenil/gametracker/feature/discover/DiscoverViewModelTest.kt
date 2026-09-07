package io.github.typenil.gametracker.feature.discover

import app.cash.turbine.test
import io.github.typenil.gametracker.R

import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.repository.GameRepository

import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.PageContinuation

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
import kotlinx.coroutines.flow.map


import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class DiscoverViewModelTest {


    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gameRepository: GameRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk()
    private val librarySeeder: LibrarySeeder = mockk()


    private val trendingFlow = MutableStateFlow<List<Game>>(emptyList())
    private val libraryFlow = MutableStateFlow<List<LibraryGame>>(emptyList())

    private val trendingGames = listOf(Game(id = 11L, name = "Trending Game"))

    @Before
    fun setUp() {
        every { gameRepository.getTrendingGamesFlow() } returns trendingFlow
        every { gameRepository.getPopularGamesFlow(any()) } returns MutableStateFlow(emptyList())
        every { libraryRepository.getLibraryGamesFlow() } returns libraryFlow.map { AppResult.Success(it) }


        coEvery { librarySeeder.seedIfEmpty() } returns Unit
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(emptyList())
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
            trendingFlow.value = trendingGames
            AppResult.Success(PageContinuation(nextOffset = null, endReached = true))
        }
        coEvery { gameRepository.refreshPopular(any(), any(), any(), any()) } returns AppResult.Success(
            PageContinuation(nextOffset = 20, endReached = false),
        )

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
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        ))
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
            AppResult.Success(PageContinuation(nextOffset = null, endReached = true))
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
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        ))
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
            AppResult.Success(
                PageContinuation(
                    nextOffset = if (append) 40 else 20,
                    endReached = false,
                ),
            )
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
    fun loadMoreTrending_usesRawContinuationNotLocalCountAfterOverlap() = runTest {
        val pageOne = (1L..3L).map { Game(id = it, name = "T$it") }
        val denseAfterOverlap = (1L..5L).map { Game(id = it, name = "T$it") }
        trendingFlow.value = pageOne
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
            val offset = args[1] as Int
            val append = args[2] as Boolean
            when {
                !append -> {
                    trendingFlow.value = pageOne
                    AppResult.Success(PageContinuation(nextOffset = 3, endReached = false))
                }
                append && offset == 3 -> {
                    trendingFlow.value = denseAfterOverlap
                    AppResult.Success(PageContinuation(nextOffset = 6, endReached = false))
                }
                append && offset == 6 -> {
                    AppResult.Success(PageContinuation(nextOffset = 9, endReached = false))
                }
                else -> error("unexpected trending request offset=$offset append=$append")
            }
        }

        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.trending.size == 3 && !it.isLoading }
            viewModel.loadMoreTrending()
            awaitItemUntil { it.trending.size == 5 }
            viewModel.loadMoreTrending()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { gameRepository.refreshTrendingGames(20, 3, true) }
        coVerify { gameRepository.refreshTrendingGames(20, 6, true) }
        coVerify(exactly = 0) { gameRepository.refreshTrendingGames(any(), 5, true) }
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
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(
                gameId = 1942L,
                status = LibraryStatus.COMPLETED,
                isFavorite = true,
                genres = listOf("RPG"),
            )
        ))
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
        val wantedFlow = MutableStateFlow<List<Game>>(emptyList())
        every { gameRepository.getPopularGamesFlow(DiscoverRail.WANTED_NOW.type) } returns wantedFlow
        coEvery { gameRepository.refreshPopular(DiscoverRail.WANTED_NOW.type, any(), any(), any()) } coAnswers {
            wantedFlow.value = wantedGames
            AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))
        }



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
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))
        ))
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
    fun append_removesFromVisibleTrending() = runTest {
        val trendingGame = Game(id = 11L, name = "Trending Game")
        trendingFlow.value = listOf(trendingGame)

        val c1 = RecommendationCandidate(101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L)
        val c2 = RecommendationCandidate(11L, "Trending Game", genres = listOf("RPG"), rating = 88.0, ratingCount = 150L)

        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))
        ))
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val offset = args[6] as Int
            val items = if (offset == 0) listOf(c1) else listOf(c2)
            AppResult.Success(RecommendationCandidatePage(items = items, nextOffset = offset + 20, endReached = false))
        }

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var item = awaitItem()
            var triggeredAppend = false
            while (true) {
                val recIds = item.recommendations.map { it.game.id }.toSet()
                val trendingIds = item.trending.map { it.id }.toSet()
                assertTrue(
                    "Turbine no-dual-visibility: game cannot appear in both recommendations and trending",
                    recIds.intersect(trendingIds).isEmpty(),
                )
                if (item.recommendations.any { it.game.id == 11L } && item.trending.none { it.id == 11L }) {
                    break
                }
                val feedSettled = !item.isLoading && item.trending.isNotEmpty()
                val readyToAppend = !triggeredAppend && feedSettled && item.recommendations.size == 1
                if (readyToAppend) {
                    triggeredAppend = true
                    viewModel.loadMoreForYou()
                }
                item = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rotate_prefersUnseen() = runTest {
        val c1 = RecommendationCandidate(101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L)
        val c2 = RecommendationCandidate(102L, "Rec 102", genres = listOf("RPG"), rating = 88.0, ratingCount = 150L)
        val c3 = RecommendationCandidate(103L, "Rec 103", genres = listOf("RPG"), rating = 85.0, ratingCount = 100L)

        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))
        ))

        var fetchCount = 0
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            fetchCount++
            val items = if (fetchCount == 1) listOf(c1, c2) else listOf(c1, c2, c3)
            AppResult.Success(RecommendationCandidatePage(items = items, nextOffset = 20, endReached = false))
        }

        val viewModel = createViewModel()
        viewModel.uiState.test {
            val initial = awaitItemUntil { it.recommendations.size == 2 && !it.isLoading }
            assertEquals(listOf(101L, 102L), initial.recommendations.map { it.game.id })

            viewModel.refresh()

            val rotated = awaitItemUntil { !it.isRefreshing && it.recommendations.any { rec -> rec.game.id == 103L } }
            assertEquals(listOf(103L), rotated.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rotate_allShown_fallback() = runTest {
        val c1 = RecommendationCandidate(101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L)
        val c2 = RecommendationCandidate(102L, "Rec 102", genres = listOf("RPG"), rating = 88.0, ratingCount = 150L)

        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(
            RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))
        ))
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(RecommendationCandidatePage(items = listOf(c1, c2), nextOffset = 20, endReached = false))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            val initial = awaitItemUntil { it.recommendations.size == 2 && !it.isLoading }
            assertEquals(listOf(101L, 102L), initial.recommendations.map { it.game.id })

            // Rotation re-fetches with history = {101, 102}; every eligible candidate
            // was shown, so the assembler falls back to the eligible set instead
            // of emitting an empty feed. The transient isRefreshing flag is not
            // asserted: StateFlow conflates it when content is identical.
            viewModel.refresh()
            advanceUntilIdle()

            val refreshed = viewModel.uiState.value
            assertFalse(refreshed.isRefreshing)
            assertEquals(listOf(101L, 102L), refreshed.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
        // Rotation path executed: initial build + one rebuild fetch.
        coVerify(exactly = 2) {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
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
    @Test
    fun railLoading_transitionsThroughErrorAndRecoversOnRetry() = runTest {
        val popularFlow = MutableStateFlow<List<Game>>(emptyList())
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns popularFlow
        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
        } returns AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val errorState = awaitItemUntil { state ->
                val rail = state.rails.firstOrNull { it.rail == DiscoverRail.POPULAR_NOW }
                rail?.error == AppError.NetworkError && !rail.isLoading
            }
            val failedRail = errorState.rails.first { it.rail == DiscoverRail.POPULAR_NOW }
            assertTrue(failedRail.games.isEmpty())
            assertEquals(AppError.NetworkError, failedRail.error)

            val recoveredGames = listOf(Game(id = 99L, name = "Recovered Popular Game"))
            coEvery {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            } coAnswers {
                popularFlow.value = recoveredGames
                AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))
            }

            viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)

            val successState = awaitItemUntil { state ->
                val rail = state.rails.firstOrNull { it.rail == DiscoverRail.POPULAR_NOW }
                rail?.games?.isNotEmpty() == true && !rail.isLoading && rail.error == null
            }
            val recoveredRail = successState.rails.first { it.rail == DiscoverRail.POPULAR_NOW }
            assertEquals(listOf(99L), recoveredRail.games.map { it.id })
            assertEquals(null, recoveredRail.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun appendFailure_doesNotRetryUntilExplicitRetry() = runTest {
        val initialGames = List(20) { Game(id = it.toLong(), name = "Game $it") }
        val popularFlow = MutableStateFlow(initialGames)
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns popularFlow
        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
        } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val loadedState = awaitItemUntil { state ->
                val rail = state.rails.firstOrNull { it.rail == DiscoverRail.POPULAR_NOW }
                rail?.games?.size == 20 && !rail.isLoading
            }
            assertEquals(20, loadedState.rails.first { it.rail == DiscoverRail.POPULAR_NOW }.games.size)

            coVerify(exactly = 1) {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            }

            coEvery {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            } returns AppResult.Error(AppError.NetworkError)

            viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)

            val errorState = awaitItemUntil { state ->
                val rail = state.rails.firstOrNull { it.rail == DiscoverRail.POPULAR_NOW }
                rail?.error == AppError.NetworkError && !rail.isLoading
            }
            val failedRail = errorState.rails.first { it.rail == DiscoverRail.POPULAR_NOW }
            assertEquals(20, failedRail.games.size)
            assertEquals(AppError.NetworkError, failedRail.error)

            coVerify(exactly = 2) {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            }

            advanceUntilIdle()
            coVerify(exactly = 2) {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            }

            coEvery {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

            viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)
            advanceUntilIdle()

            coVerify(exactly = 3) {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun popularPage_with19ItemsAndNextOffset20_loadsNextPage() = runTest {
        val popularFlow = MutableStateFlow(List(19) { Game(id = it.toLong() + 1, name = "G$it") })
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns popularFlow
        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
        } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

        val viewModel = createViewModel()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 0, false)
        }

        viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)
        advanceUntilIdle()
        coVerify {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 20, true)
        }
    }

    @Test
    fun cachedRail_isVisibleWhenInitialRefreshFails() = runTest {
        val cached = listOf(Game(id = 7L, name = "Cached"))
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns MutableStateFlow(cached)
        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), any(), any())
        } returns AppResult.Error(AppError.NetworkError)

        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItemUntil { ui ->
                val rail = ui.rails.firstOrNull { it.rail == DiscoverRail.POPULAR_NOW }
                rail?.games?.isNotEmpty() == true && rail.error == AppError.NetworkError
            }
            val rail = state.rails.first { it.rail == DiscoverRail.POPULAR_NOW }
            assertEquals(listOf(7L), rail.games.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedPullRefresh_preservesCachedRails() = runTest {
        val popularFlow = MutableStateFlow(listOf(Game(id = 3L, name = "Kept")))
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns popularFlow
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery {
            gameRepository.refreshPopular(any(), any(), any(), any())
        } returns AppResult.Error(AppError.NetworkError)

        viewModel.refresh()
        viewModel.uiState.test {
            val state = awaitItemUntil { ui ->
                val rail = ui.rails.firstOrNull { it.rail == DiscoverRail.POPULAR_NOW }
                rail?.error == AppError.NetworkError && rail.games.isNotEmpty() && !ui.isRefreshing
            }
            assertEquals(
                listOf(3L),
                state.rails.first { it.rail == DiscoverRail.POPULAR_NOW }.games.map { it.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun forYouRefreshFailure_preservesExistingRecommendations() = runTest {
        val candidate = RecommendationCandidate(
            101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L,
        )
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(
            RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Error(AppError.NetworkError)

        viewModel.refresh()
        viewModel.uiState.test {
            val state = awaitItemUntil { it.forYouError == AppError.NetworkError && it.recommendations.isNotEmpty() }
            assertEquals(listOf(101L), state.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun forYouAppendFailure_waitsForExplicitRetry() = runTest {
        val c1 = RecommendationCandidate(101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L)
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Success(
            RecommendationCandidatePage(items = listOf(c1), nextOffset = 30, endReached = false),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns AppResult.Error(AppError.NetworkError)

        viewModel.loadMoreForYou()
        advanceUntilIdle()
        coVerify(exactly = 2) {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        }

        viewModel.loadMoreForYou()
        advanceUntilIdle()
        coVerify(exactly = 2) {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        }

        viewModel.retryForYou()
        advanceUntilIdle()
        coVerify(exactly = 3) {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun forYouRefreshFailure_retryRebuildsPageZero() = runTest {
        val candidate = RecommendationCandidate(
            101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L,
        )
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        val offsets = mutableListOf<Int>()
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val offset = invocation.args[6] as Int
            offsets += offset
            if (offsets.size == 1) {
                AppResult.Success(
                    RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
                )
            } else {
                AppResult.Error(AppError.NetworkError)
            }
        }

        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && it.forYouError == null }
            assertEquals(listOf(0), offsets)

            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(AppError.NetworkError, viewModel.uiState.value.forYouError)
            assertEquals(listOf(101L), viewModel.uiState.value.recommendations.map { it.game.id })

            coEvery {
                gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                val offset = invocation.args[6] as Int
                offsets += offset
                AppResult.Success(
                    RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
                )
            }

            viewModel.retryForYou()
            advanceUntilIdle()
            assertEquals(0, offsets.last())
            assertEquals(null, viewModel.uiState.value.forYouError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun librarySignalFailure_retryRebuildsRecommendations() = runTest {
        val candidate = RecommendationCandidate(
            101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L,
        )
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        val offsets = mutableListOf<Int>()
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            offsets += invocation.args[6] as Int
            AppResult.Success(
                RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
            )
        }

        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && it.forYouError == null }
            assertEquals(listOf(0), offsets)

            coEvery {
                libraryRepository.getRecommendationSignals()
            } returns AppResult.Error(AppError.UnknownError(null))
            libraryFlow.value = listOf(libraryGame(7L, LibraryStatus.PLAYING))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.forYouError is AppError.UnknownError)
            assertEquals(listOf(101L), viewModel.uiState.value.recommendations.map { it.game.id })

            coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
                listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
            )
            viewModel.retryForYou()
            advanceUntilIdle()
            assertEquals(0, offsets.last())
            assertEquals(listOf(0, 0), offsets)
            assertEquals(null, viewModel.uiState.value.forYouError)
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
    @Test
    fun `reconnect when forYouError exists automatically retries For You`() = runTest {
        val networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        val candidate = RecommendationCandidate(
            101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L,
        )
        val offsets = mutableListOf<Int>()
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val offset = invocation.args[6] as Int
            offsets += offset
            if (offsets.size == 1) {
                AppResult.Success(
                    RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
                )
            } else {
                AppResult.Error(AppError.NetworkError)
            }
        }

        val viewModel = DiscoverViewModel(
            gameRepository = gameRepository,
            libraryRepository = libraryRepository,
            librarySeeder = librarySeeder,
            networkMonitor = networkMonitor,
        )

        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && it.forYouError == null }

            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(AppError.NetworkError, viewModel.uiState.value.forYouError)
            coEvery {
                gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                val offset = invocation.args[6] as Int
                offsets += offset
                AppResult.Success(
                    RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
                )
            }

            networkStatus.value = NetworkStatus.Available
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.forYouError)
            assertEquals(listOf(101L), viewModel.uiState.value.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `reconnect with trending and forYou errors rebuilds recommendations once`() = runTest {
        val networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Unavailable)
        val networkMonitor: NetworkMonitor = mockk {
            every { status } returns networkStatus
        }
        val candidate = RecommendationCandidate(
            101L, "Rec 101", genres = listOf("RPG"), rating = 90.0, ratingCount = 200L,
        )
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        // Initial load: trending fails and forYou fails
        coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } returns AppResult.Error(AppError.NetworkError)

        val candidateCalls = mutableListOf<Int>()
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val offset = invocation.args[6] as Int
            candidateCalls += offset
            if (candidateCalls.size == 1) {
                AppResult.Error(AppError.NetworkError)
            } else {
                AppResult.Success(
                    RecommendationCandidatePage(items = listOf(candidate), nextOffset = 30, endReached = false),
                )
            }
        }

        val viewModel = DiscoverViewModel(
            gameRepository = gameRepository,
            libraryRepository = libraryRepository,
            librarySeeder = librarySeeder,
            networkMonitor = networkMonitor,
        )

        viewModel.uiState.test {
            val errorState = awaitItemUntil { it.error != null && it.forYouError != null }
            assertEquals(AppError.NetworkError, errorState.error)
            assertEquals(AppError.NetworkError, errorState.forYouError)
            assertEquals(1, candidateCalls.size)

            // Configure success for trending on reconnect
            coEvery { gameRepository.refreshTrendingGames(any(), any(), any()) } coAnswers {
                trendingFlow.value = trendingGames
                AppResult.Success(PageContinuation(nextOffset = null, endReached = true))
            }

            // Network reconnects
            networkStatus.value = NetworkStatus.Available
            advanceUntilIdle()

            // Verify getRecommendationCandidatesPage was called exactly once during recovery (size == 2)
            assertEquals(2, candidateCalls.size)
            val recoveredState = viewModel.uiState.value
            assertEquals(null, recoveredState.error)
            assertEquals(null, recoveredState.forYouError)
            assertEquals(listOf(101L), recoveredState.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
    }


    private fun createViewModel(): DiscoverViewModel {
        return DiscoverViewModel(
            gameRepository = gameRepository,
            libraryRepository = libraryRepository,
            librarySeeder = librarySeeder,
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
