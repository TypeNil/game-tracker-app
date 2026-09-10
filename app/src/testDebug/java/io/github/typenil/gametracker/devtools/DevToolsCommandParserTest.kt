package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsCommandParserTest {

    private fun parse(
        host: String?,
        path: String?,
        params: Map<String, String> = emptyMap(),
    ): DevCommandParseResult = DevToolsCommandParser.parse(host, listOfNotNull(path)) { params[it] }

    private fun expectedSeed(result: DevCommandParseResult): DevSeedRequest {
        val command = (result as DevCommandParseResult.Command).command
        return (command as DevToolsCommand.Seed).request
    }

    private fun expectedWipe(result: DevCommandParseResult): DevWipeTarget {
        val command = (result as DevCommandParseResult.Command).command
        return (command as DevToolsCommand.Wipe).target
    }

    @Test
    fun otherHosts_areNotDevCommands() {
        assertEquals(
            DevCommandParseResult.NotADevCommand,
            parse("game", "seed", mapOf(DevToolsCommandParser.QUERY_PRESET to "EDGE")),
        )
        assertEquals(DevCommandParseResult.NotADevCommand, parse(null, "seed"))
    }

    @Test
    fun bareDevUri_opensTheScreenWithoutRunningAnything() {
        assertEquals(
            DevCommandParseResult.Command(DevToolsCommand.ShowState),
            parse(DevToolsCommandParser.HOST, null),
        )
        assertEquals(
            DevCommandParseResult.Command(DevToolsCommand.ShowState),
            parse(DevToolsCommandParser.HOST, DevToolsCommandParser.PATH_STATE),
        )
    }

    @Test
    fun unknownCommand_isRejected() {
        assertTrue(parse(DevToolsCommandParser.HOST, "drop-everything") is DevCommandParseResult.Invalid)
    }

    @Test
    fun seedWithNoParameters_usesTheRealisticDefaults() {
        val request = expectedSeed(parse(DevToolsCommandParser.HOST, DevToolsCommandParser.PATH_SEED))

        assertEquals(DevSeedPreset.REALISTIC, request.preset)
        assertEquals(DevSeedPreset.REALISTIC.defaultCount, request.count)
        assertEquals(DevToolsCommandParser.DEFAULT_SEED, request.seed)
        assertEquals(LibraryImportMode.REPLACE, request.mode)
    }

    @Test
    fun seedHonoursEveryParameter() {
        val request = expectedSeed(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_SEED,
                mapOf(
                    DevToolsCommandParser.QUERY_PRESET to "stress",
                    DevToolsCommandParser.QUERY_COUNT to "12",
                    DevToolsCommandParser.QUERY_SEED to "99",
                    DevToolsCommandParser.QUERY_MODE to "Merge",
                ),
            ),
        )

        assertEquals(DevSeedPreset.STRESS, request.preset)
        assertEquals(12, request.count)
        assertEquals(99L, request.seed)
        assertEquals(LibraryImportMode.MERGE, request.mode)
    }

    @Test
    fun outOfRangeCount_isClampedNotRejected() {
        val request = expectedSeed(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_SEED,
                mapOf(DevToolsCommandParser.QUERY_COUNT to "10000"),
            ),
        )

        assertEquals(DevSeedPreset.MAX_COUNT, request.count)
    }

    @Test
    fun unknownPreset_isRejected() {
        assertTrue(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_SEED,
                mapOf(DevToolsCommandParser.QUERY_PRESET to "TOTALLY_REAL"),
            ) is DevCommandParseResult.Invalid,
        )
    }

    @Test
    fun nonNumericCountAndSeed_areRejected() {
        assertTrue(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_SEED,
                mapOf(DevToolsCommandParser.QUERY_COUNT to "many"),
            ) is DevCommandParseResult.Invalid,
        )
        assertTrue(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_SEED,
                mapOf(DevToolsCommandParser.QUERY_SEED to "later"),
            ) is DevCommandParseResult.Invalid,
        )
    }

    @Test
    fun unknownMode_isRejected() {
        assertTrue(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_SEED,
                mapOf(DevToolsCommandParser.QUERY_MODE to "sideways"),
            ) is DevCommandParseResult.Invalid,
        )
    }

    @Test
    fun wipeReadsTheTargetCaseInsensitively() {
        assertEquals(
            DevWipeTarget.LIBRARY,
            expectedWipe(
                parse(
                    DevToolsCommandParser.HOST,
                    DevToolsCommandParser.PATH_WIPE,
                    mapOf(DevToolsCommandParser.QUERY_TARGET to "library"),
                ),
            ),
        )
        assertEquals(
            DevWipeTarget.NOTIFICATION_LEDGER,
            expectedWipe(
                parse(
                    DevToolsCommandParser.HOST,
                    DevToolsCommandParser.PATH_WIPE,
                    mapOf(DevToolsCommandParser.QUERY_TARGET to "NOTIFICATION_LEDGER"),
                ),
            ),
        )
    }

    @Test
    fun wipeWithoutOrWithAnUnknownTarget_isRejected() {
        assertTrue(
            parse(DevToolsCommandParser.HOST, DevToolsCommandParser.PATH_WIPE) is DevCommandParseResult.Invalid,
        )
        assertTrue(
            parse(
                DevToolsCommandParser.HOST,
                DevToolsCommandParser.PATH_WIPE,
                mapOf(DevToolsCommandParser.QUERY_TARGET to "EVERYTHING"),
            ) is DevCommandParseResult.Invalid,
        )
    }
}
