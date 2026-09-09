package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.RecurringItem

internal data class BackupData(
    val currentFunds: CurrentFunds?,
    val activities: List<ActivityEntry>,
    val recurringItems: List<RecurringItem>,
    val customCategories: List<CustomCategory> = emptyList(),
)

internal interface BackupDataRepository {
    suspend fun loadBackupData(): BackupData

    suspend fun replaceBackupData(data: BackupData)
}
