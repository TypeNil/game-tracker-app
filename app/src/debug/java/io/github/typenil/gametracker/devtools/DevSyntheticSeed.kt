package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryBackupItem
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlin.random.Random

/**
 * Presets generated entirely in code. Reserved ids cannot be resolved by any remote source, which is
 * what makes this the only way to reach data the catalog cannot express — a 200-character title,
 * right-to-left script, a null release date, or several hundred rows of write volume.
 */
internal object DevSyntheticSeed {

    /** Two entries that make the "notify me about this game" intent reachable from a fresh install. */
    fun coverageExtras(now: Long): List<LibraryBackupItem> = listOf(
        seedItem(
            game = syntheticGame(DevSeedPreset.COVERAGE, index = 0, name = "Coverage: TBA, subscribed", releaseDate = null),
            status = LibraryStatus.WISHLIST,
            addedAtEpochSeconds = now - SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - SECONDS_PER_DAY,
            notifyOnRelease = true,
        ),
        // Deliberately subscribed *and* already released: the worker's stop-tracking path.
        seedItem(
            game = syntheticGame(
                DevSeedPreset.COVERAGE,
                index = 1,
                name = "Coverage: released, still subscribed",
                releaseDate = now - 3 * SECONDS_PER_DAY,
            ),
            status = LibraryStatus.COMPLETED,
            addedAtEpochSeconds = now - 2 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 2 * SECONDS_PER_DAY,
            rating = MIN_RATING,
            notifyOnRelease = true,
        ),
    )

    fun edge(now: Long): List<LibraryBackupItem> = listOf(
        // Maximum title length: single-line truncation in every card, row and header.
        edgeItem(0, longTitle, now, LibraryStatus.PLAYING, rating = 8, hours = 42, favorite = true),
        // CJK: wide glyphs, no word breaks.
        edgeItem(1, "エッジケース：日本語のタイトル", now, LibraryStatus.COMPLETED, rating = 9, hours = 120),
        // Right-to-left script inside a left-to-right layout.
        edgeItem(2, "מקרה קצה בעברית", now, LibraryStatus.WISHLIST),
        // Emoji: surrogate pairs, so code-point handling differs from char handling.
        edgeItem(3, "Edge 🎮 Case 🕹️ Emoji", now, LibraryStatus.DROPPED, rating = 3, hours = 5),
        // Two distinct games sharing a title: list keys must come from the id, not the name.
        edgeItem(4, "Doom", now, LibraryStatus.COMPLETED, rating = MAX_RATING, hours = 25),
        edgeItem(5, "Doom", now, LibraryStatus.PLAYING, rating = 7, hours = 3),
        // No artwork, no summary, no release date, no rating, and a note past the storage limit.
        seedItem(
            game = syntheticGame(DevSeedPreset.EDGE, index = 6, name = "Edge case: bare entry"),
            status = LibraryStatus.NOT_INTERESTED,
            addedAtEpochSeconds = now - 7 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 7 * SECONDS_PER_DAY,
            notes = overLimitNote,
        ),
    )

    fun stress(count: Int, random: Random, now: Long): List<LibraryBackupItem> {
        val statuses = LibraryStatus.entries
        return List(count) { index ->
            val status = statuses[index % statuses.size]
            seedItem(
                game = syntheticGame(DevSeedPreset.STRESS, index = index, name = "Stress ${index + 1}"),
                status = status,
                rating = random.nextInt(MIN_RATING, MAX_RATING + 1),
                hours = if (status.supportsHours) random.nextInt(0, STRESS_MAX_HOURS) else 0,
                favorite = index % STRESS_FAVORITE_EVERY == 0,
                addedAtEpochSeconds = now - (index + 1) * SECONDS_PER_DAY,
                updatedAtEpochSeconds = now - index * SECONDS_PER_DAY,
            )
        }
    }

    /** One entry per release-check window the worker distinguishes, all subscribed. */
    fun notifications(now: Long): List<LibraryBackupItem> {
        val windows: List<Pair<String, Long?>> = listOf(
            "released yesterday" to now - SECONDS_PER_DAY,
            "releases today" to now,
            "releases in 3 days" to now + 3 * SECONDS_PER_DAY,
            "releases in 40 days" to now + 40 * SECONDS_PER_DAY,
            "release date TBA" to null,
        )
        return windows.mapIndexed { index, (label, releaseDate) ->
            seedItem(
                game = syntheticGame(
                    DevSeedPreset.NOTIFICATIONS,
                    index = index,
                    name = "Notification: $label",
                    releaseDate = releaseDate,
                ),
                status = if (index == 0) LibraryStatus.COMPLETED else LibraryStatus.WISHLIST,
                addedAtEpochSeconds = now - (index + 1) * SECONDS_PER_DAY,
                updatedAtEpochSeconds = now - index * SECONDS_PER_DAY,
                notifyOnRelease = true,
            )
        }
    }

    private fun edgeItem(
        index: Int,
        name: String,
        now: Long,
        status: LibraryStatus,
        rating: Int? = null,
        hours: Int = 0,
        favorite: Boolean = false,
    ): LibraryBackupItem = seedItem(
        game = syntheticGame(DevSeedPreset.EDGE, index = index, name = name),
        status = status,
        rating = rating,
        hours = hours,
        favorite = favorite,
        addedAtEpochSeconds = now - (index + 1) * SECONDS_PER_DAY,
        updatedAtEpochSeconds = now - index * SECONDS_PER_DAY,
    )

    private fun syntheticGame(
        preset: DevSeedPreset,
        index: Int,
        name: String,
        releaseDate: Long? = null,
    ): Game = Game(
        id = syntheticId(preset, index),
        name = name,
        releaseDateEpochSeconds = releaseDate,
        // Non-empty tags keep Insights aggregates and filter chips non-degenerate.
        genres = listOf(DEV_TOOLS_GENRE),
        platforms = listOf(DEV_TOOLS_PLATFORM),
    )

    private const val DEV_TOOLS_GENRE = "Dev tools"
    private const val DEV_TOOLS_PLATFORM = "Android"
    private const val STRESS_FAVORITE_EVERY = 7
    private const val STRESS_MAX_HOURS = 600

    /** "Edge case " is exactly ten characters, so twenty repetitions are exactly the 200-character cap. */
    private val longTitle: String = "Edge case ".repeat(20)
}
