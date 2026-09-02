package io.github.typenil.gametracker.core.network.di

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NetworkModuleCallTimeoutTest {

    @Test
    fun `call timeout aborts a stalled response far below the read-timeout budget`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()

        val client = NetworkModule.buildOkHttpClient(callTimeoutSeconds = 1)
        val request = Request.Builder().url(server.url("/")).build()

        val startNanos = System.nanoTime()
        try {
            client.newCall(request).execute().use {
                fail("Expected the call to be aborted by the call timeout")
            }
        } catch (e: IOException) {
            // Monotonic elapsed-time bound, CI-tolerant: never an exactly-one-second wall
            // assertion. Without the callTimeout the same stall would hold Paging in
            // Loading until the 15-second read timeout.
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            assertTrue(
                "Expected abort near the 1s call budget, took ${elapsedMillis}ms: ${e.message}",
                elapsedMillis in 500..5_000
            )
            assertTrue(
                "Expected a timeout failure, got: ${e.message}",
                e.message?.contains("timeout", ignoreCase = true) == true
            )
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }
}
