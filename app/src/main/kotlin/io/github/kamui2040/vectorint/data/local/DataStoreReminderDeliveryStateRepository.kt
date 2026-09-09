package io.github.kamui2040.vectorint.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.kamui2040.vectorint.data.ReminderDeliveryStateRepository
import kotlinx.coroutines.flow.first

private val REMINDER_ACKNOWLEDGEMENTS =
    stringSetPreferencesKey("reminder_delivery_acknowledgements_v1")

internal class DataStoreReminderDeliveryStateRepository(
    private val dataStore: DataStore<Preferences>,
) : ReminderDeliveryStateRepository {
    override suspend fun loadAcknowledgedKeys(): Set<String> =
        dataStore.data
            .first()[REMINDER_ACKNOWLEDGEMENTS]
            ?.toSet()
            .orEmpty()

    override suspend fun replaceAcknowledgedKeys(keys: Set<String>) {
        dataStore.edit { preferences ->
            if (keys.isEmpty()) {
                preferences.remove(REMINDER_ACKNOWLEDGEMENTS)
            } else {
                preferences[REMINDER_ACKNOWLEDGEMENTS] = keys.toSet()
            }
        }
    }
}
