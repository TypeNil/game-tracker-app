package io.github.typenil.gametracker.core.network.di

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    private val fakeInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }

    @Test
    fun `transport client has standard timeouts and zero interceptors`() {
        val client = NetworkModule.buildTransportHttpClient()

        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(20_000, client.callTimeoutMillis)
        assertTrue("Transport client must not have any interceptors", client.interceptors.isEmpty())
        assertTrue("Transport client must not have any network interceptors", client.networkInterceptors.isEmpty())
    }

    @Test
    fun `api client includes bff interceptors and logging interceptor in debug order`() {
        val transport = NetworkModule.buildTransportHttpClient()
        val client = NetworkModule.buildApiHttpClient(
            transport = transport,
            bffInterceptors = setOf(fakeInterceptor),
            enableLogging = true,
        )

        assertEquals(2, client.interceptors.size)
        assertEquals(fakeInterceptor, client.interceptors[0])
        assertTrue("Second interceptor must be HttpLoggingInterceptor", client.interceptors[1] is HttpLoggingInterceptor)
    }

    @Test
    fun `api client without logging includes only bff interceptors`() {
        val transport = NetworkModule.buildTransportHttpClient()
        val client = NetworkModule.buildApiHttpClient(
            transport = transport,
            bffInterceptors = setOf(fakeInterceptor),
            enableLogging = false,
        )

        assertEquals(1, client.interceptors.size)
        assertEquals(fakeInterceptor, client.interceptors[0])
        assertFalse(client.interceptors.any { it is HttpLoggingInterceptor })
    }

    @Test
    fun `image client inherits transport timeouts and is provably free of bff interceptors and logger`() {
        val transport = NetworkModule.buildTransportHttpClient()
        val imageClient = NetworkModule.provideImageHttpClient(transport)

        assertEquals(15_000, imageClient.connectTimeoutMillis)
        assertEquals(15_000, imageClient.readTimeoutMillis)
        assertEquals(20_000, imageClient.callTimeoutMillis)

        assertTrue(
            "Image client must be free of BFF interceptors and logging interceptors",
            imageClient.interceptors.isEmpty(),
        )
        assertTrue(
            "Image client must not have any network interceptors",
            imageClient.networkInterceptors.isEmpty(),
        )
    }
}
