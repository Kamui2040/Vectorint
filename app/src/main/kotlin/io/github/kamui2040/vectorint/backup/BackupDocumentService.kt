package io.github.kamui2040.vectorint.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

internal interface BackupDocumentGateway {
    suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    )

    suspend fun read(uri: Uri): ByteArray
}

internal class AndroidBackupDocumentGateway(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupDocumentGateway {
    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ) = withContext(ioDispatcher) {
        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw IOException("The selected backup document cannot be opened")
    }

    override suspend fun read(uri: Uri): ByteArray =
        withContext(ioDispatcher) {
            val input =
                contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected backup document cannot be opened")
            input.use { source ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > VectorintBackupContract.MAX_BYTES) {
                        throw InvalidVectorintBackup("Backup exceeds the size limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
}

internal sealed interface BackupExportResult {
    data object Exported : BackupExportResult

    data object Failed : BackupExportResult
}

internal class BackupDocumentService(
    private val coordinator: BackupCoordinator,
    private val documents: BackupDocumentGateway,
) {
    suspend fun export(uri: Uri): BackupExportResult =
        when (val backup = coordinator.createBackup()) {
            is BackupCreationResult.Ready ->
                try {
                    documents.write(uri, backup.bytes)
                    BackupExportResult.Exported
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    BackupExportResult.Failed
                }

            BackupCreationResult.StorageFailed -> BackupExportResult.Failed
        }

    suspend fun restore(uri: Uri): BackupRestoreResult {
        val bytes =
            try {
                documents.read(uri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: InvalidVectorintBackup) {
                return BackupRestoreResult.InvalidBackup
            } catch (_: Exception) {
                return BackupRestoreResult.StorageFailed
            }
        return coordinator.restore(bytes)
    }
}
