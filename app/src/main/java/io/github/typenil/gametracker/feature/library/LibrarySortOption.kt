package io.github.typenil.gametracker.feature.library

import androidx.annotation.StringRes
import io.github.typenil.gametracker.R

enum class LibrarySortOption(@param:StringRes val labelRes: Int) {
    ADDED_DESC(R.string.library_sort_added_desc),
    UPDATED_DESC(R.string.library_sort_updated_desc),
    USER_RATING_DESC(R.string.library_sort_rating_desc),
    TITLE_ASC(R.string.library_sort_title_asc),
    HOURS_PLAYED_DESC(R.string.library_sort_hours_desc)
}
