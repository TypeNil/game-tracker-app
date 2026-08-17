package io.github.typenil.gametracker.core.network

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.network.mapper.toAppError
import io.github.typenil.gametracker.core.network.mapper.toDomain
import io.github.typenil.gametracker.core.network.model.GameDto
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
            coverUrl = "https://example.com/cover.jpg",
            rating = 91.5,
            releaseDateEpochSeconds = 1700000000L,
            summary = "Epic adventure",
            genres = listOf("Action", "RPG"),
            platforms = listOf("PC", "PS5")
        )

        val domain = dto.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Test Game", domain.name)
        assertEquals("https://example.com/cover.jpg", domain.coverUrl)
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
    fun `toAppError maps HttpException to HttpError with statusCode`() {
        val responseBody = "Rate limit exceeded".toResponseBody("text/plain".toMediaType())
        val httpException = HttpException(Response.error<String>(429, responseBody))

        val error = httpException.toAppError()

        assertTrue(error is AppError.HttpError)
        assertEquals(429, (error as AppError.HttpError).statusCode)
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
        assertEquals(exception, (error as AppError.UnknownError).throwable)
    }
}
