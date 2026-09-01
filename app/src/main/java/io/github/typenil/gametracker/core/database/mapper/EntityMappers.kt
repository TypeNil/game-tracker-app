package io.github.typenil.gametracker.core.database.mapper

import io.github.typenil.gametracker.core.database.entity.CompanyColumn
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.PopulatedLibraryGameEntity
import io.github.typenil.gametracker.core.database.entity.ReleaseDateColumn
import io.github.typenil.gametracker.core.database.entity.SimilarGameColumn
import io.github.typenil.gametracker.core.database.entity.VideoColumn
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameCompany
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame

/**
 * Maps a domain [Game] model to a Room [GameEntity].
 */
fun Game.toEntity(
    cachedAtEpochSeconds: Long = System.currentTimeMillis() / 1000
): GameEntity {
    return GameEntity(
        id = this.id,
        name = this.name,
        coverUrl = this.coverUrl,
        rating = this.rating,
        releaseDateEpochSeconds = this.releaseDateEpochSeconds,
        summary = this.summary,
        genres = this.genres,
        platforms = this.platforms,
        cachedAtEpochSeconds = cachedAtEpochSeconds
    )
}

/**
 * Maps a Room [GameEntity] to a pure domain [Game] model.
 */
fun GameEntity.toDomain(): Game {
    return Game(
        id = this.id,
        name = this.name,
        coverUrl = this.coverUrl,
        rating = this.rating,
        releaseDateEpochSeconds = this.releaseDateEpochSeconds,
        summary = this.summary,
        genres = this.genres,
        platforms = this.platforms
    )
}

/**
 * Maps a list of [GameEntity] instances to a list of domain [Game] models.
 */
fun List<GameEntity>.toDomain(): List<Game> {
    return this.map { it.toDomain() }
}

/**
 * Maps a domain [GameDetails] model to a Room [GameDetailsEntity].
 */
fun GameDetails.toEntity(
    cachedAtEpochSeconds: Long = System.currentTimeMillis() / 1000
): GameDetailsEntity {
    return GameDetailsEntity(
        gameId = this.id,
        name = this.name,
        coverUrl = this.coverUrl,
        rating = this.rating,
        totalRating = this.totalRating,
        totalRatingCount = this.totalRatingCount,
        releaseDateEpochSeconds = this.releaseDateEpochSeconds,
        summary = this.summary,
        url = this.url,
        genres = this.genres,
        themes = this.themes,
        gameModes = this.gameModes,
        platforms = this.platforms,
        releaseDates = this.releaseDates.map {
            ReleaseDateColumn(platform = it.platform, dateEpochSeconds = it.dateEpochSeconds, year = it.year)
        },
        companies = this.companies.map {
            CompanyColumn(name = it.name, isDeveloper = it.isDeveloper, isPublisher = it.isPublisher)
        },
        screenshots = this.screenshots,
        videos = this.videos.map { VideoColumn(videoId = it.videoId, name = it.name) },
        similarGames = this.similarGames.map {
            SimilarGameColumn(
                id = it.id,
                name = it.name,
                coverUrl = it.coverUrl,
                totalRating = it.totalRating,
                genres = it.genres,
                platforms = it.platforms,
            )
        },
        cachedAtEpochSeconds = cachedAtEpochSeconds,
        artworkUrl = this.artworkUrl,
        timeToBeatMainSeconds = this.timeToBeatMainSeconds,
        timeToBeatCompleteSeconds = this.timeToBeatCompleteSeconds
    )
}

/**
 * Maps a Room [GameDetailsEntity] to a pure domain [GameDetails] model.
 */
fun GameDetailsEntity.toDomain(): GameDetails {
    return GameDetails(
        id = this.gameId,
        name = this.name,
        coverUrl = this.coverUrl,
        rating = this.rating,
        totalRating = this.totalRating,
        totalRatingCount = this.totalRatingCount,
        releaseDateEpochSeconds = this.releaseDateEpochSeconds,
        summary = this.summary,
        genres = this.genres,
        themes = this.themes,
        gameModes = this.gameModes,
        platforms = this.platforms,
        releaseDates = this.releaseDates.map {
            GameReleaseDate(platform = it.platform, dateEpochSeconds = it.dateEpochSeconds, year = it.year)
        },
        companies = this.companies.map {
            GameCompany(name = it.name, isDeveloper = it.isDeveloper, isPublisher = it.isPublisher)
        },
        screenshots = this.screenshots,
        videos = this.videos.map { GameVideo(videoId = it.videoId, name = it.name) },
        similarGames = this.similarGames.map {
            GameSummary(
                id = it.id,
                name = it.name,
                coverUrl = it.coverUrl,
                totalRating = it.totalRating,
                genres = it.genres,
                platforms = it.platforms,
            )
        },
        url = this.url,
        artworkUrl = this.artworkUrl,
        timeToBeatMainSeconds = this.timeToBeatMainSeconds,
        timeToBeatCompleteSeconds = this.timeToBeatCompleteSeconds
    )
}

/**
 * Maps a domain [LibraryEntry] to a Room [LibraryEntryEntity].
 */
fun LibraryEntry.toEntity(): LibraryEntryEntity {
    return LibraryEntryEntity(
        gameId = this.gameId,
        status = this.status,
        userRating = this.userRating,
        userNotes = this.userNotes,
        isFavorite = this.isFavorite,
        addedAtEpochSeconds = this.addedAtEpochSeconds,
        updatedAtEpochSeconds = this.updatedAtEpochSeconds,
        hoursPlayed = this.hoursPlayed
    )
}

/**
 * Maps a Room [LibraryEntryEntity] to a pure domain [LibraryEntry] model.
 */
fun LibraryEntryEntity.toDomain(): LibraryEntry {
    return LibraryEntry(
        gameId = this.gameId,
        status = this.status,
        userRating = this.userRating,
        userNotes = this.userNotes,
        isFavorite = this.isFavorite,
        addedAtEpochSeconds = this.addedAtEpochSeconds,
        updatedAtEpochSeconds = this.updatedAtEpochSeconds,
        hoursPlayed = this.hoursPlayed
    )
}

/**
 * Maps a Room [PopulatedLibraryGameEntity] to a domain [LibraryGame].
 */
fun PopulatedLibraryGameEntity.toDomain(): LibraryGame {
    val detailsRow = details.firstOrNull()
    return LibraryGame(
        game = this.game.toDomain(),
        entry = this.entry.toDomain(),
        developerName = detailsRow?.companies.developerName(),
        bannerUrl = detailsRow?.screenshots?.firstOrNull { it.isNotBlank() },
    )
}

internal fun List<CompanyColumn>?.developerName(): String? {
    val named = this.orEmpty().filter { it.name.isNotBlank() }
    return named.firstOrNull { it.isDeveloper }?.name ?: named.firstOrNull()?.name
}
