package com.gametracker.backend.error

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ErrorHandlingTest {

    private fun Application.errorTestModule() {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        configureErrorHandling()
        routing {
            get("/throw/rate-limit") {
                throw UpstreamRateLimitException(retryAfterSeconds = 30)
            }
            get("/throw/bad-gateway") {
                throw UpstreamBadGatewayException("Upstream failed")
            }
            get("/throw/service-unavailable") {
                throw UpstreamServiceUnavailableException("Service down")
            }
            get("/throw/timeout") {
                throw UpstreamTimeoutException("Timed out")
            }
            get("/throw/bad-request") {
                throw IllegalArgumentException("Invalid input")
            }
            get("/throw/not-found") {
                throw NoSuchElementException("Resource missing")
            }
            get("/throw/generic") {
                throw RuntimeException("Unexpected bug")
            }
            get("/throw/cancellation") {
                throw CancellationException("Coroutine cancelled")
            }
        }
    }

    @Test
    fun `UpstreamRateLimitException maps to 429 and preserves Retry-After header`() = testApplication {
        application { errorTestModule() }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/throw/rate-limit")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("30", response.headers[HttpHeaders.RetryAfter])

        val body = response.body<ErrorResponse>()
        assertEquals("RATE_LIMIT_EXCEEDED", body.code)
    }

    @Test
    fun `UpstreamBadGatewayException maps to 502 Bad Gateway`() = testApplication {
        application { errorTestModule() }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/throw/bad-gateway")
        assertEquals(HttpStatusCode.BadGateway, response.status)

        val body = response.body<ErrorResponse>()
        assertEquals("BAD_GATEWAY", body.code)
    }

    @Test
    fun `UpstreamServiceUnavailableException maps to 503 Service Unavailable`() = testApplication {
        application { errorTestModule() }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/throw/service-unavailable")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = response.body<ErrorResponse>()
        assertEquals("SERVICE_UNAVAILABLE", body.code)
    }

    @Test
    fun `UpstreamTimeoutException maps to 504 Gateway Timeout`() = testApplication {
        application { errorTestModule() }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/throw/timeout")
        assertEquals(HttpStatusCode.GatewayTimeout, response.status)

        val body = response.body<ErrorResponse>()
        assertEquals("GATEWAY_TIMEOUT", body.code)
    }

    @Test
    fun `IllegalArgumentException maps to 400 Bad Request`() = testApplication {
        application { errorTestModule() }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/throw/bad-request")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", body.code)
    }

    @Test
    fun `CancellationException is rethrown and not caught by ErrorResponse handler`() = testApplication {
        application { errorTestModule() }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/throw/cancellation")
        val isCustomErrorResponse = runCatching { response.body<ErrorResponse>() }.getOrNull()?.code == "INTERNAL_SERVER_ERROR"
        assertFalse(isCustomErrorResponse)
    }
}
