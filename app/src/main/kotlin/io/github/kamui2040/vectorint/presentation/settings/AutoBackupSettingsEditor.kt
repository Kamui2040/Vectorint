package io.github.kamui2040.vectorint.presentation.settings

import io.github.kamui2040.vectorint.backup.AutoBackupConfiguration
import io.github.kamui2040.vectorint.backup.AutoBackupScheduler
import io.github.kamui2040.vectorint.backup.AutoBackupSettingsRepository
import io.github.kamui2040.vectorint.backup.AutoBackupState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal sealed interface AutoBackupSettingsLoadResult {
    data class Ready(
        val state: AutoBackupState,
    ) : AutoBackupSettingsLoadResult

    data object Failed : AutoBackupSettingsLoadResult
}

internal sealed interface AutoBackupSettingsSaveResult {
    data object Saved : AutoBackupSettingsSaveResult

    data object SavedSchedulingFailed : AutoBackupSettingsSaveResult

    data object Failed : AutoBackupSettingsSaveResult
}

internal class AutoBackupSettingsEditor(
    private val repository: AutoBackupSettingsRepository,
    private val scheduler: AutoBackupScheduler,
) {
    suspend fun load(): AutoBackupSettingsLoadResult =
        try {
            AutoBackupSettingsLoadResult.Ready(repository.state.first())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AutoBackupSettingsLoadResult.Failed
        }

    suspend fun save(configuration: AutoBackupConfiguration): AutoBackupSettingsSaveResult {
        try {
            repository.saveConfiguration(configuration)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return AutoBackupSettingsSaveResult.Failed
        }
        return try {
            scheduler.applyConfiguration(configuration)
            AutoBackupSettingsSaveResult.Saved
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AutoBackupSettingsSaveResult.SavedSchedulingFailed
        }
    }

    suspend fun selectDestination(
        current: AutoBackupConfiguration,
        treeUri: String,
    ): AutoBackupSettingsSaveResult {
        val updated = current.copy(destinationTreeUri = treeUri)
        return when (val saved = save(updated)) {
            AutoBackupSettingsSaveResult.Saved ->
                try {
                    scheduler.requestNow()
                    AutoBackupSettingsSaveResult.Saved
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    AutoBackupSettingsSaveResult.SavedSchedulingFailed
                }

            AutoBackupSettingsSaveResult.SavedSchedulingFailed,
            AutoBackupSettingsSaveResult.Failed,
            -> saved
        }
    }

    suspend fun clearDestination(current: AutoBackupConfiguration): AutoBackupSettingsSaveResult =
        save(current.copy(destinationTreeUri = null))
}
