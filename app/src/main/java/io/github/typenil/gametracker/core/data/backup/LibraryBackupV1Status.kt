package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.model.LibraryStatus

/**
 * Immutable schemaVersion=1 status wire values. Domain enum names must not
 * become the backup contract; map explicitly and bump the schema to add values.
 */
internal object LibraryBackupV1Status {
    const val PLAYING = "PLAYING"
    const val WISHLIST = "WISHLIST"
    const val COMPLETED = "COMPLETED"
    const val DROPPED = "DROPPED"
    const val NOT_INTERESTED = "NOT_INTERESTED"

    private val wireToDomain = mapOf(
        PLAYING to LibraryStatus.PLAYING,
        WISHLIST to LibraryStatus.WISHLIST,
        COMPLETED to LibraryStatus.COMPLETED,
        DROPPED to LibraryStatus.DROPPED,
        NOT_INTERESTED to LibraryStatus.NOT_INTERESTED,
    )

    private val domainToWire = wireToDomain.entries.associate { it.value to it.key }

    fun toDomain(wire: String): LibraryStatus? = wireToDomain[wire]

    fun toWire(status: LibraryStatus): String =
        domainToWire[status]
            ?: throw IllegalArgumentException("LibraryStatus.$status is not a v1 backup value")
}
