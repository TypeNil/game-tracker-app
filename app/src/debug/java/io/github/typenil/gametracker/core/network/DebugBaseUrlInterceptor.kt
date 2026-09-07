package io.github.typenil.gametracker.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class DebugBaseUrlInterceptor @Inject constructor(
    private val debugBffUrlStore: DebugBffUrlStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val overrideUrl = debugBffUrlStore.currentUrl() ?: return chain.proceed(originalRequest)

        val newUrl = originalRequest.url.newBuilder()
            .scheme(overrideUrl.scheme)
            .host(overrideUrl.host)
            .port(overrideUrl.port)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DebugNetworkInterceptorModule {

    @Binds
    @IntoSet
    abstract fun bindDebugBaseUrlInterceptor(
        interceptor: DebugBaseUrlInterceptor,
    ): Interceptor
}
