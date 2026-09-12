package io.github.kamui2040.vectorint.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.UUID

internal enum class AutoBackupRunResult {
    SAVED,
    SKIPPED,
    FAILED,
}

internal class AutoBackupRunner(
    private val settingsRepository: AutoBackupSettingsRepository,
    private val backupCoordinator: BackupCoordinator,
    private val directoryGateway: AutoBackupDirectoryGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val idSource: () -> String = {
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(8)
    },
) {
    private val runMutex = Mutex()

    suspend fun run(): AutoBackupRunResult = runMutex.withLock { runOnce() }

    private suspend fun runOnce(): AutoBackupRunResult {
        val attemptedAt = Instant.now(clock)
        val configuration =
            try {
                settingsRepository.state.first().configuration
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return AutoBackupRunResult.FAILED
            }
        val destination = configuration.destinationTreeUri
        if (!configuration.hasDestination || destination == null) return AutoBackupRunResult.SKIPPED

        val result =
            when (val backup = backupCoordinator.createBackup()) {
                is BackupCreationResult.Ready ->
                    try {
                        directoryGateway.writeVerified(
                            treeUri = destination,
                            fileName = AutoBackupFilePolicy.createFileName(attemptedAt, idSource()),
                            bytes = backup.bytes,
                            keepLatest = AUTOMATIC_BACKUP_RETENTION,
                        )
                        AutoBackupRunResult.SAVED
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        AutoBackupRunResult.FAILED
                    }

                BackupCreationResult.StorageFailed -> AutoBackupRunResult.FAILED
            }
        try {
            settingsRepository.recordResult(
                result =
                    when (result) {
                        AutoBackupRunResult.SAVED -> AutoBackupLastResult.SUCCEEDED
                        AutoBackupRunResult.FAILED -> AutoBackupLastResult.FAILED
                        AutoBackupRunResult.SKIPPED -> AutoBackupLastResult.NONE
                    },
                attemptedAt = attemptedAt,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The backup itself remains valid even if its status could not be recorded.
        }
        return result
    }

    private companion object {
        const val AUTOMATIC_BACKUP_RETENTION = 10
    }
}
