package io.github.typenil.gametracker.core.model

/**
 * Strongly-typed error domain model for handling system, network, and HTTP failures.
 */
sealed interface AppError {
    /**
     * Network connectivity failure (e.g. no internet connection, DNS resolution failure, socket timeout).
     */
    data object NetworkError : AppError

    /**
     * HTTP response error with a status code (e.g. 404 Not Found, 429 Rate Limit, 500 Internal Server Error).
     */
    data class HttpError(
        val statusCode: Int,
        val message: String? = null
    ) : AppError

    /**
     * Data serialization or parsing failure.
     */
    data class SerializationError(
        val message: String? = null
    ) : AppError

    /**
     * Unexpected runtime error.
     */
    data class UnknownError(
        val throwable: Throwable? = null
    ) : AppError
}
