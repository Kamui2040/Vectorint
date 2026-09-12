package io.github.kamui2040.vectorint.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kamui2040.vectorint.backup.AutoBackupConfiguration
import io.github.kamui2040.vectorint.backup.AutoBackupInterval
import io.github.kamui2040.vectorint.backup.AutoBackupLastResult
import io.github.kamui2040.vectorint.backup.AutoBackupSettingsRepository
import io.github.kamui2040.vectorint.backup.AutoBackupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant

private const val AUTO_BACKUP_DATA_STORE_NAME = "vectorint_auto_backup"
private val DESTINATION_TREE_URI = stringPreferencesKey("destination_tree_uri")
private val AFTER_CHANGES = booleanPreferencesKey("after_changes")
private val ON_APP_START = booleanPreferencesKey("on_app_start")
private val ON_APP_BACKGROUND = booleanPreferencesKey("on_app_background")
private val INTERVAL = stringPreferencesKey("interval")
private val LAST_RESULT = stringPreferencesKey("last_result")
private val LAST_ATTEMPT_EPOCH_SECOND = longPreferencesKey("last_attempt_epoch_second")
private val LAST_ATTEMPT_NANO = longPreferencesKey("last_attempt_nano")

internal val Context.vectorintAutoBackupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AUTO_BACKUP_DATA_STORE_NAME,
)

internal class DataStoreAutoBackupSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AutoBackupSettingsRepository {
    override val state: Flow<AutoBackupState> =
        dataStore.data
            .map { preferences ->
                AutoBackupState(
                    configuration =
                        AutoBackupConfiguration(
                            destinationTreeUri = preferences[DESTINATION_TREE_URI]?.takeIf(String::isNotBlank),
                            afterChanges = preferences[AFTER_CHANGES] ?: false,
                            onAppStart = preferences[ON_APP_START] ?: false,
                            onAppBackground = preferences[ON_APP_BACKGROUND] ?: false,
                            interval = preferences[INTERVAL].toAutoBackupInterval(),
                        ),
                    lastResult = preferences[LAST_RESULT].toAutoBackupLastResult(),
                    lastAttemptAt = preferences.toLastAttemptAt(),
                )
            }.distinctUntilChanged()

    override suspend fun saveConfiguration(configuration: AutoBackupConfiguration) {
        dataStore.edit { preferences ->
            val destination = configuration.destinationTreeUri?.takeIf(String::isNotBlank)
            if (destination == null) {
                preferences.remove(DESTINATION_TREE_URI)
            } else {
                preferences[DESTINATION_TREE_URI] = destination
            }
            preferences[AFTER_CHANGES] = configuration.afterChanges
            preferences[ON_APP_START] = configuration.onAppStart
            preferences[ON_APP_BACKGROUND] = configuration.onAppBackground
            preferences[INTERVAL] = configuration.interval.name
        }
    }

    override suspend fun recordResult(
        result: AutoBackupLastResult,
        attemptedAt: Instant,
    ) {
        dataStore.edit { preferences ->
            preferences[LAST_RESULT] = result.name
            preferences[LAST_ATTEMPT_EPOCH_SECOND] = attemptedAt.epochSecond
            preferences[LAST_ATTEMPT_NANO] = attemptedAt.nano.toLong()
        }
    }
}

private fun String?.toAutoBackupInterval(): AutoBackupInterval =
    AutoBackupInterval.entries.firstOrNull { it.name == this } ?: AutoBackupInterval.NEVER

private fun String?.toAutoBackupLastResult(): AutoBackupLastResult =
    AutoBackupLastResult.entries.firstOrNull { it.name == this } ?: AutoBackupLastResult.NONE

private fun Preferences.toLastAttemptAt(): Instant? {
    val epochSecond = this[LAST_ATTEMPT_EPOCH_SECOND] ?: return null
    val nano = this[LAST_ATTEMPT_NANO] ?: return null
    if (nano !in 0L..999_999_999L) return null
    return runCatching { Instant.ofEpochSecond(epochSecond, nano) }.getOrNull()
}
