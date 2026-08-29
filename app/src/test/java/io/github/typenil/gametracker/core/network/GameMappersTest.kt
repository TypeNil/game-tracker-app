package io.github.typenil.gametracker.core.network

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import io.github.typenil.gametracker.core.network.model.CompanyDto
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.ReleaseDateDto
import io.github.typenil.gametracker.core.network.model.SimilarGameDto
import io.github.typenil.gametracker.core.network.model.VideoDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class GameMappersTest {

    @Test
    fun `toDomain correctly maps all fields from GameDto`() {
        val dto = GameDto(
            id = 42L,
            name = "Test Game",
            coverUrl = "file:///android_asset/covers/cover_witcher3.jpg",
            rating = 91.5,
            releaseDateEpochSeconds = 1700000000L,
            summary = "Epic adventure",
            genres = listOf("Action", "RPG"),
            platforms = listOf("PC", "PS5")
        )

        val domain = dto.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Test Game", domain.name)
        assertEquals("file:///android_asset/covers/cover_witcher3.jpg", domain.coverUrl)
        assertEquals(91.5, domain.rating)
        assertEquals(1700000000L, domain.releaseDateEpochSeconds)
        assertEquals("Epic adventure", domain.summary)
        assertEquals(listOf("Action", "RPG"), domain.genres)
        assertEquals(listOf("PC", "PS5"), domain.platforms)
    }

    @Test
    fun `toDomain preserves explicit nulls and empty collections`() {
        val dto = GameDto(
            id = 1L,
            name = "Minimal Game",
            coverUrl = null,
            rating = null,
            releaseDateEpochSeconds = null,
            summary = null,
            genres = emptyList(),
            platforms = emptyList()
        )

        val domain = dto.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("Minimal Game", domain.name)
        assertNull(domain.coverUrl)
        assertNull(domain.rating)
        assertNull(domain.releaseDateEpochSeconds)
        assertNull(domain.summary)
        assertTrue(domain.genres.isEmpty())
        assertTrue(domain.platforms.isEmpty())
    }

    @Test
    fun `toDomain maps enriched GameDetailsDto keeping both rating scales`() {
        val dto = GameDetailsDto(
            id = 1942L,
            name = "The Witcher 3: Wild Hunt",
            coverUrl = "https://example.com/cover.jpg",
            rating = 93.7,
            releaseDateEpochSeconds = 1431993600L,
            summary = "RPG masterpiece",
            genres = listOf("RPG"),
            platforms = listOf("PC"),
            url = "https://www.igdb.com/games/the-witcher-3-wild-hunt",
            totalRating = 92.7,
            totalRatingCount = 5451L,
            themes = listOf("Fantasy"),
            gameModes = listOf("Single player"),
            releaseDates = listOf(ReleaseDateDto(platform = "PC", dateEpochSeconds = 1431993600L, year = 2015)),
            companies = listOf(CompanyDto(name = "CD Projekt RED", isDeveloper = true)),
            screenshots = listOf("https://example.com/shot.jpg"),
            videos = listOf(VideoDto(videoId = "abc123", name = "Trailer")),
            similarGames = listOf(
                SimilarGameDto(
                    id = 25076L,
                    name = "Red Dead Redemption 2",
                    totalRating = 93.6,
                    genres = listOf("Shooter"),
                    platforms = listOf("PC"),
                ),
            )
        )

        val domain = dto.toDomain()

        assertEquals(1942L, domain.id)
        assertEquals(93.7, domain.rating!!, 0.001)
        assertEquals(92.7, domain.totalRating!!, 0.001)
        assertEquals(5451L, domain.totalRatingCount)
        assertEquals("https://www.igdb.com/games/the-witcher-3-wild-hunt", domain.url)
        assertEquals(listOf("Fantasy"), domain.themes)
        assertEquals(listOf("Single player"), domain.gameModes)
        assertEquals("PC", domain.releaseDates.single().platform)
        assertEquals(1431993600L, domain.releaseDates.single().dateEpochSeconds)
        assertEquals("CD Projekt RED", domain.companies.single().name)
        assertTrue(domain.companies.single().isDeveloper)
        assertEquals("abc123", domain.videos.single().videoId)
        assertEquals(25076L, domain.similarGames.single().id)
        assertEquals(93.6, domain.similarGames.single().totalRating!!, 0.001)
        assertEquals(listOf("Shooter"), domain.similarGames.single().genres)
        assertEquals(listOf("PC"), domain.similarGames.single().platforms)
    }

    @Test
    fun `toDomain maps sparse GameDetailsDto into defaults`() {
        val dto = GameDetailsDto(id = 7L, name = "Sparse Game")

        val domain = dto.toDomain()

        assertEquals(7L, domain.id)
        assertNull(domain.totalRating)
        assertNull(domain.totalRatingCount)
        assertNull(domain.url)
        assertTrue(domain.themes.isEmpty())
        assertTrue(domain.releaseDates.isEmpty())
        assertTrue(domain.companies.isEmpty())
        assertTrue(domain.screenshots.isEmpty())
        assertTrue(domain.videos.isEmpty())
        assertTrue(domain.similarGames.isEmpty())
    }

    @Test
    fun `toAppError maps IOException to NetworkError`() {
        val exception = IOException("Connection reset")
        val error = exception.toAppError()

        assertEquals(AppError.NetworkError, error)
    }

    @Test
    fun `toAppError safely parses structured ErrorResponseDto JSON`() {
        val jsonPayload = """{"code":"RATE_LIMIT_EXCEEDED","message":"Rate limit reached","timestamp":1700000000}"""
        val responseBody = jsonPayload.toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<String>(429, responseBody))

        val error = httpException.toAppError()

        assertTrue(error is AppError.HttpError)
        val httpError = error as AppError.HttpError
        assertEquals(429, httpError.statusCode)
        assertEquals("RATE_LIMIT_EXCEEDED", httpError.errorCode)
        assertEquals("Rate limit reached", httpError.message)
    }

    @Test
    fun `toAppError handles unparseable error body gracefully without crashing`() {
        val malformedPayload = "<html>502 Bad Gateway</html>"
        val responseBody = malformedPayload.toResponseBody("text/html".toMediaType())
        val httpException = HttpException(Response.error<String>(502, responseBody))

        val error = httpException.toAppError()

        assertTrue(error is AppError.HttpError)
        val httpError = error as AppError.HttpError
        assertEquals(502, httpError.statusCode)
        assertNull(httpError.errorCode)
    }

    @Test
    fun `toAppError handles error body reading IOException gracefully without escaping`() {
        val throwingBody = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = 100L
            override fun source(): BufferedSource {
                throw IOException("Broken stream during read")
            }
        }
        val httpException = HttpException(Response.error<String>(500, throwingBody))

        val error = httpException.toAppError()

        assertTrue(error is AppError.HttpError)
        val httpError = error as AppError.HttpError
        assertEquals(500, httpError.statusCode)
        assertNull(httpError.errorCode)
    }

    @Test
    fun `toAppError maps SerializationException to SerializationError`() {
        val exception = SerializationException("Malformed JSON")
        val error = exception.toAppError()

        assertTrue(error is AppError.SerializationError)
        assertEquals("Malformed JSON", (error as AppError.SerializationError).message)
    }

    @Test
    fun `toAppError maps generic throwable to UnknownError`() {
        val exception = IllegalStateException("Something bad")
        val error = exception.toAppError()

        assertTrue(error is AppError.UnknownError)
        assertEquals(exception, (error as AppError.UnknownError).cause)
    }

    @Test
    fun toDomain_mapsRecommendationCandidateDto() {
        val dto = RecommendationCandidateDto(
            id = 99L,
            name = "Similar",
            coverUrl = "https://example.com/c.jpg",
            rating = 88.0,
            ratingCount = 12,
            releaseDateEpochSeconds = 1431993600,
            summary = "ok",
            genres = listOf("RPG"),
            themes = listOf("Fantasy"),
            platforms = listOf("PC"),
            similarToGameIds = listOf(1942L),
        )
        val domain = dto.toDomain()
        assertEquals(99L, domain.gameId)
        assertEquals("Similar", domain.name)
        assertEquals(12L, domain.ratingCount)
        assertEquals(listOf("Fantasy"), domain.themes)
        assertEquals(listOf(1942L), domain.similarToGameIds)
    }
}
