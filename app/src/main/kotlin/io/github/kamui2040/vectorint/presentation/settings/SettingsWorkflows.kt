package io.github.kamui2040.vectorint.presentation.settings

import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal sealed interface SettingsLoadResult {
    data class Ready(
        val settings: UserSettings,
    ) : SettingsLoadResult

    data object Failed : SettingsLoadResult
}

internal sealed interface SettingsSaveResult {
    data object Saved : SettingsSaveResult

    data object StorageFailed : SettingsSaveResult
}

internal class SettingsEditor(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun load(): SettingsLoadResult =
        try {
            SettingsLoadResult.Ready(settingsRepository.settings.first())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SettingsLoadResult.Failed
        }

    suspend fun save(settings: UserSettings): SettingsSaveResult =
        try {
            settingsRepository.save(settings)
            SettingsSaveResult.Saved
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SettingsSaveResult.StorageFailed
        }
}
