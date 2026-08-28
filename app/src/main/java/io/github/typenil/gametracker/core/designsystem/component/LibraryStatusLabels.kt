package io.github.typenil.gametracker.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.LibraryCompleted
import io.github.typenil.gametracker.core.designsystem.theme.LibraryCompletedLight
import io.github.typenil.gametracker.core.designsystem.theme.LibraryDropped
import io.github.typenil.gametracker.core.designsystem.theme.LibraryDroppedLight
import io.github.typenil.gametracker.core.designsystem.theme.LibraryNotInterested
import io.github.typenil.gametracker.core.designsystem.theme.LibraryNotInterestedLight
import io.github.typenil.gametracker.core.designsystem.theme.LibraryPlaying
import io.github.typenil.gametracker.core.designsystem.theme.LibraryPlayingLight
import io.github.typenil.gametracker.core.designsystem.theme.LibraryWishlist
import io.github.typenil.gametracker.core.designsystem.theme.LibraryWishlistLight
import io.github.typenil.gametracker.core.model.LibraryStatus

@StringRes
fun LibraryStatus.displayNameRes(): Int = when (this) {
    LibraryStatus.PLAYING -> R.string.library_status_playing
    LibraryStatus.WISHLIST -> R.string.library_status_wishlist
    LibraryStatus.COMPLETED -> R.string.library_status_completed
    LibraryStatus.DROPPED -> R.string.library_status_dropped
    LibraryStatus.NOT_INTERESTED -> R.string.library_status_not_interested
}

fun LibraryStatus.contentColor(isDarkTheme: Boolean): Color = if (isDarkTheme) {
    when (this) {
        LibraryStatus.PLAYING -> LibraryPlaying
        LibraryStatus.WISHLIST -> LibraryWishlist
        LibraryStatus.COMPLETED -> LibraryCompleted
        LibraryStatus.DROPPED -> LibraryDropped
        LibraryStatus.NOT_INTERESTED -> LibraryNotInterested
    }
} else {
    when (this) {
        LibraryStatus.PLAYING -> LibraryPlayingLight
        LibraryStatus.WISHLIST -> LibraryWishlistLight
        LibraryStatus.COMPLETED -> LibraryCompletedLight
        LibraryStatus.DROPPED -> LibraryDroppedLight
        LibraryStatus.NOT_INTERESTED -> LibraryNotInterestedLight
    }
}

@Composable
@ReadOnlyComposable
fun LibraryStatus.contentColor(): Color {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return contentColor(isDarkTheme)
}
