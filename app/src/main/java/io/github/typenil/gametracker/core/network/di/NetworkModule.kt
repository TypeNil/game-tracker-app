package io.github.typenil.gametracker.core.network.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.core.network.api.BffApiService
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TransportHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Whole-call budget covering DNS, connection establishment, server processing,
     * body transfer, redirects and OkHttp's internal route retries. Without it a
     * flaky network can silently consume connect+read timeouts back to back and
     * keep Paging in Loading for tens of seconds.
     */
    internal const val CALL_TIMEOUT_SECONDS = 20L

    @VisibleForTesting
    internal fun buildTransportHttpClient(callTimeoutSeconds: Long = CALL_TIMEOUT_SECONDS): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @TransportHttpClient
    fun provideTransportHttpClient(): OkHttpClient = buildTransportHttpClient(CALL_TIMEOUT_SECONDS)

    @VisibleForTesting
    internal fun buildApiHttpClient(
        transport: OkHttpClient,
        bffInterceptors: Set<Interceptor> = emptySet(),
        enableLogging: Boolean = BuildConfig.DEBUG,
    ): OkHttpClient {
        val builder = transport.newBuilder()
        bffInterceptors.forEach { builder.addInterceptor(it) }
        if (enableLogging) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideApiHttpClient(
        @TransportHttpClient transport: OkHttpClient,
        bffInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = buildApiHttpClient(transport, bffInterceptors)

    @Provides
    @Singleton
    @ImageHttpClient
    fun provideImageHttpClient(
        @TransportHttpClient transport: OkHttpClient,
    ): OkHttpClient {
        return transport.newBuilder().build()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ImageHttpClient okHttpClient: OkHttpClient,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { okHttpClient }
                    )
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BFF_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideBffApiService(retrofit: Retrofit): BffApiService {
        return retrofit.create(BffApiService::class.java)
    }
}
