package io.github.kamui2040.vectorint.backup

import kotlinx.coroutines.flow.Flow
import java.time.Instant

internal enum class AutoBackupInterval {
    NEVER,
    DAILY,
    WEEKLY,
}

internal data class AutoBackupConfiguration(
    val destinationTreeUri: String? = null,
    val afterChanges: Boolean = false,
    val onAppStart: Boolean = false,
    val onAppBackground: Boolean = false,
    val interval: AutoBackupInterval = AutoBackupInterval.NEVER,
) {
    val hasDestination: Boolean
        get() = !destinationTreeUri.isNullOrBlank()
}

internal enum class AutoBackupLastResult {
    NONE,
    SUCCEEDED,
    FAILED,
}

internal data class AutoBackupState(
    val configuration: AutoBackupConfiguration = AutoBackupConfiguration(),
    val lastResult: AutoBackupLastResult = AutoBackupLastResult.NONE,
    val lastAttemptAt: Instant? = null,
)

internal interface AutoBackupSettingsRepository {
    val state: Flow<AutoBackupState>

    suspend fun saveConfiguration(configuration: AutoBackupConfiguration)

    suspend fun recordResult(
        result: AutoBackupLastResult,
        attemptedAt: Instant,
    )
}
