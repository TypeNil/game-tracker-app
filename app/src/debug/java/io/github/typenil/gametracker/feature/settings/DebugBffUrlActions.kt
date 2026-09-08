package io.github.typenil.gametracker.feature.settings

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.network.DebugBffUrlStore
import io.github.typenil.gametracker.core.network.toDebugBffOriginOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DebugBffUrlEntryPoint {
    fun debugBffUrlStore(): DebugBffUrlStore
}

internal object DebugBffUrlActions {
    const val isVisible: Boolean = true

    private fun store(context: Context): DebugBffUrlStore {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugBffUrlEntryPoint::class.java,
        ).debugBffUrlStore()
    }

    fun currentUrl(context: Context): String {
        return store(context).currentUrl()?.toString().orEmpty()
    }

    fun getUrl(context: Context): String = currentUrl(context)

    fun setUrl(context: Context, url: String): Boolean {
        val origin = url.toDebugBffOriginOrNull() ?: return false
        store(context).setUrl(origin.toHttpUrlOrNull())
        return true
    }

    fun resetUrl(context: Context) {
        store(context).setUrl(null as HttpUrl?)
    }

    fun isValidUrl(url: String): Boolean {
        return url.toDebugBffOriginOrNull() != null
    }

    fun toOriginOrNull(url: String): String? {
        return url.toDebugBffOriginOrNull()
    }

    fun toDebugBffOriginOrNull(url: String): String? {
        return url.toDebugBffOriginOrNull()
    }
}
