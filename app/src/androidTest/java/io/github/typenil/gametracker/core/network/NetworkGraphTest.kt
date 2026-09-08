package io.github.typenil.gametracker.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import dagger.hilt.android.EntryPointAccessors
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts the production Hilt network graph actually used at runtime:
 * the [SingletonImageLoader] singleton, shared transport resources and
 * isolated interceptor chains. Rebuilt copies in JVM tests cannot catch
 * qualifier or factory-registration regressions.
 */
@RunWith(AndroidJUnit4::class)
class NetworkGraphTest {

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun graph(): DebugNetworkGraphEntryPoint =
        EntryPointAccessors.fromApplication(
            appContext,
            DebugNetworkGraphEntryPoint::class.java,
        )

    @Test
    fun singletonImageLoader_isHiltProvidedLoader() {
        assertSame(graph().imageLoader(), SingletonImageLoader.get(appContext))
    }

    @Test
    fun transportResources_areSharedAcrossClients() {
        val graph = graph()
        assertSame(graph.transportClient().connectionPool, graph.apiClient().connectionPool)
        assertSame(graph.transportClient().dispatcher, graph.imageClient().dispatcher)
    }

    @Test
    fun apiClient_rewritesWhileImageClientStaysClean() {
        val graph = graph()
        assertTrue(
            graph.apiClient().interceptors.any { it is DebugBaseUrlInterceptor },
        )
        assertTrue(
            graph.imageClient().interceptors.none {
                it is DebugBaseUrlInterceptor || it is HttpLoggingInterceptor
            },
        )
    }

    @Test
    fun settingsStore_controlsProductionApiClient() {
        val graph = graph()
        val recorder = RecordingResponder()
        try {
            graph.debugBffUrlStore().setUrl("http://192.168.1.200:8888")

            val client = graph.apiClient()
                .newBuilder()
                .addInterceptor(recorder)
                .build()
            client.newCall(
                Request.Builder()
                    .url("https://original.example/api/v1/games?limit=20")
                    .build(),
            ).execute().close()

            assertEquals("192.168.1.200", recorder.capturedUrl?.host)
            assertEquals(8888, recorder.capturedUrl?.port)
            assertEquals("/api/v1/games", recorder.capturedUrl?.encodedPath)
            assertEquals("limit=20", recorder.capturedUrl?.query)
        } finally {
            graph.debugBffUrlStore().setUrl(urlString = null)
        }
    }

    private class RecordingResponder : Interceptor {
        var capturedUrl: HttpUrl? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            capturedUrl = chain.request().url
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_OK)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private companion object {
        const val HTTP_OK = 200
    }
}
