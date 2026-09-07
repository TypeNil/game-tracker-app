package com.gametracker.backend.error

sealed class UpstreamException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Upstream API quota or request limit exceeded (HTTP 429).
 * Carries a guaranteed-validated [retryAfterSeconds] value to pass to the client.
 */
class UpstreamRateLimitException(
    val retryAfterSeconds: Long
) : UpstreamException("Upstream rate limit reached. Retry after $retryAfterSeconds seconds.")

/**
 * Upstream gateway error: malformed response format, serialization failure, or upstream 502/4xx (HTTP 502).
 */
class UpstreamBadGatewayException(
    message: String,
    cause: Throwable? = null
) : UpstreamException(message, cause)

/**
 * Upstream service temporarily unavailable or network connection failure (HTTP 503).
 */
class UpstreamServiceUnavailableException(
    message: String,
    cause: Throwable? = null
) : UpstreamException(message, cause)

/**
 * Timed out waiting for the upstream service to connect or respond (HTTP 504).
 */
class UpstreamTimeoutException(
    message: String,
    cause: Throwable? = null
) : UpstreamException(message, cause)
