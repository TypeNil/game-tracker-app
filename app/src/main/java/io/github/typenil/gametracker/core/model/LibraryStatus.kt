package io.github.typenil.gametracker.core.model

/**
 * Status of a game in the user's personal library.
 */
enum class LibraryStatus {
    PLAYING,
    WISHLIST,
    COMPLETED,
    DROPPED,
    NOT_INTERESTED;

    /**
     * Whether this status supports tracking hours played.
     */
    val supportsHours: Boolean
        get() = this == PLAYING || this == COMPLETED || this == DROPPED
}
