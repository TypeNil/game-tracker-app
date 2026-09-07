package io.github.typenil.gametracker.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import dagger.hilt.android.EntryPointAccessors
import okhttp3.logging.HttpLoggingInterceptor
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
}
