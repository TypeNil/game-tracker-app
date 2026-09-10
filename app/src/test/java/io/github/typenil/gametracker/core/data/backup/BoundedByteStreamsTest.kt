package io.github.typenil.gametracker.core.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedByteStreamsTest {

    @Test
    fun readAtMost_returnsExactBytesUnderCap() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val read = ByteArrayInputStream(payload).readAtMost(maxBytes = 8)
        assertArrayEquals(payload, read)
    }

    @Test
    fun readAtMost_rejectsPayloadAboveCap() {
        val payload = ByteArray(5) { 1 }
        val error = assertThrows(IOException::class.java) {
            ByteArrayInputStream(payload).readAtMost(maxBytes = 4)
        }
        assertEquals(LibraryBackupError.TOO_LARGE.name, error.message)
    }

    @Test
    fun ensureFitsBackupLimit_returnsSameArrayAtCap() {
        val payload = ByteArray(4) { 1 }
        assertArrayEquals(payload, payload.ensureFitsBackupLimit(maxBytes = 4))
    }

    @Test
    fun ensureFitsBackupLimit_rejectsAboveCap() {
        val payload = ByteArray(5) { 1 }
        val error = assertThrows(IOException::class.java) {
            payload.ensureFitsBackupLimit(maxBytes = 4)
        }
        assertEquals(LibraryBackupError.TOO_LARGE.name, error.message)
    }
}
