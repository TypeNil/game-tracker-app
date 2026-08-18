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
     * HTTP response error with a status code and structured error code from BFF.
     */
    data class HttpError(
        val statusCode: Int,
        val errorCode: String? = null,
        val message: String? = null
    ) : AppError

    /**
     * Data serialization or parsing failure.
     */
    data class SerializationError(
        val message: String? = null
    ) : AppError

    /**
     * Unexpected runtime exception.
     */
    data class UnknownError(
        val cause: Throwable? = null
    ) : AppError
}
