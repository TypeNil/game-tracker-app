package io.github.typenil.gametracker.core.database.mapper

import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.CompanyColumn
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameCompany
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityMappersTest {

    @Test
    fun `Game toEntity maps all fields correctly`() {
        val domainGame = Game(
            id = 42L,
            name = "Elden Ring",
            coverUrl = "https://example.com/cover.jpg",
            rating = 96.5,
            releaseDateEpochSeconds = 1645747200L,
            summary = "Epic action RPG",
            genres = listOf("RPG", "Action"),
            platforms = listOf("PC", "PS5")
        )

        val entity = domainGame.toEntity(cachedAtEpochSeconds = 1700000000L)

        assertEquals(42L, entity.id)
        assertEquals("Elden Ring", entity.name)
        assertEquals("https://example.com/cover.jpg", entity.coverUrl)
        assertEquals(96.5, entity.rating)
        assertEquals(1645747200L, entity.releaseDateEpochSeconds)
        assertEquals("Epic action RPG", entity.summary)
        assertEquals(listOf("RPG", "Action"), entity.genres)
        assertEquals(listOf("PC", "PS5"), entity.platforms)
        assertEquals(1700000000L, entity.cachedAtEpochSeconds)
    }

    @Test
    fun `GameEntity toDomain maps all fields correctly`() {
        val entity = GameEntity(
            id = 42L,
            name = "Elden Ring",
            coverUrl = "https://example.com/cover.jpg",
            rating = 96.5,
            releaseDateEpochSeconds = 1645747200L,
            summary = "Epic action RPG",
            genres = listOf("RPG", "Action"),
            platforms = listOf("PC", "PS5"),
            cachedAtEpochSeconds = 1700000000L
        )

        val domainGame = entity.toDomain()

        assertEquals(42L, domainGame.id)
        assertEquals("Elden Ring", domainGame.name)
        assertEquals("https://example.com/cover.jpg", domainGame.coverUrl)
        assertEquals(96.5, domainGame.rating)
        assertEquals(1645747200L, domainGame.releaseDateEpochSeconds)
        assertEquals("Epic action RPG", domainGame.summary)
        assertEquals(listOf("RPG", "Action"), domainGame.genres)
        assertEquals(listOf("PC", "PS5"), domainGame.platforms)
    }

    @Test
    fun `List of GameEntity toDomain maps all elements`() {
        val entities = listOf(
            GameEntity(
                id = 1L,
                name = "Game 1",
                coverUrl = null,
                rating = null,
                releaseDateEpochSeconds = null,
                summary = null,
                genres = emptyList(),
                platforms = emptyList(),
                cachedAtEpochSeconds = 1000L
            )
        )

        val domainList = entities.toDomain()

        assertEquals(1, domainList.size)
        assertEquals(1L, domainList[0].id)
        assertEquals("Game 1", domainList[0].name)
    }

    @Test
    fun `LibraryEntry toEntity and toDomain round-trip maps correctly`() {
        val entry = LibraryEntry(
            gameId = 101L,
            status = LibraryStatus.PLAYING,
            userRating = 9,
            userNotes = "Great game so far",
            isFavorite = true,
            addedAtEpochSeconds = 1600000000L,
            updatedAtEpochSeconds = 1600001000L,
            hoursPlayed = 55
        )

        val entity = entry.toEntity()
        assertEquals(101L, entity.gameId)
        assertEquals(LibraryStatus.PLAYING, entity.status)
        assertEquals(9, entity.userRating)
        assertEquals("Great game so far", entity.userNotes)
        assertEquals(true, entity.isFavorite)
        assertEquals(55, entity.hoursPlayed)

        val mappedBack = entity.toDomain()
        assertEquals(entry, mappedBack)
        assertEquals(55, mappedBack.hoursPlayed)
    }

    @Test
    fun `GameDetails toEntity and toDomain round-trip keeps both rating scales and nested lists`() {
        val details = GameDetails(
            id = 1942L,
            name = "The Witcher 3: Wild Hunt",
            coverUrl = "https://example.com/cover.jpg",
            rating = 93.7,
            totalRating = 92.7,
            totalRatingCount = 5451L,
            releaseDateEpochSeconds = 1431993600L,
            summary = "RPG masterpiece",
            genres = listOf("RPG"),
            themes = listOf("Fantasy"),
            gameModes = listOf("Single player"),
            platforms = listOf("PC"),
            releaseDates = listOf(GameReleaseDate(platform = "PC", dateEpochSeconds = 1431993600L, year = 2015)),
            companies = listOf(GameCompany(name = "CD Projekt RED", isDeveloper = true)),
            screenshots = listOf("https://example.com/shot1.jpg"),
            videos = listOf(GameVideo(videoId = "abc123", name = "Trailer")),
            similarGames = listOf(GameSummary(id = 25076L, name = "Red Dead Redemption 2", totalRating = 93.6)),
            url = "https://www.igdb.com/games/the-witcher-3-wild-hunt"
        )

        val entity = details.toEntity(cachedAtEpochSeconds = 1700000000L)
        assertEquals(1942L, entity.gameId)
        assertEquals(93.7, entity.rating!!, 0.001)
        assertEquals(92.7, entity.totalRating!!, 0.001)
        assertEquals(5451L, entity.totalRatingCount)
        assertEquals("CD Projekt RED", entity.companies.single().name)
        assertTrue(entity.companies.single().isDeveloper)
        assertEquals(1700000000L, entity.cachedAtEpochSeconds)

        val mappedBack = entity.toDomain()
        assertEquals(details, mappedBack)
    }

    @Test
    fun `developerName prefers isDeveloper then first named company`() {
        assertEquals(
            "CD Projekt RED",
            listOf(
                CompanyColumn(name = "Warner", isPublisher = true),
                CompanyColumn(name = "CD Projekt RED", isDeveloper = true),
            ).developerName(),
        )
        assertEquals(
            "Solo Studio",
            listOf(CompanyColumn(name = "Solo Studio")).developerName(),
        )
        assertEquals(null, emptyList<CompanyColumn>().developerName())
        assertEquals(null, (null as List<CompanyColumn>?).developerName())
    }
}
