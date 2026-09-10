package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryImportMode

/**
 * Parses `gamertracker://dev/...` commands without touching `android.net.Uri`, so every accepted and
 * rejected form is unit-testable on the JVM. The Activity only extracts the host, the path segments
 * and the query parameters and hands them here.
 *
 * Number handling is deliberate: a value that is not a number is rejected (a typo must not silently
 * seed something else), while a numeric value outside the supported range is clamped, because the
 * intent is unambiguous.
 */
internal object DevToolsCommandParser {

    const val HOST = "dev"
    const val PATH_SEED = "seed"
    const val PATH_WIPE = "wipe"
    const val PATH_STATE = "state"
    const val QUERY_PRESET = "preset"
    const val QUERY_COUNT = "count"
    const val QUERY_SEED = "seed"
    const val QUERY_MODE = "mode"
    const val QUERY_TARGET = "target"
    const val DEFAULT_SEED = 42L

    private const val MODE_MERGE = "merge"
    private const val MODE_REPLACE = "replace"

    fun parse(host: String?, pathSegments: List<String>, param: (String) -> String?): DevCommandParseResult {
        if (!HOST.equals(host, ignoreCase = true)) return DevCommandParseResult.NotADevCommand
        return when (pathSegments.firstOrNull()?.lowercase()) {
            null, PATH_STATE -> DevCommandParseResult.Command(DevToolsCommand.ShowState)
            PATH_SEED -> parseSeed(param)
            PATH_WIPE -> parseWipe(param)
            else -> DevCommandParseResult.Invalid("unknown devtools command")
        }
    }

    private fun parseSeed(param: (String) -> String?): DevCommandParseResult {
        val presetParam = param(QUERY_PRESET)?.trim()
        val preset = if (presetParam.isNullOrEmpty()) {
            DevSeedPreset.REALISTIC
        } else {
            DevSeedPreset.parse(presetParam)
                ?: return DevCommandParseResult.Invalid("unknown preset \"$presetParam\"")
        }

        val countParam = param(QUERY_COUNT)?.trim()
        val count = if (countParam.isNullOrEmpty()) {
            preset.defaultCount
        } else {
            val requested = countParam.toIntOrNull()
                ?: return DevCommandParseResult.Invalid("count must be a number")
            DevSeedPreset.clampCount(requested)
        }

        val seedParam = param(QUERY_SEED)?.trim()
        val seed = if (seedParam.isNullOrEmpty()) {
            DEFAULT_SEED
        } else {
            seedParam.toLongOrNull() ?: return DevCommandParseResult.Invalid("seed must be a number")
        }

        val modeParam = param(QUERY_MODE)?.trim()
        val mode = when {
            modeParam.isNullOrEmpty() || modeParam.equals(MODE_REPLACE, ignoreCase = true) ->
                LibraryImportMode.REPLACE

            modeParam.equals(MODE_MERGE, ignoreCase = true) -> LibraryImportMode.MERGE
            else -> return DevCommandParseResult.Invalid("mode must be \"merge\" or \"replace\"")
        }

        return DevCommandParseResult.Command(
            DevToolsCommand.Seed(DevSeedRequest(preset, count, seed, mode)),
        )
    }

    private fun parseWipe(param: (String) -> String?): DevCommandParseResult {
        val targetParam = param(QUERY_TARGET)?.trim()
        if (targetParam.isNullOrEmpty()) return DevCommandParseResult.Invalid("target is required")
        val target = DevWipeTarget.parse(targetParam)
            ?: return DevCommandParseResult.Invalid("unknown target \"$targetParam\"")
        return DevCommandParseResult.Command(DevToolsCommand.Wipe(target))
    }
}
