package io.github.typenil.gametracker.backend.error

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

fun Application.configureErrorHandling() {
    install(StatusPages) {
        configureUpstreamExceptionHandlers()
        configureClientExceptionHandlers()
        configureDirectStatusHandlers()
        configureFallbackExceptionHandler()
    }
}

private fun StatusPagesConfig.configureUpstreamExceptionHandlers() {
    exception<UpstreamRateLimitException> { call, cause ->
        logger.warn("Upstream rate limit reached on {}: {}", loggedRequestPath(call.request.local.uri), cause.message)
        call.response.header(HttpHeaders.RetryAfter, cause.retryAfterSeconds.toString())
        call.respond(
            status = HttpStatusCode.TooManyRequests,
            message = ErrorResponse(
                code = "RATE_LIMIT_EXCEEDED",
                message = "Rate limit exceeded. Please retry later."
            )
        )
    }

    exception<UpstreamBadGatewayException> { call, cause ->
        logger.error("Upstream bad gateway on {}: {}", loggedRequestPath(call.request.local.uri), cause.message, cause)
        call.respond(
            status = HttpStatusCode.BadGateway,
            message = ErrorResponse(code = "BAD_GATEWAY", message = "Upstream service error.")
        )
    }

    exception<UpstreamServiceUnavailableException> { call, cause ->
        logger.error("Upstream service unavailable on {}: {}", loggedRequestPath(call.request.local.uri), cause.message, cause)
        call.respond(
            status = HttpStatusCode.ServiceUnavailable,
            message = ErrorResponse(code = "SERVICE_UNAVAILABLE", message = "Upstream service is temporarily unavailable.")
        )
    }

    exception<UpstreamTimeoutException> { call, cause ->
        logger.error("Upstream timeout on {}: {}", loggedRequestPath(call.request.local.uri), cause.message, cause)
        call.respond(
            status = HttpStatusCode.GatewayTimeout,
            message = ErrorResponse(code = "GATEWAY_TIMEOUT", message = "Upstream request timed out.")
        )
    }
}

private fun StatusPagesConfig.configureClientExceptionHandlers() {
    exception<IllegalArgumentException> { call, cause ->
        logger.warn("Validation error on {}: {}", loggedRequestPath(call.request.local.uri), cause.message)
        call.respond(
            status = HttpStatusCode.BadRequest,
            message = ErrorResponse(code = "BAD_REQUEST", message = cause.message ?: "Invalid request parameters.")
        )
    }

    exception<NoSuchElementException> { call, cause ->
        logger.warn("Resource not found on {}: {}", loggedRequestPath(call.request.local.uri), cause.message)
        call.respond(
            status = HttpStatusCode.NotFound,
            message = ErrorResponse(code = "NOT_FOUND", message = cause.message ?: "Requested resource not found.")
        )
    }
}

private fun StatusPagesConfig.configureDirectStatusHandlers() {
    status(HttpStatusCode.TooManyRequests) { call, _ ->
        call.response.header(HttpHeaders.RetryAfter, "1")
        call.respond(
            status = HttpStatusCode.TooManyRequests,
            message = ErrorResponse(
                code = "RATE_LIMIT_EXCEEDED",
                message = "Rate limit exceeded. Please retry later."
            )
        )
    }
}

private fun StatusPagesConfig.configureFallbackExceptionHandler() {
    exception<Throwable> { call, cause ->
        if (cause is CancellationException) {
            // Strict structured-concurrency compliance: CancellationException is never swallowed
            throw cause
        }
        logger.error("Unhandled internal server error on {}", loggedRequestPath(call.request.local.uri), cause)
        call.respond(
            status = HttpStatusCode.InternalServerError,
            message = ErrorResponse(
                code = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred. Please try again later."
            )
        )
    }
}

internal fun loggedRequestPath(uri: String): String = uri.substringBefore('?')
