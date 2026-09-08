package io.github.typenil.gametracker.feature.settings

import android.content.Context

internal object DebugBffUrlActions {
    const val isVisible: Boolean = false

    fun currentUrl(context: Context): String = ""

    fun getUrl(context: Context): String = ""

    fun setUrl(context: Context, url: String): Boolean = false

    fun resetUrl(context: Context) = Unit

    fun isValidUrl(url: String): Boolean = false

    fun toOriginOrNull(url: String): String? = null

    fun toDebugBffOriginOrNull(url: String): String? = null
}
