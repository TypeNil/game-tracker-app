package io.github.typenil.gametracker.core.designsystem.component

import androidx.annotation.StringRes
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.LibraryStatus

@StringRes
fun LibraryStatus.displayNameRes(): Int = when (this) {
    LibraryStatus.PLAYING -> R.string.library_status_playing
    LibraryStatus.WISHLIST -> R.string.library_status_wishlist
    LibraryStatus.COMPLETED -> R.string.library_status_completed
    LibraryStatus.DROPPED -> R.string.library_status_dropped
    LibraryStatus.NOT_INTERESTED -> R.string.library_status_not_interested
}
