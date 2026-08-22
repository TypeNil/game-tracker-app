package io.github.typenil.gametracker.core.database.converter

import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameTrackerTypeConvertersTest {

    private val converters = GameTrackerTypeConverters()

    @Test
    fun toLibraryStatus_mapsPlanToPlayToWishlist() {
        assertEquals(LibraryStatus.WISHLIST, converters.toLibraryStatus("PLAN_TO_PLAY"))
    }

    @Test
    fun toLibraryStatus_mapsNewStatusesCorrectly() {
        assertEquals(LibraryStatus.WISHLIST, converters.toLibraryStatus("WISHLIST"))
        assertEquals(LibraryStatus.NOT_INTERESTED, converters.toLibraryStatus("NOT_INTERESTED"))
        assertEquals(LibraryStatus.PLAYING, converters.toLibraryStatus("PLAYING"))
        assertEquals(LibraryStatus.COMPLETED, converters.toLibraryStatus("COMPLETED"))
        assertEquals(LibraryStatus.DROPPED, converters.toLibraryStatus("DROPPED"))
        assertNull(converters.toLibraryStatus(null))
        assertNull(converters.toLibraryStatus(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun toLibraryStatus_throwsOnUnknownStatus() {
        converters.toLibraryStatus("INVALID_STATUS_NAME")
    }

    @Test
    fun fromLibraryStatus_serializesExactEnumName() {
        assertEquals("WISHLIST", converters.fromLibraryStatus(LibraryStatus.WISHLIST))
        assertEquals("NOT_INTERESTED", converters.fromLibraryStatus(LibraryStatus.NOT_INTERESTED))
        assertNull(converters.fromLibraryStatus(null))
    }
}
