package io.github.typenil.gametracker.core.network

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import io.github.typenil.gametracker.core.network.model.GameDto
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
            coverUrl = "file:///android_asset/covers/cover_witcher3.png",
            rating = 91.5,
            releaseDateEpochSeconds = 1700000000L,
            summary = "Epic adventure",
            genres = listOf("Action", "RPG"),
            platforms = listOf("PC", "PS5")
        )

        val domain = dto.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Test Game", domain.name)
        assertEquals("file:///android_asset/covers/cover_witcher3.png", domain.coverUrl)
        assertEquals(91.5, domain.rating)
        assertEquals(1700000000L, domain.releaseDateEpochSeconds)
        assertEquals("Epic adventure", domain.summary)
        assertEquals(listOf("Action", "RPG"), domain.genres)
        assertEquals(listOf("PC", "PS5"), domain.platforms)
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
}
