package io.github.typenil.gametracker.core.network

import io.github.typenil.gametracker.core.network.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugOverrideDoesNotRewriteImageRequestsTest {

    @Test
    fun `debug bff override rewrites api requests but image requests remain untouched`() {
        val overrideUrl = "http://192.168.1.200:8888/".toHttpUrl()
        val store: DebugBffUrlStore = mockk {
            every { currentUrl() } returns overrideUrl
        }
        val debugInterceptor = DebugBaseUrlInterceptor(store)

        val transport = NetworkModule.buildTransportHttpClient()
        val apiClient = NetworkModule.buildApiHttpClient(
            transport = transport,
            bffInterceptors = setOf(debugInterceptor),
            enableLogging = false,
        )
        val imageClient = NetworkModule.provideImageHttpClient(transport)

        assertTrue(
            "API client must contain DebugBaseUrlInterceptor",
            apiClient.interceptors.any { it is DebugBaseUrlInterceptor },
        )
        assertFalse(
            "Image client must not contain DebugBaseUrlInterceptor",
            imageClient.interceptors.any { it is DebugBaseUrlInterceptor },
        )

        val capturedApiUrl = apiClient.executeAndCaptureUrl(
            "http://localhost:8080/api/v1/games?limit=20",
            "application/json",
        )
        assertEquals("http", capturedApiUrl?.scheme)
        assertEquals("192.168.1.200", capturedApiUrl?.host)
        assertEquals(8888, capturedApiUrl?.port)
        assertEquals("/api/v1/games", capturedApiUrl?.encodedPath)
        assertEquals("limit=20", capturedApiUrl?.query)

        val capturedImageUrl = imageClient.executeAndCaptureUrl(
            "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.jpg",
            "image/jpeg",
        )
        assertEquals("https", capturedImageUrl?.scheme)
        assertEquals("images.igdb.com", capturedImageUrl?.host)
        assertEquals(443, capturedImageUrl?.port)
        assertEquals("/igdb/image/upload/t_cover_big/co1r7f.jpg", capturedImageUrl?.encodedPath)
    }

    @Test
    fun `same api client picks up override set after construction`() {
        val overrideUrl = "http://192.168.1.200:8888/".toHttpUrl()
        val store: DebugBffUrlStore = mockk {
            every { currentUrl() } returns null andThen overrideUrl
        }
        val transport = NetworkModule.buildTransportHttpClient()
        val apiClient = NetworkModule.buildApiHttpClient(
            transport = transport,
            bffInterceptors = setOf(DebugBaseUrlInterceptor(store)),
            enableLogging = false,
        )

        val before = apiClient.executeAndCaptureUrl(API_PATH, "application/json")
        val after = apiClient.executeAndCaptureUrl(API_PATH, "application/json")

        assertEquals("localhost", before?.host)
        assertEquals("192.168.1.200", after?.host)
    }
    private companion object {
        const val HTTP_OK = 200
        const val API_PATH = "http://localhost:8080/api/v1/games"
    }

    private fun OkHttpClient.executeAndCaptureUrl(url: String, mediaType: String): HttpUrl? {
        val recorder = RecordingResponder(mediaType)
        newBuilder().addInterceptor(recorder).build()
            .newCall(Request.Builder().url(url).build()).execute().close()
        return recorder.capturedUrl
    }

    private class RecordingResponder(private val mediaType: String) : Interceptor {
        var capturedUrl: HttpUrl? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            capturedUrl = chain.request().url
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_OK)
                .message("OK")
                .body("{}".toResponseBody(mediaType.toMediaType()))
                .build()
        }
    }
}
