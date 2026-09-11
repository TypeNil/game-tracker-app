package io.github.typenil.gametracker.devtools.ui

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.github.typenil.gametracker.devtools.DevAppRestarter
import io.github.typenil.gametracker.devtools.DevDiagnostics
import io.github.typenil.gametracker.devtools.DevToolsRepository
import io.github.typenil.gametracker.devtools.DevWipeOutcome
import io.github.typenil.gametracker.devtools.DevWipeTarget
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Pins the one decision the repository deliberately does not make: a full reset must relaunch the
 * app, because the screens keep describing the database they were rendered from.
 */
class DevToolsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: DevToolsRepository = mockk()
    private val appRestarter: DevAppRestarter = mockk(relaxed = true)

    private fun viewModel(): DevToolsViewModel {
        coEvery { repository.diagnostics() } returns AppResult.Success(diagnostics())
        return DevToolsViewModel(repository = repository, appRestarter = appRestarter)
    }

    @Test
    fun wipingEverything_relaunchesTheAppAfterTheReset() = runTest {
        coEvery { repository.wipe(DevWipeTarget.ALL) } returns
            AppResult.Success(DevWipeOutcome(DevWipeTarget.ALL, 0, 0, 0))

        viewModel().wipe(DevWipeTarget.ALL)

        verify(exactly = 1) { appRestarter.restart() }
    }

    @Test
    fun wipingOneStore_leavesTheRunningUiAlone() = runTest {
        coEvery { repository.wipe(DevWipeTarget.LIBRARY) } returns
            AppResult.Success(DevWipeOutcome(DevWipeTarget.LIBRARY, 0, 0, 0))

        viewModel().wipe(DevWipeTarget.LIBRARY)

        verify(exactly = 0) { appRestarter.restart() }
    }

    @Test
    fun wipingEverything_doesNotRelaunchWhenTheResetFailed() = runTest {
        coEvery { repository.wipe(DevWipeTarget.ALL) } returns
            AppResult.Error(AppError.UnknownError(IllegalStateException("reset failed")))

        viewModel().wipe(DevWipeTarget.ALL)

        verify(exactly = 0) { appRestarter.restart() }
    }

    private fun diagnostics() = DevDiagnostics(
        applicationId = "io.github.typenil.gametracker.demo.debug",
        versionName = "1.0.3-demo",
        versionCode = 4,
        flavor = "demo",
        buildType = "debug",
        roomSchemaVersion = 7,
        databaseBytes = 0,
        writeAheadLogBytes = 0,
        libraryEntries = 0,
        libraryEntriesByStatus = LibraryStatus.entries.associateWith { 0 },
        notificationEvents = 0,
        searchHistoryEntries = 0,
        bffOverride = "",
    )
}
