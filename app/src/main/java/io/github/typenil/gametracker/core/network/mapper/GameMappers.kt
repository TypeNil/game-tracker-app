package io.github.typenil.gametracker.core.network.mapper

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.network.model.ErrorResponseDto
import io.github.typenil.gametracker.core.network.model.GameDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * Maps a network [GameDto] to a pure domain [Game] model.
 */
fun GameDto.toDomain(): Game {
    return Game(
        id = id,
        name = name,
        coverUrl = coverUrl,
        rating = rating,
        releaseDateEpochSeconds = releaseDateEpochSeconds,
        summary = summary,
        genres = genres,
        platforms = platforms
    )
}

/**
 * Maps a list of [GameDto]s to a list of domain [Game]s.
 */
fun List<GameDto>.toDomain(): List<Game> = map { it.toDomain() }

/**
 * Safely parses the error body and maps HTTP exceptions into typed [AppError.HttpError]
 * without exposing raw transport payloads or allowing body reading exceptions to escape.
 */
fun Throwable.toAppError(): AppError {
    return when (this) {
        is HttpException -> {
            val parsedError = parseErrorResponse()
            AppError.HttpError(
                statusCode = code(),
                errorCode = parsedError?.code,
                message = parsedError?.message
            )
        }
        is IOException -> AppError.NetworkError
        is SerializationException -> AppError.SerializationError(message = message)
        else -> AppError.UnknownError(cause = this)
    }
}

private fun HttpException.parseErrorResponse(): ErrorResponseDto? {
    return try {
        response()
            ?.errorBody()
            ?.string()
            ?.let { json.decodeFromString<ErrorResponseDto>(it) }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
