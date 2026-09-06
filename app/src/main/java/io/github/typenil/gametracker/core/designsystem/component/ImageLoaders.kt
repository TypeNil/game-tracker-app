package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Builds a Coil [ImageRequest] keyed to [url] and [reloadToken].
 *
 * When network reconnects or a refresh occurs, [reloadToken] changes,
 * causing Compose to produce a fresh [ImageRequest] with an updated parameter.
 * This instructs Coil's painter to exit stale error states and immediately
 * retry loading the image over the restored network on the fly.
 */
@Composable
fun rememberImageModel(
    url: String?,
    reloadToken: Long = 0L,
): Any? {
    val context = LocalContext.current
    return remember(url, reloadToken) {
        if (url.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .apply {
                    if (reloadToken != 0L) {
                        memoryCacheKeyExtra("reloadToken", reloadToken.toString())
                    }
                }
                .build()
        }
    }
}
