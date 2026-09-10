package io.github.typenil.gametracker.core.data.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal const val MAX_BACKUP_BYTES = 8 * 1024 * 1024

internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) {
            throw IOException(LibraryBackupError.TOO_LARGE.name)
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
