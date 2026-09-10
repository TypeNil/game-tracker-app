package io.github.typenil.gametracker.core.data.backup

import android.content.Context
import android.net.Uri
import io.github.typenil.gametracker.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DocumentBytesStore {
    suspend fun read(uri: Uri, maxBytes: Int = MAX_BACKUP_BYTES): ByteArray

    suspend fun write(uri: Uri, bytes: ByteArray)
}

@Singleton
class ContentResolverDocumentBytesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentBytesStore {

    override suspend fun read(uri: Uri, maxBytes: Int): ByteArray = withContext(ioDispatcher) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Document provider returned a null input stream")
        stream.use { it.readAtMost(maxBytes) }
    }

    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(ioDispatcher) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Document provider returned a null output stream")
        stream.use { output ->
            output.write(bytes)
            output.flush()
        }
    }
}
