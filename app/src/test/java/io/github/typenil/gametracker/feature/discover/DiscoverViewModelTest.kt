package io.github.typenil.gametracker.feature.discover

import app.cash.turbine.test
import io.github.typenil.gametracker.R

import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.repository.GameRepository

import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.data.FakeUserPreferencesRepository
import io.github.typenil.gametracker.core.model.UserPreferences
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.PageContinuation

import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryEntryDraft

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
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
            libraryRepository.upsertUserEdits(any(), any())
        } returns AppResult.Success(Unit)
        coEvery { libraryRepository.removeGameFromLibrary(any()) } returns AppResult.Success(Unit)
        coEvery { gameRepository.refreshGameDetails(any(), any()) } returns AppResult.Success(Unit)
    }

    @Test
    fun init_andPullToRefresh_doNotCallRefreshTrendingGames() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItemUntil { !it.isLoading }
            assertTrue(state.recommendations.isEmpty())
            assertFalse(state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { librarySeeder.seedIfEmpty() }
        coVerify(exactly = 0) { gameRepository.refreshTrendingGames(any(), any(), any()) }

        viewModel.refresh()
        advanceUntilIdle()
        coVerify(exactly = 0) { gameRepository.refreshTrendingGames(any(), any(), any()) }
    }

    @Test
    fun recsError_doesNotTreatTrendingCacheAsContent() = runTest {
        trendingFlow.value = trendingGames
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
            val state = awaitItemUntil { it.forYouError != null && !it.isLoading }
            assertTrue(state.recommendations.isEmpty())
            assertTrue(state.rails.all { it.games.isEmpty() })
            assertFalse(state.hasContent)
            assertFalse(state.isInitialLoading)
            assertEquals(AppError.NetworkError, state.forYouError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refresh_setsRefreshingOnlyForPullToRefresh() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        coEvery { gameRepository.refreshPopular(any(), any(), any(), any()) } coAnswers {
            gate.await()
            AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))
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
    fun positiveLibrary_buildsForYouRecommendations() = runTest {
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
            cancelAndIgnoreRemainingEvents()
        }
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
        val draft = LibraryEntryDraft(
            status = LibraryStatus.PLAYING,
            userRating = 8,
            hoursPlayed = 12,
            userNotes = "fun",
            isFavorite = true,
            releaseNotificationsEnabled = true,
        )
        viewModel.onSaveLibraryEntry(11L, draft)
        advanceUntilIdle()
        coVerify {
            libraryRepository.upsertUserEdits(11L, draft)
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
            libraryRepository.upsertUserEdits(any(), any())
        } returns AppResult.Error(AppError.UnknownError(IllegalStateException("fail")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onLibraryCardAction(Game(id = 11L, name = "Trending Game"))
        advanceUntilIdle()
        viewModel.onSaveLibraryEntry(
            11L,
            LibraryEntryDraft(
                status = LibraryStatus.PLAYING,
                userRating = 8,
                hoursPlayed = 12,
                userNotes = "fun",
                isFavorite = true,
                releaseNotificationsEnabled = false,
            ),
        )
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
            userPreferencesRepository = FakeUserPreferencesRepository(),
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
    fun `reconnect with forYou error rebuilds recommendations once`() = runTest {
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
            userPreferencesRepository = FakeUserPreferencesRepository(),
            networkMonitor = networkMonitor,
        )

        viewModel.uiState.test {
            val errorState = awaitItemUntil { it.forYouError != null }
            assertEquals(AppError.NetworkError, errorState.forYouError)
            assertEquals(1, candidateCalls.size)

            networkStatus.value = NetworkStatus.Available
            advanceUntilIdle()

            assertEquals(2, candidateCalls.size)
            val recoveredState = viewModel.uiState.value
            assertEquals(null, recoveredState.forYouError)
            assertEquals(listOf(101L), recoveredState.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `pull to refresh on selected rail preserves other rail cursor for subsequent append`() = runTest {
        val popularFlow = MutableStateFlow(listOf(Game(id = 1L, name = "Pop 1")))
        val wantedFlow = MutableStateFlow(listOf(Game(id = 2L, name = "Wanted 1")))
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns popularFlow
        every { gameRepository.getPopularGamesFlow(DiscoverRail.WANTED_NOW.type) } returns wantedFlow

        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 0, false)
        } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 20, true)
        } returns AppResult.Success(PageContinuation(nextOffset = 40, endReached = false))

        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 40, true)
        } returns AppResult.Success(PageContinuation(nextOffset = 60, endReached = false))

        coEvery {
            gameRepository.refreshPopular(DiscoverRail.WANTED_NOW.type, any(), any(), any())
        } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

        val viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 0, false)
        }

        viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 20, true)
        }

        viewModel.selectRail(DiscoverRail.WANTED_NOW)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 40, true)
        }
        coVerify(exactly = 1) {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 0, false)
        }
    }

    @Test
    fun `cancelled rail append does not stay loading or replace cursor on deferred completion`() = runTest {
        val popularFlow = MutableStateFlow(listOf(Game(id = 1L, name = "Pop 1")))
        val wantedFlow = MutableStateFlow(listOf(Game(id = 2L, name = "Wanted 1")))
        every { gameRepository.getPopularGamesFlow(DiscoverRail.POPULAR_NOW.type) } returns popularFlow
        every { gameRepository.getPopularGamesFlow(DiscoverRail.WANTED_NOW.type) } returns wantedFlow

        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 0, false)
        } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

        coEvery {
            gameRepository.refreshPopular(DiscoverRail.WANTED_NOW.type, any(), any(), any())
        } returns AppResult.Success(PageContinuation(nextOffset = 20, endReached = false))

        val appendDeferred = CompletableDeferred<AppResult<PageContinuation>>()
        coEvery {
            gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 20, true)
        } coAnswers {
            appendDeferred.await()
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val initial = awaitItemUntil { !it.isLoading }
            assertFalse(initial.rails.first { it.rail == DiscoverRail.POPULAR_NOW }.isLoading)

            viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)
            val loadingState = awaitItemUntil { state ->
                state.rails.first { it.rail == DiscoverRail.POPULAR_NOW }.isLoading
            }
            assertTrue(loadingState.rails.first { it.rail == DiscoverRail.POPULAR_NOW }.isLoading)

            viewModel.selectRail(DiscoverRail.WANTED_NOW)
            val selectedState = awaitItemUntil { it.selectedRail == DiscoverRail.WANTED_NOW }
            assertEquals(DiscoverRail.WANTED_NOW, selectedState.selectedRail)

            viewModel.refresh()
            advanceUntilIdle()

            val stateAfterRefresh = viewModel.uiState.value
            val popularRailAfterRefresh = stateAfterRefresh.rails.first { it.rail == DiscoverRail.POPULAR_NOW }
            assertFalse(popularRailAfterRefresh.isLoading)

            appendDeferred.complete(
                AppResult.Success(PageContinuation(nextOffset = 999, endReached = false)),
            )
            advanceUntilIdle()

            val stateAfterDeferred = viewModel.uiState.value
            val popularRailAfterDeferred = stateAfterDeferred.rails.first { it.rail == DiscoverRail.POPULAR_NOW }
            assertFalse(popularRailAfterDeferred.isLoading)

            coEvery {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 20, true)
            } returns AppResult.Success(PageContinuation(nextOffset = 40, endReached = false))

            viewModel.loadMoreRail(DiscoverRail.POPULAR_NOW)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, any(), 999, any())
            }
            coVerify {
                gameRepository.refreshPopular(DiscoverRail.POPULAR_NOW.type, 20, 20, true)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `forYou rebuild empty first page continues to fill next offset until eligible candidate found`() = runTest {
        val candidateInLibrary = RecommendationCandidate(
            gameId = 1942L,
            name = "In Library Game",
            genres = listOf("RPG"),
            rating = 90.0,
            ratingCount = 200L,
        )
        val eligibleCandidate = RecommendationCandidate(
            gameId = 2024L,
            name = "Eligible Game",
            genres = listOf("RPG"),
            rating = 95.0,
            ratingCount = 300L,
        )
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(
            listOf(RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))),
        )
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val offset = invocation.args[6] as Int
            if (offset == 0) {
                AppResult.Success(
                    RecommendationCandidatePage(
                        items = listOf(candidateInLibrary),
                        nextOffset = 30,
                        endReached = false,
                    ),
                )
            } else {
                AppResult.Success(
                    RecommendationCandidatePage(
                        items = listOf(eligibleCandidate),
                        nextOffset = null,
                        endReached = true,
                    ),
                )
            }
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItemUntil { !it.isLoading }
            assertEquals(listOf(2024L), state.recommendations.map { it.game.id })
            assertFalse(state.forYouLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forYou append does not reinsert candidate added to library while append request was in flight`() = runTest {
        val initial = rpgCandidate(101L, "Initial Rec")
        val appendCandidate = rpgCandidate(102L, "Append Rec")
        stubFavoriteRpgSignals()
        val appendDeferred = CompletableDeferred<AppResult<RecommendationCandidatePage>>()
        stubCandidatePages { offset ->
            when (offset) {
                0 -> candidatePage(listOf(initial), nextOffset = 30, endReached = false)
                30 -> appendDeferred.await()
                else -> candidatePage(emptyList(), nextOffset = null, endReached = true)
            }
        }
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }
            viewModel.loadMoreForYou()
            runCurrent()
            stubFavoriteRpgSignals(
                RecommendationSignal(102L, LibraryStatus.COMPLETED, isFavorite = false, genres = listOf("RPG")),
            )
            libraryFlow.value = listOf(
                libraryGame(1942L, LibraryStatus.COMPLETED, "RPG Game"),
                libraryGame(102L, LibraryStatus.COMPLETED, "Append Rec"),
            )
            advanceUntilIdle()
            appendDeferred.complete(candidatePage(listOf(appendCandidate), nextOffset = 60, endReached = false))
            advanceUntilIdle()
            val state = awaitItemUntil { !it.forYouLoading }
            assertFalse(state.recommendations.any { it.game.id == 102L })
            assertEquals(listOf(101L), state.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forYou loadMoreForYou on queued dispatcher executes exactly one candidate page append`() = runTest {
        stubFavoriteRpgSignals()
        stubCandidatePages { offset ->
            if (offset == 0) {
                candidatePage(listOf(rpgCandidate(101L, "Initial Rec")), nextOffset = 30, endReached = false)
            } else {
                candidatePage(listOf(rpgCandidate(102L, "Append Rec")), nextOffset = null, endReached = true)
            }
        }
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        try {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.loadMoreForYou()
            runCurrent()
            advanceUntilIdle()
            coVerify(exactly = 1) {
                gameRepository.getRecommendationCandidatesPage(
                    any(), any(), any(), any(), any(), any(), 30, any(),
                )
            }
        } finally {
            Dispatchers.setMain(mainDispatcherRule.testDispatcher)
        }
    }

    @Test
    fun emptyingLibrary_clearsForYouAndShowsColdStart() = runTest {
        libraryFlow.value = listOf(libraryGame(1942L, LibraryStatus.COMPLETED, "RPG Game"))
        stubFavoriteRpgSignals()
        stubCandidatePages {
            candidatePage(
                items = listOf(rpgCandidate(101L, "Rec 101")),
                nextOffset = null,
                endReached = true,
            )
        }
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val filled = awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }
            assertFalse(filled.isColdStart)
            assertEquals(listOf(101L), filled.recommendations.map { it.game.id })

            coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(emptyList())
            libraryFlow.value = emptyList()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(viewModel.uiState.value.isColdStart)
        assertTrue(viewModel.uiState.value.recommendations.isEmpty())
    }

    @Test
    fun emptyLibrary_withColdStartPrefs_fetchesForYouCandidates() = runTest {
        val prefs = FakeUserPreferencesRepository(
            UserPreferences(
                recommendationGenres = setOf("Role-playing (RPG)", "Action", "Adventure"),
                recommendationPlatforms = setOf("NINTENDO"),
                recommendationOnboardingDismissed = true,
            ),
        )
        stubCandidatePages {
            candidatePage(
                items = listOf(rpgCandidate(101L, "Rec 101")),
                nextOffset = null,
                endReached = true,
            )
        }
        val viewModel = createViewModel(prefs)
        viewModel.uiState.test {
            val filled = awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }
            assertEquals(listOf(101L), filled.recommendations.map { it.game.id })
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(atLeast = 1) {
            gameRepository.getRecommendationCandidatesPage(
                genres = match { tags ->
                    tags.containsAll(listOf("Adventure", "Role-playing (RPG)")) &&
                        "Action" !in tags
                },
                themes = match { tags -> "Action" in tags },
                platforms = match { tags ->
                    tags.containsAll(
                        listOf(
                            "Nintendo Switch", "Nintendo Switch 2", "Wii U", "Wii",
                            "Nintendo 3DS", "Nintendo DS", "Nintendo 64", "SNES", "NES",
                        ),
                    )
                },
                exclude = any(),
                similarTo = any(),
                limit = any(),
                offset = any(),
                sort = any(),
            )
        }
    }

    @Test
    fun resetPrefs_clearsColdStartSignalsAndStopsFetchOnEmptyLibrary() = runTest {
        val prefs = FakeUserPreferencesRepository(
            UserPreferences(
                recommendationGenres = setOf("Role-playing (RPG)", "Action", "Adventure"),
                recommendationPlatforms = setOf("NINTENDO"),
                recommendationOnboardingDismissed = true,
            ),
        )
        stubCandidatePages {
            candidatePage(
                items = listOf(rpgCandidate(101L, "Rec 101")),
                nextOffset = null,
                endReached = true,
            )
        }
        val viewModel = createViewModel(prefs)
        viewModel.uiState.test {
            awaitItemUntil { it.recommendations.isNotEmpty() && !it.isLoading }
            val cleared = viewModel.resetRecommendationPreferences()
            assertTrue(cleared)
            val cold = awaitItemUntil { it.isColdStart && it.recommendations.isEmpty() }
            assertTrue(cold.recommendationOnboardingDismissed)
            assertTrue(cold.recommendationGenres.isEmpty())
            assertTrue(cold.recommendationPlatforms.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }




    private fun createViewModel(
        userPreferencesRepository: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    ): DiscoverViewModel {
        return DiscoverViewModel(
            gameRepository = gameRepository,
            libraryRepository = libraryRepository,
            librarySeeder = librarySeeder,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    private fun rpgCandidate(id: Long, name: String): RecommendationCandidate =
        RecommendationCandidate(id, name, genres = listOf("RPG"), rating = 90.0, ratingCount = 200L)

    private fun candidatePage(
        items: List<RecommendationCandidate>,
        nextOffset: Int?,
        endReached: Boolean,
    ): AppResult.Success<RecommendationCandidatePage> =
        AppResult.Success(RecommendationCandidatePage(items, nextOffset, endReached))

    private fun stubFavoriteRpgSignals(vararg extra: RecommendationSignal) {
        val base = RecommendationSignal(1942L, LibraryStatus.COMPLETED, isFavorite = true, genres = listOf("RPG"))
        coEvery { libraryRepository.getRecommendationSignals() } returns AppResult.Success(listOf(base) + extra)
    }

    private fun stubCandidatePages(
        pageForOffset: suspend (Int) -> AppResult<RecommendationCandidatePage>,
    ) {
        coEvery {
            gameRepository.getRecommendationCandidatesPage(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            pageForOffset(invocation.args[CANDIDATE_OFFSET_ARG_INDEX] as Int)
        }
    }

    private companion object {
        const val CANDIDATE_OFFSET_ARG_INDEX = 6
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
