package com.gametracker.backend.routes

import com.gametracker.backend.application.IgdbConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class HealthAndRateLimitTest {

    private fun Application.healthTestModule(configured: Boolean) {
        val config = object : IgdbConfig {
            override val clientId = if (configured) "valid_id" else ""
            override val clientSecret = if (configured) "valid_secret" else ""
        }
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(RateLimit) {
            register(RateLimitName("api_v1")) {
                rateLimiter(limit = 2, refillPeriod = 60.seconds)
            }
        }
        routing {
            healthRoutes(config)
            rateLimit(RateLimitName("api_v1")) {
                get("/limited-endpoint") {
                    call.respondText("OK")
                }
            }
        }
    }

    @Test
    fun `health endpoint returns UP`() = testApplication {
        application { healthTestModule(configured = true) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthStatusDto>()
        assertEquals("UP", body.status)
    }

    @Test
    fun `health ready returns READY when configured and NOT_READY when not configured`() = testApplication {
        application { healthTestModule(configured = true) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val responseReady = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, responseReady.status)
        val bodyReady = responseReady.body<ReadyStatusDto>()
        assertEquals("READY", bodyReady.status)
        assertEquals(true, bodyReady.configured)
    }

    @Test
    fun `health ready returns 503 when credentials missing`() = testApplication {
        application { healthTestModule(configured = false) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = response.body<ReadyStatusDto>()
        assertEquals("NOT_READY", body.status)
        assertEquals(false, body.configured)
    }

    @Test
    fun `health routes are not throttled when API rate limit is exceeded`() = testApplication {
        application { healthTestModule(configured = true) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Исчерпываем 2 разрешенных вызова к ограниченному эндпоинту
        val res1 = client.get("/limited-endpoint")
        val res2 = client.get("/limited-endpoint")
        val res3 = client.get("/limited-endpoint")

        assertEquals(HttpStatusCode.OK, res1.status)
        assertEquals(HttpStatusCode.OK, res2.status)
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)

        // Health-эндпоинты должны оставаться доступными (200 OK)
        val healthRes = client.get("/health")
        val pingRes = client.get("/api/v1/ping")

        assertEquals(HttpStatusCode.OK, healthRes.status)
        assertEquals(HttpStatusCode.OK, pingRes.status)
    }
}
