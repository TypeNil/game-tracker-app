package com.gametracker.backend.routes

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.error.ErrorResponse
import com.gametracker.backend.error.configureErrorHandling
import com.gametracker.backend.resolveClientIp
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.header
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
                modifyResponse { call, _ ->
                    call.response.header(HttpHeaders.RetryAfter, "1")
                }
            }
        }
        configureErrorHandling()
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
    fun `client 429 rate limit response returns JSON ErrorResponse and Retry-After header`() = testApplication {
        application { healthTestModule(configured = true) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Exhaust 2 permits
        val res1 = client.get("/limited-endpoint")
        val res2 = client.get("/limited-endpoint")
        val res3 = client.get("/limited-endpoint")

        assertEquals(HttpStatusCode.OK, res1.status)
        assertEquals(HttpStatusCode.OK, res2.status)
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)
        assertEquals("1", res3.headers[HttpHeaders.RetryAfter])

        val error = res3.body<ErrorResponse>()
        assertEquals("RATE_LIMIT_EXCEEDED", error.code)
    }

    @Test
    fun `health routes are not throttled when API rate limit is exceeded`() = testApplication {
        application { healthTestModule(configured = true) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        client.get("/limited-endpoint")
        client.get("/limited-endpoint")
        val res3 = client.get("/limited-endpoint")
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)

        val healthRes = client.get("/health")
        val pingRes = client.get("/api/v1/ping")

        assertEquals(HttpStatusCode.OK, healthRes.status)
        assertEquals(HttpStatusCode.OK, pingRes.status)
    }

    @Test
    fun `trusted proxy correctly resolves client IP whereas untrusted peer falls back to local remoteHost`() = testApplication {
        application {
            install(XForwardedHeaders) {
                skipLastProxies(1)
            }
            routing {
                get("/test-ip-trusted") {
                    // testApplication direct peer is localhost (127.0.0.1 / localhost)
                    val ip = resolveClientIp(call, isProxyEnabled = true, trustedHosts = setOf("127.0.0.1", "localhost"))
                    call.respondText(ip)
                }
                get("/test-ip-untrusted") {
                    // direct peer is NOT in trustedHosts (e.g. only 10.0.0.1 is trusted)
                    val ip = resolveClientIp(call, isProxyEnabled = true, trustedHosts = setOf("10.0.0.1"))
                    call.respondText(ip)
                }
            }
        }

        val client = createClient {}

        // 1. When proxy is trusted, X-Forwarded-For is respected
        val trustedResponse = client.get("/test-ip-trusted") {
            header(HttpHeaders.XForwardedFor, "203.0.113.195")
        }
        assertEquals("203.0.113.195", trustedResponse.body<String>())

        // 2. When peer is not in trusted proxy allowlist, spoofed X-Forwarded-For is ignored
        val untrustedResponse = client.get("/test-ip-untrusted") {
            header(HttpHeaders.XForwardedFor, "203.0.113.195")
        }
        assertEquals("localhost", untrustedResponse.body<String>())
    }
}
