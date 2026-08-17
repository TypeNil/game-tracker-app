package io.github.typenil.gametracker.core.network.mapper

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.network.model.GameDto
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

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
 * Maps raw thrown network/HTTP exceptions into typed [AppError] domain failures.
 */
fun Throwable.toAppError(): AppError {
    return when (this) {
        is HttpException -> AppError.HttpError(
            statusCode = code(),
            message = response()?.errorBody()?.string() ?: message()
        )
        is IOException -> AppError.NetworkError
        is SerializationException -> AppError.SerializationError(message = message)
        else -> AppError.UnknownError(throwable = this)
    }
}
