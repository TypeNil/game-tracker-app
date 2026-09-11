package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryBackupItem
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlin.random.Random

/**
 * Presets built from the app's own catalog source. Every game keeps a real, resolvable id, so the
 * Details screen loads these through the normal network-plus-cache path — in the demo flavor from
 * the packaged fixtures, in the live flavor from the BFF.
 */
internal object DevCatalogSeed {

    fun realistic(catalog: List<Game>, count: Int, random: Random, now: Long): List<LibraryBackupItem> =
        catalog.take(count).mapIndexed { index, game ->
            val status = realisticCycle[index % realisticCycle.size]
            seedItem(
                game = game,
                status = status,
                rating = realisticRating(status, random),
                hours = realisticHours(status, random),
                favorite = index % FAVORITE_EVERY == 0,
                notes = if (index == 0) maxLengthNote else null,
                notifyOnRelease = isUnreleased(game, now),
                addedAtEpochSeconds = now - (index + 1) * REALISTIC_SPREAD_SECONDS,
                updatedAtEpochSeconds = now - index * REALISTIC_SPREAD_SECONDS,
            )
        }

    /**
     * Guarantees the things that are tedious to reach by tapping: every [LibraryStatus], both ends of
     * the rating and hours ranges, both favorite states, a note on the storage boundary, and the
     * notification intent on both a TBA and an already-released game.
     *
     * The catalog part never drops below [MIN_COVERAGE_CATALOG] entries, so a small `count` still
     * delivers the coverage the preset promises; the result reports how many entries were delivered.
     */
    fun coverage(catalog: List<Game>, count: Int, random: Random, now: Long): List<LibraryBackupItem> {
        val catalogCount = (count - COVERAGE_EXTRAS).coerceAtLeast(MIN_COVERAGE_CATALOG)
        val statuses = LibraryStatus.entries
        val catalogItems = catalog.take(catalogCount).mapIndexed { index, game ->
            seedItem(
                game = game,
                status = statuses[index % statuses.size],
                rating = if (index % 2 == 0) MAX_RATING else MIN_RATING,
                hours = when (index) {
                    0 -> 0
                    1 -> MAX_HOURS
                    else -> random.nextInt(0, COVERAGE_MAX_HOURS)
                },
                favorite = index % 2 == 0,
                notes = if (index == 0) maxLengthNote else null,
                addedAtEpochSeconds = now - (index + 1) * SECONDS_PER_DAY,
                updatedAtEpochSeconds = now - index * SECONDS_PER_DAY,
            )
        }
        return catalogItems + DevSyntheticSeed.coverageExtras(now)
    }

    private fun realisticRating(status: LibraryStatus, random: Random): Int? = when (status) {
        LibraryStatus.COMPLETED, LibraryStatus.PLAYING -> random.nextInt(REALISTIC_GOOD_RATING_MIN, MAX_RATING + 1)
        LibraryStatus.DROPPED -> random.nextInt(MIN_RATING, REALISTIC_GOOD_RATING_MIN)
        LibraryStatus.WISHLIST, LibraryStatus.NOT_INTERESTED -> null
    }

    private fun realisticHours(status: LibraryStatus, random: Random): Int = when (status) {
        LibraryStatus.COMPLETED -> random.nextInt(MIN_GOOD_HOURS, REALISTIC_COMPLETED_HOURS_MAX)
        LibraryStatus.PLAYING -> random.nextInt(MIN_RATING, REALISTIC_PLAYING_HOURS_MAX)
        LibraryStatus.DROPPED -> random.nextInt(0, REALISTIC_DROPPED_HOURS_MAX)
        LibraryStatus.WISHLIST, LibraryStatus.NOT_INTERESTED -> 0
    }

    private fun isUnreleased(game: Game, now: Long): Boolean {
        val releaseDate = game.releaseDateEpochSeconds
        return releaseDate == null || releaseDate > now
    }

    /** Runnable order matters for the library list, so the mix is fixed rather than random. */
    private val realisticCycle = listOf(
        LibraryStatus.COMPLETED,
        LibraryStatus.PLAYING,
        LibraryStatus.WISHLIST,
        LibraryStatus.COMPLETED,
        LibraryStatus.DROPPED,
        LibraryStatus.PLAYING,
        LibraryStatus.WISHLIST,
        LibraryStatus.COMPLETED,
        LibraryStatus.NOT_INTERESTED,
        LibraryStatus.PLAYING,
    )

    private const val FAVORITE_EVERY = 5
    private const val MIN_GOOD_HOURS = 10
    private const val REALISTIC_GOOD_RATING_MIN = 6
    private const val REALISTIC_COMPLETED_HOURS_MAX = 120
    private const val REALISTIC_PLAYING_HOURS_MAX = 60
    private const val REALISTIC_DROPPED_HOURS_MAX = 10
    private const val COVERAGE_MAX_HOURS = 500
    private const val COVERAGE_EXTRAS = 2
    private const val MIN_COVERAGE_CATALOG = 5
    private const val REALISTIC_SPREAD_SECONDS = 3 * SECONDS_PER_DAY
}
