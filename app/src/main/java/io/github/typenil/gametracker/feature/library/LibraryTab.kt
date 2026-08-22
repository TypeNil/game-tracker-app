package io.github.typenil.gametracker.feature.library

import androidx.annotation.StringRes
import io.github.typenil.gametracker.R

enum class LibraryTab(@StringRes val titleRes: Int) {
    ALL(R.string.library_tab_all),
    PLAYING(R.string.library_status_playing),
    WISHLIST(R.string.library_status_wishlist),
    COMPLETED(R.string.library_status_completed),
    DROPPED(R.string.library_status_dropped),
    NOT_INTERESTED(R.string.library_status_not_interested)
}
