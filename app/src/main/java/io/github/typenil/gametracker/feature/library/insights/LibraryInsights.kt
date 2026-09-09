package io.github.typenil.gametracker.feature.library.insights

import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.designsystem.component.resolvePlatformFamilies
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import java.util.Locale

private const val MOST_PLAYED_LIMIT = 5
private const val TOP_TASTE_LIMIT = 3

data class MostPlayedGame(
    val gameId: Long,
    val name: String,
    val hoursPlayed: Int,
)

data class LibraryInsights(
    val totalGames: Int,
    val playingCount: Int,
    val completedCount: Int,
    val wishlistCount: Int,
    val droppedCount: Int,
    val favoritesCount: Int,
    val totalHours: Long,
    val averageUserRating: Double?,
    val completionRate: Double?,
    val mostPlayed: List<MostPlayedGame>,
    val topGenres: List<String>,
    val topPlatforms: List<PlatformFamily>,
)

fun computeLibraryInsights(games: List<LibraryGame>): LibraryInsights {
    val population = games.filter { it.entry.status != LibraryStatus.NOT_INTERESTED }
    val playingCount = population.count { it.entry.status == LibraryStatus.PLAYING }
    val completedCount = population.count { it.entry.status == LibraryStatus.COMPLETED }
    val wishlistCount = population.count { it.entry.status == LibraryStatus.WISHLIST }
    val droppedCount = population.count { it.entry.status == LibraryStatus.DROPPED }
    val startedCount = playingCount + completedCount + droppedCount
    val ratings = population.mapNotNull { it.entry.userRating }

    return LibraryInsights(
        totalGames = population.size,
        playingCount = playingCount,
        completedCount = completedCount,
        wishlistCount = wishlistCount,
        droppedCount = droppedCount,
        favoritesCount = population.count { it.entry.isFavorite },
        totalHours = population.sumOf { it.entry.hoursPlayed.toLong() },
        averageUserRating = if (ratings.isEmpty()) {
            null
        } else {
            ratings.sum().toDouble() / ratings.size
        },
        completionRate = if (startedCount == 0) {
            null
        } else {
            completedCount.toDouble() / startedCount
        },
        mostPlayed = population
            .filter { it.entry.hoursPlayed > 0 }
            .sortedWith(
                compareByDescending<LibraryGame> { it.entry.hoursPlayed }
                    .thenBy { it.game.id },
            )
            .take(MOST_PLAYED_LIMIT)
            .map { libraryGame ->
                MostPlayedGame(
                    gameId = libraryGame.game.id,
                    name = libraryGame.game.name,
                    hoursPlayed = libraryGame.entry.hoursPlayed,
                )
            },
        topGenres = topGenres(population),
        topPlatforms = topPlatforms(population),
    )
}

private fun topGenres(population: List<LibraryGame>): List<String> =
    population.asSequence()
        .flatMap { libraryGame ->
            libraryGame.game.genres.asSequence()
                .map(::formatGenreTag)
                .filter(String::isNotBlank)
                .distinct()
        }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.lowercase(Locale.ROOT) },
        )
        .take(TOP_TASTE_LIMIT)
        .map { it.key }

private fun topPlatforms(population: List<LibraryGame>): List<PlatformFamily> =
    population.asSequence()
        .flatMap { resolvePlatformFamilies(it.game.platforms).asSequence() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<PlatformFamily, Int>> { it.value }
                .thenBy { it.key.ordinal },
        )
        .take(TOP_TASTE_LIMIT)
        .map { it.key }
