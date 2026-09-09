package io.github.typenil.gametracker.backend.application

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConfigTest {

    @Test
    fun `DEFAULT_HOST is loopback`() {
        assertEquals("127.0.0.1", ServerConfig.DEFAULT_HOST)
    }

    @Test
    fun `DEFAULT_PORT is 8080`() {
        assertEquals(8080, ServerConfig.DEFAULT_PORT)
    }
}
