package io.github.typenil.gametracker.devtools

/**
 * A command carried by a `gamertracker://dev/...` URI.
 *
 * The same commands are reachable from the developer tools screen, so the UI and `adb` automation
 * cannot drift apart.
 */
internal sealed interface DevToolsCommand {
    data class Seed(val request: DevSeedRequest) : DevToolsCommand
    data class Wipe(val target: DevWipeTarget) : DevToolsCommand
    data object ShowState : DevToolsCommand
}

internal sealed interface DevCommandParseResult {
    data class Command(val command: DevToolsCommand) : DevCommandParseResult

    /** A devtools URI whose command cannot be honoured; [reason] is surfaced to the developer. */
    data class Invalid(val reason: String) : DevCommandParseResult

    /** Not a devtools URI: the screen opens and nothing runs. */
    data object NotADevCommand : DevCommandParseResult
}
