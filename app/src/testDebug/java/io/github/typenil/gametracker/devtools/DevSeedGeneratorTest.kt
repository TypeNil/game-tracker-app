package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryNotes
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevSeedGeneratorTest {

    private val now = 1_800_000_000L
    private val catalogSize = 90

    private val catalog: List<Game> = List(catalogSize) { index ->
        Game(
            id = CATALOG_ID_BASE + index,
            name = "Catalog game $index",
            // Mixed release dates so the "unreleased" branch is reachable in both directions.
            releaseDateEpochSeconds = now + (index % 3 - 1) * SECONDS_PER_DAY,
        )
    }

    private fun generate(
        preset: DevSeedPreset,
        count: Int = preset.defaultCount,
        seed: Long = DEFAULT_TEST_SEED,
    ): DevSeed = DevSeedGenerator.generate(
        DevSeedRequest(preset, count, seed, LibraryImportMode.REPLACE),
        catalog,
        now,
    )

    @Test
    fun sameSeed_producesTheSameLibrary() {
        assertEquals(
            generate(DevSeedPreset.REALISTIC).items,
            generate(DevSeedPreset.REALISTIC).items,
        )
    }

    @Test
    fun everyPreset_producesConsistentDistinctItems() {
        DevSeedPreset.entries.forEach { preset ->
            val items = generate(preset).items
            assertTrue("$preset produced nothing", items.isNotEmpty())
            items.forEach { item ->
                assertEquals("$preset broke the entry/game id contract", item.game.id, item.entry.gameId)
            }
            assertEquals(
                "$preset produced duplicate game ids",
                items.size,
                items.map { it.game.id }.distinct().size,
            )
        }
    }

    @Test
    fun realistic_honoursTheRequestedCount() {
        assertEquals(20, generate(DevSeedPreset.REALISTIC).items.size)
        assertEquals(5, generate(DevSeedPreset.REALISTIC, count = 5).items.size)
        // Never more than the catalog can supply.
        assertEquals(catalogSize, generate(DevSeedPreset.REALISTIC, count = 500).items.size)
    }

    @Test
    fun stress_producesExactlyTheRequestedCount() {
        assertEquals(150, generate(DevSeedPreset.STRESS).items.size)
        assertEquals(500, generate(DevSeedPreset.STRESS, count = 500).items.size)
    }

    @Test
    fun coverage_reachesEveryStatusEvenForATinyRequest() {
        val items = generate(DevSeedPreset.COVERAGE, count = 1).items

        assertEquals(
            "coverage must reach every library status",
            LibraryStatus.entries.toSet(),
            items.map { it.entry.status }.toSet(),
        )
        assertTrue("coverage must stay usable below the default count", items.size >= 7)
    }

    @Test
    fun coverage_keepsRatingsAndHoursInsideTheStoredRanges() {
        val items = generate(DevSeedPreset.COVERAGE).items
        val ratings = items.mapNotNull { it.entry.userRating }

        assertEquals(setOf(MIN_RATING, MAX_RATING), ratings.toSet())
        assertEquals(setOf(0, MAX_HOURS), items.map { it.entry.hoursPlayed }.filter { it == 0 || it == MAX_HOURS }.toSet())
        assertEquals(setOf(true, false), items.map { it.entry.isFavorite }.toSet())
        assertTrue(items.any { it.entry.releaseNotificationsEnabled })
    }

    @Test
    fun everyPreset_keepsStoredValuesInsideTheSchemaRanges() {
        DevSeedPreset.entries.forEach { preset ->
            generate(preset).items.forEach { item ->
                val entry = item.entry
                assertTrue("$preset produced a negative game id", entry.gameId > 0)
                assertTrue("$preset produced a blank name", item.game.name.isNotBlank())
                assertTrue("$preset produced negative hours", entry.hoursPlayed >= 0)
                assertTrue("$preset produced a negative addedAt", entry.addedAtEpochSeconds >= 0)
                assertTrue(
                    "$preset produced updatedAt before addedAt",
                    entry.updatedAtEpochSeconds >= entry.addedAtEpochSeconds,
                )
                entry.userRating?.let { rating ->
                    assertTrue("$preset produced rating $rating", rating in MIN_RATING..MAX_RATING)
                }
            }
        }
    }

    @Test
    fun edge_carriesThePathologicalTitles() {
        val names = generate(DevSeedPreset.EDGE).items.map { it.game.name }

        assertEquals("expected a 200-character title", 200, names.first().length)
        assertEquals(2, names.count { it == "Doom" })
        assertTrue("expected a CJK title", names.any { it.contains("日本語") })
        assertTrue("expected a right-to-left title", names.any { it.contains("עברית") })
        assertTrue("expected an emoji title", names.any { it.contains("🎮") })
    }

    @Test
    fun edge_noteBeyondTheStorageLimitIsClampedExactlyToIt() {
        val note = generate(DevSeedPreset.EDGE).items.mapNotNull { it.entry.userNotes }.single()

        assertTrue("the edge preset exists to exercise the clamp", note.length > LibraryNotes.MAX_CODE_POINTS)
        assertEquals(LibraryNotes.MAX_CODE_POINTS, LibraryNotes.clamp(note).length)
    }

    @Test
    fun coverage_noteSitsExactlyOnTheStorageLimit() {
        val note = generate(DevSeedPreset.COVERAGE).items.first().entry.userNotes

        assertEquals(LibraryNotes.MAX_CODE_POINTS, note?.length)
    }

    @Test
    fun notifications_windowsAreRelativeToTheSuppliedClock() {
        val byName = generate(DevSeedPreset.NOTIFICATIONS).items.associateBy { it.game.name }

        assertEquals(now - SECONDS_PER_DAY, byName.getValue("Notification: released yesterday").game.releaseDateEpochSeconds)
        assertEquals(now, byName.getValue("Notification: releases today").game.releaseDateEpochSeconds)
        assertEquals(
            now + 3 * SECONDS_PER_DAY,
            byName.getValue("Notification: releases in 3 days").game.releaseDateEpochSeconds,
        )
        assertEquals(
            now + 40 * SECONDS_PER_DAY,
            byName.getValue("Notification: releases in 40 days").game.releaseDateEpochSeconds,
        )
        assertNull(byName.getValue("Notification: release date TBA").game.releaseDateEpochSeconds)
        assertTrue(generate(DevSeedPreset.NOTIFICATIONS).items.all { it.entry.releaseNotificationsEnabled })
    }

    @Test
    fun catalogPresets_onlyGenerateOneExtraReservedIdPerCoverageExtra() {
        val catalogIds = catalog.map { it.id }.toSet()

        assertEquals(0, generate(DevSeedPreset.REALISTIC).items.count { it.game.id !in catalogIds })
        assertEquals(2, generate(DevSeedPreset.COVERAGE).items.count { it.game.id !in catalogIds })
    }

    @Test
    fun syntheticPresets_useReservedDisjointIdBlocks() {
        val syntheticIdsByPreset = DevSeedPreset.entries
            .filter { !it.usesCatalog }
            .associateWith { preset -> generate(preset).syntheticItems.map { it.game.id } }

        syntheticIdsByPreset.forEach { (preset, ids) ->
            assertTrue("$preset generated no reserved ids", ids.isNotEmpty())
            assertTrue("$preset left the reserved range", ids.all { it >= SYNTHETIC_ID_BASE })
        }
        val allIds = syntheticIdsByPreset.values.flatten()
        assertEquals("preset id blocks overlap", allIds.size, allIds.distinct().size)
    }

    @Test
    fun clampCount_keepsRequestsInsideTheSupportedRange() {
        assertEquals(DevSeedPreset.MIN_COUNT, DevSeedPreset.clampCount(0))
        assertEquals(DevSeedPreset.MAX_COUNT, DevSeedPreset.clampCount(10_000))
        assertEquals(42, DevSeedPreset.clampCount(42))
    }

    private companion object {
        const val CATALOG_ID_BASE = 1_000L
        const val DEFAULT_TEST_SEED = 7L
    }
}
