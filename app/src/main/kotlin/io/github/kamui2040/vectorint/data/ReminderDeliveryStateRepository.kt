package io.github.kamui2040.vectorint.data

internal interface ReminderDeliveryStateRepository {
    suspend fun loadAcknowledgedKeys(): Set<String>

    suspend fun replaceAcknowledgedKeys(keys: Set<String>)
}
