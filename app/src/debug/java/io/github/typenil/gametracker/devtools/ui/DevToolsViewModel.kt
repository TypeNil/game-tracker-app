package io.github.typenil.gametracker.devtools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.devtools.DevSeedRequest
import io.github.typenil.gametracker.devtools.DevToolsCommand
import io.github.typenil.gametracker.devtools.DevToolsRepository
import io.github.typenil.gametracker.devtools.DevWipeTarget
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the developer tools' state.
 *
 * The repository already dispatches its own I/O, so this only sequences operations and keeps one
 * operation in flight at a time: two overlapping seeds would both report "success" while the second
 * one decided what the library actually looks like.
 */
@HiltViewModel
internal class DevToolsViewModel @Inject constructor(
    private val repository: DevToolsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevToolsUiState())
    val uiState: StateFlow<DevToolsUiState> = _uiState.asStateFlow()

    init {
        refreshDiagnostics()
    }

    /** Entry point for `gamertracker://dev/...`, so automation and the screen share one path. */
    fun run(command: DevToolsCommand) {
        when (command) {
            is DevToolsCommand.Seed -> seed(command.request)
            is DevToolsCommand.Wipe -> wipe(command.target)
            DevToolsCommand.ShowState -> refreshDiagnostics()
        }
    }

    fun seed(request: DevSeedRequest) {
        startOperation(OPERATION_SEED) {
            when (val result = repository.seed(request)) {
                is AppResult.Success -> DevActionResult.Seeded(result.data)
                is AppResult.Error -> DevActionResult.Failed(OPERATION_SEED, result.error.describe())
            }
        }
    }

    fun wipe(target: DevWipeTarget) {
        startOperation(OPERATION_WIPE) {
            when (val result = repository.wipe(target)) {
                is AppResult.Success -> DevActionResult.Wiped(result.data)
                is AppResult.Error -> DevActionResult.Failed(OPERATION_WIPE, result.error.describe())
            }
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _uiState.update { state ->
                when (val result = repository.diagnostics()) {
                    is AppResult.Success ->
                        state.copy(diagnostics = result.data, diagnosticsUnavailable = false)

                    is AppResult.Error -> state.copy(diagnosticsUnavailable = true)
                }
            }
        }
    }

    fun onCommandRejected(reason: String) {
        _uiState.update { it.copy(lastResult = DevActionResult.Rejected(reason)) }
    }

    private fun startOperation(operation: String, block: suspend () -> DevActionResult) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, lastResult = null) }
            val result = block()
            // Every operation changes the numbers the panel is there to show, so re-read them.
            val diagnostics = repository.diagnostics()
            _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    lastResult = result,
                    diagnostics = (diagnostics as? AppResult.Success)?.data ?: state.diagnostics,
                    diagnosticsUnavailable = state.diagnosticsUnavailable && diagnostics is AppResult.Error,
                )
            }
        }
    }

    private companion object {
        const val OPERATION_SEED = "Seed"
        const val OPERATION_WIPE = "Clear"
    }
}

private fun AppError.describe(): String = when (this) {
    AppError.NetworkError -> "network error"
    is AppError.HttpError -> "HTTP $statusCode${message?.let { ": $it" }.orEmpty()}"
    is AppError.SerializationError -> message ?: "serialization error"
    is AppError.UnknownError -> cause?.message ?: cause?.toString() ?: "unknown error"
}
