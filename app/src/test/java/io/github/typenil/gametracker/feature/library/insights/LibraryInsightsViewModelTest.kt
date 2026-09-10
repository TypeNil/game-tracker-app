package io.github.typenil.gametracker.feature.library.insights

import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryInsightsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var fakeLibraryRepository: FakeLibraryRepository

    @Before
    fun setUp() {
        fakeLibraryRepository = FakeLibraryRepository()
    }

    @Test
    fun successWithGames_emitsContent() = runTest {
        fakeLibraryRepository.flows = listOf(flowOf(AppResult.Success(listOf(playingGame()))))

        val viewModel = LibraryInsightsViewModel(fakeLibraryRepository)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is LibraryInsightsUiState.Content)
            assertEquals(1, (state as LibraryInsightsUiState.Content).insights.totalGames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun successEmptyOrNotInterestedOnly_emitsEmpty() = runTest {
        fakeLibraryRepository.flows = listOf(
            flowOf(
                AppResult.Success(
                    listOf(
                        libraryGame(id = 1L, name = "Skip", status = LibraryStatus.NOT_INTERESTED),
                    ),
                ),
            ),
        )

        val viewModel = LibraryInsightsViewModel(fakeLibraryRepository)

        viewModel.uiState.test {
            assertEquals(LibraryInsightsUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repositoryError_emitsError() = runTest {
        val error = AppError.UnknownError(null)
        fakeLibraryRepository.flows = listOf(flowOf(AppResult.Error(error)))

        val viewModel = LibraryInsightsViewModel(fakeLibraryRepository)

        viewModel.uiState.test {
            assertEquals(LibraryInsightsUiState.Error(error), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun retryAfterTerminalRepositoryError_resubscribesAndShowsContent() = runTest {
        fakeLibraryRepository.flows = listOf(
            flow {
                emit(AppResult.Error(AppError.UnknownError(null)))
            },
            flowOf(AppResult.Success(listOf(playingGame()))),
        )

        val viewModel = LibraryInsightsViewModel(fakeLibraryRepository)

        viewModel.uiState.test {
            assertTrue(awaitItem() is LibraryInsightsUiState.Error)
            assertEquals(1, fakeLibraryRepository.subscriptions)

            viewModel.onRetry()

            val content = awaitItem()
            assertTrue(content is LibraryInsightsUiState.Content)
            assertEquals(2, fakeLibraryRepository.subscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeLibraryRepository : LibraryRepository {
        var flows: List<Flow<AppResult<List<LibraryGame>>>> = listOf(
            flowOf(AppResult.Success(emptyList())),
        )
        var subscriptions: Int = 0

        override fun getLibraryGamesFlow(): Flow<AppResult<List<LibraryGame>>> {
            val flow = flows.getOrElse(subscriptions) { flows.last() }
            subscriptions++
            return flow
        }

        override fun getLibraryEntryFlow(gameId: Long): Flow<AppResult<LibraryEntry?>> =
            flowOf(AppResult.Success(null))

        override suspend fun setGameStatus(
            gameId: Long,
            status: LibraryStatus,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> =
            AppResult.Success(Unit)

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
            hoursPlayed: Int,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> =
            AppResult.Success(Unit)
    }
}

private fun playingGame(): LibraryGame =
    libraryGame(id = 1L, name = "Hades", status = LibraryStatus.PLAYING, hoursPlayed = 12)

private fun libraryGame(
    id: Long,
    name: String,
    status: LibraryStatus,
    hoursPlayed: Int = 0,
): LibraryGame = LibraryGame(
    game = Game(id = id, name = name),
    entry = LibraryEntry(
        gameId = id,
        status = status,
        addedAtEpochSeconds = 1L,
        updatedAtEpochSeconds = 1L,
        hoursPlayed = hoursPlayed,
    ),
)
