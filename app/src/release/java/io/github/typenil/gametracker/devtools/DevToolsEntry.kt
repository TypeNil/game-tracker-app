package io.github.typenil.gametracker.devtools

import android.content.Context

/**
 * Release counterpart of the debug developer-tools entry point. It exists only because `main`'s
 * settings screen names [DevToolsEntry]; the debug implementation is never compiled into a release
 * variant.
 */
internal object DevToolsEntry {
    const val isAvailable: Boolean = false

    fun open(context: Context) = Unit
}
