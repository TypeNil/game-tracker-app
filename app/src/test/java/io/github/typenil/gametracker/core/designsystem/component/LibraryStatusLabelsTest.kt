package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStatusLabelsTest {

    @Test
    fun `displayNameRes maps each status correctly`() {
        assertEquals(R.string.library_status_playing, LibraryStatus.PLAYING.displayNameRes())
        assertEquals(R.string.library_status_completed, LibraryStatus.COMPLETED.displayNameRes())
        assertEquals(R.string.library_status_wishlist, LibraryStatus.WISHLIST.displayNameRes())
        assertEquals(R.string.library_status_dropped, LibraryStatus.DROPPED.displayNameRes())
        assertEquals(R.string.library_status_not_interested, LibraryStatus.NOT_INTERESTED.displayNameRes())
    }

    @Test
    fun `leadingIcon maps each status to distinct vector icon`() {
        assertEquals(Icons.Filled.Bookmark, LibraryStatus.PLAYING.leadingIcon())
        assertEquals(Icons.Filled.Check, LibraryStatus.COMPLETED.leadingIcon())
        assertEquals(Icons.Filled.BookmarkBorder, LibraryStatus.WISHLIST.leadingIcon())
        assertEquals(Icons.Filled.Close, LibraryStatus.DROPPED.leadingIcon())
        assertEquals(Icons.Filled.RemoveCircleOutline, LibraryStatus.NOT_INTERESTED.leadingIcon())
    }
}
