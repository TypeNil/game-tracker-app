package io.github.typenil.gametracker.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NetworkConnectivityPillTest {

    @Test
    fun `pill modes are distinct and comprehensive`() {
        assertEquals(3, PillMode.entries.size)
        assertNotNull(PillMode.valueOf("Hidden"))
        assertNotNull(PillMode.valueOf("Offline"))
        assertNotNull(PillMode.valueOf("Restored"))
    }

    @Test
    fun `pill test tag is defined`() {
        assertEquals("network_connectivity_pill", NETWORK_CONNECTIVITY_PILL_TAG)
    }
}
