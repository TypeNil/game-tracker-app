package io.github.typenil.gametracker.devtools

import android.content.Context
import android.content.Intent

/**
 * Build-type seam for the developer tools entry point.
 *
 * `main`'s settings screen names this symbol, so every build type has to provide it; the release
 * variant provides the no-op below. `isAvailable` is a `const`, so the release branch folds away at
 * compile time — but the boundary that actually keeps the tools out of a release APK is that
 * `src/debug` is not part of a release compilation, and `verifyReleaseArtifacts` proves it.
 */
internal object DevToolsEntry {
    const val isAvailable: Boolean = true

    fun open(context: Context) {
        context.startActivity(Intent(context, DevToolsActivity::class.java))
    }
}
