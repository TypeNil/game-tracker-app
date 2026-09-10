package io.github.typenil.gametracker.devtools.ui

import io.github.typenil.gametracker.devtools.DevDiagnostics
import io.github.typenil.gametracker.devtools.DevSeedOutcome
import io.github.typenil.gametracker.devtools.DevWipeOutcome

internal data class DevToolsUiState(
    val isBusy: Boolean = false,
    val diagnostics: DevDiagnostics? = null,
    val diagnosticsUnavailable: Boolean = false,
    val lastResult: DevActionResult? = null,
)

/** What the last command did. Kept on screen rather than shown as a transient snackbar. */
internal sealed interface DevActionResult {
    data class Seeded(val outcome: DevSeedOutcome) : DevActionResult

    data class Wiped(val outcome: DevWipeOutcome) : DevActionResult

    data class Failed(val operation: String, val message: String) : DevActionResult

    /** A `gamertracker://dev/...` command that could not be honoured. */
    data class Rejected(val reason: String) : DevActionResult
}
