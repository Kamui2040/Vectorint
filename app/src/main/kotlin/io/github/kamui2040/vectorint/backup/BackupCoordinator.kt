package io.github.kamui2040.vectorint.backup

import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.BackupDataRepository
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant

internal sealed interface BackupCreationResult {
    data class Ready(
        val bytes: ByteArray,
    ) : BackupCreationResult

    data object StorageFailed : BackupCreationResult
}

internal sealed interface BackupRestoreResult {
    data object Restored : BackupRestoreResult

    data object InvalidBackup : BackupRestoreResult

    data object StorageFailed : BackupRestoreResult

    data object RecoveryFailed : BackupRestoreResult
}

internal class BackupCoordinator(
    private val dataRepository: BackupDataRepository,
    private val settingsRepository: SettingsRepository,
    private val codec: VectorintBackupCodec = VectorintBackupCodec(),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun createBackup(): BackupCreationResult =
        try {
            val backup =
                VectorintBackup(
                    createdAt = Instant.now(clock),
                    data = dataRepository.loadBackupData(),
                    settings = settingsRepository.settings.first(),
                )
            BackupCreationResult.Ready(codec.encode(backup))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            BackupCreationResult.StorageFailed
        }

    suspend fun restore(bytes: ByteArray): BackupRestoreResult {
        val candidate =
            try {
                codec.decode(bytes).let { backup ->
                    backup.copy(data = backup.data.normalized())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return BackupRestoreResult.InvalidBackup
            }

        val previousData: BackupData
        val previousSettings: UserSettings
        try {
            previousData = dataRepository.loadBackupData().normalized()
            previousSettings = settingsRepository.settings.first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return BackupRestoreResult.StorageFailed
        }

        return withContext(NonCancellable) {
            val settingsToRestore =
                if (candidate.sourceVersion >= 4) {
                    candidate.settings
                } else {
                    previousSettings.copy(includeExpectedIncome = candidate.settings.includeExpectedIncome)
                }
            try {
                dataRepository.replaceBackupData(candidate.data)
                settingsRepository.save(settingsToRestore)
                val restoredExactly =
                    dataRepository.loadBackupData().normalized() == candidate.data &&
                        settingsRepository.settings.first() == settingsToRestore
                if (restoredExactly) {
                    BackupRestoreResult.Restored
                } else {
                    recover(previousData, previousSettings)
                }
            } catch (cancellation: CancellationException) {
                recover(previousData, previousSettings)
                throw cancellation
            } catch (_: Exception) {
                recover(previousData, previousSettings)
            }
        }
    }

    private suspend fun recover(
        previousData: BackupData,
        previousSettings: UserSettings,
    ): BackupRestoreResult {
        val dataRecovered =
            try {
                dataRepository.replaceBackupData(previousData)
                dataRepository.loadBackupData().normalized() == previousData
            } catch (_: Exception) {
                false
            }
        val settingsRecovered =
            try {
                settingsRepository.save(previousSettings)
                settingsRepository.settings.first() == previousSettings
            } catch (_: Exception) {
                false
            }
        return if (dataRecovered && settingsRecovered) {
            BackupRestoreResult.StorageFailed
        } else {
            BackupRestoreResult.RecoveryFailed
        }
    }
}

private fun BackupData.normalized(): BackupData =
    copy(
        accounts = accounts.sortedBy { it.id.value },
        activities = activities.sortedBy { it.id.value },
        recurringItems = recurringItems.sortedBy { it.id.value },
        customCategories = customCategories.sortedBy { it.id.value },
    )
