package com.gametracker.backend

import com.gametracker.backend.routes.HealthStatusDto
import com.gametracker.backend.routes.PingResponseDto
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    @Test
    fun healthEndpointReturnsStatusUp() = testApplication {
        application {
            module()
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<HealthStatusDto>()
        assertEquals("UP", body.status)
        assertEquals("GameTracker-BFF", body.service)
        assertEquals("1.0.0", body.version)
        assertNotNull(body.timestamp)
    }

    @Test
    fun pingEndpointReturnsPong() = testApplication {
        application {
            module()
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/api/v1/ping")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<PingResponseDto>()
        assertEquals("pong", body.message)
    }
}
