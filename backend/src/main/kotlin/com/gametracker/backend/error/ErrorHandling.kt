package com.gametracker.backend.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

/**
 * Централизованная обработка исключений в Ktor через плагин StatusPages.
 * Перехватывает ошибки и отдает клиенту структурированный JSON [ErrorResponse].
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Validation error on ${call.request.local.uri}: ${cause.message}")
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse(
                    code = "BAD_REQUEST",
                    message = cause.message ?: "Invalid request parameters"
                )
            )
        }

        exception<NoSuchElementException> { call, cause ->
            logger.warn("Resource not found on ${call.request.local.uri}: ${cause.message}")
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponse(
                    code = "NOT_FOUND",
                    message = cause.message ?: "Requested resource not found"
                )
            )
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled internal server error on ${call.request.local.uri}", cause)
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(
                    code = "INTERNAL_SERVER_ERROR",
                    message = "An unexpected error occurred. Please try again later."
                )
            )
        }
    }
}
