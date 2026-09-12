package io.github.kamui2040.vectorint.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

internal interface AutoBackupDirectoryGateway {
    suspend fun writeVerified(
        treeUri: String,
        fileName: String,
        bytes: ByteArray,
        keepLatest: Int,
    )
}

internal class AndroidAutoBackupDirectoryGateway(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoBackupDirectoryGateway {
    override suspend fun writeVerified(
        treeUri: String,
        fileName: String,
        bytes: ByteArray,
        keepLatest: Int,
    ) = withContext(ioDispatcher) {
        require(keepLatest > 0)
        val selectedTree = Uri.parse(treeUri)
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(
                selectedTree,
                DocumentsContract.getTreeDocumentId(selectedTree),
            )
        val created =
            DocumentsContract.createDocument(
                contentResolver,
                parent,
                BACKUP_MIME_TYPE,
                fileName,
            ) ?: throw IOException("The automatic backup document could not be created")

        try {
            contentResolver.openOutputStream(created, "wt")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: throw IOException("The automatic backup document could not be opened")
            if (!readBounded(created).contentEquals(bytes)) {
                throw IOException("The automatic backup document could not be verified")
            }
        } catch (failure: Exception) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, created) }
            throw failure
        }

        try {
            automaticBackupDocuments(selectedTree)
                .sortedByDescending(AutomaticBackupDocument::displayName)
                .drop(keepLatest)
                .forEach { document ->
                    runCatching { DocumentsContract.deleteDocument(contentResolver, document.uri) }
                }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A provider can allow file creation but not listing or deletion. The verified backup remains valid.
        }
    }

    private fun readBounded(uri: Uri): ByteArray {
        val input =
            contentResolver.openInputStream(uri)
                ?: throw IOException("The automatic backup document could not be opened")
        return input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = source.read(buffer)
                if (count == -1) break
                if (output.size() + count > VectorintBackupContract.MAX_BYTES) {
                    throw IOException("The automatic backup document exceeds the size limit")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun automaticBackupDocuments(treeUri: Uri): List<AutomaticBackupDocument> {
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
        val documents = mutableListOf<AutomaticBackupDocument>()
        contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)
                if (AutoBackupFilePolicy.isManagedFileName(displayName)) {
                    documents +=
                        AutomaticBackupDocument(
                            displayName = displayName,
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex)),
                        )
                }
            }
        }
        return documents
    }

    private data class AutomaticBackupDocument(
        val displayName: String,
        val uri: Uri,
    )

    private companion object {
        const val BACKUP_MIME_TYPE = "application/json"
    }
}
