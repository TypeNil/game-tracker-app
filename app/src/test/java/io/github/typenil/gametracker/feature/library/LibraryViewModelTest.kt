package io.github.typenil.gametracker.feature.library

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val fakeLibraryRepository = FakeLibraryRepository()

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

    private fun createViewModel(): LibraryViewModel = LibraryViewModel(fakeLibraryRepository)

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
            assertEquals(listOf(hades, eldenRing), favState.filteredGames)

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
            // Initial sort is UPDATED_DESC: hades (500), eldenRing (400), hollowKnight (300)
            val initial = awaitItem()
            assertEquals(listOf(hades, eldenRing, hollowKnight), initial.filteredGames)

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

    private class FakeLibraryRepository : LibraryRepository {
        val libraryGamesFlow = MutableStateFlow<List<LibraryGame>>(emptyList())

        override fun getLibraryGamesFlow(): Flow<List<LibraryGame>> = libraryGamesFlow
        override fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntry?> = flowOf(null)
        override suspend fun setGameStatus(
            gameId: Long,
            status: LibraryStatus
        ): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun addToWishlist(game: Game): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun upsertUserEdits(
            gameId: Long,
            status: LibraryStatus,
            userRating: Int?,
            hoursPlayed: Int,
            userNotes: String?,
            isFavorite: Boolean,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> = AppResult.Success(Unit)
    }
}
