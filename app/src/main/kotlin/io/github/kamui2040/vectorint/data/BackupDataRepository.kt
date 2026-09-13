package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.asLegacyDefaultAccount

internal data class BackupData(
    val accounts: List<Account>,
    val activities: List<ActivityEntry>,
    val recurringItems: List<RecurringItem>,
    val customCategories: List<CustomCategory> = emptyList(),
) {
    constructor(
        currentFunds: CurrentFunds?,
        activities: List<ActivityEntry>,
        recurringItems: List<RecurringItem>,
        customCategories: List<CustomCategory> = emptyList(),
    ) : this(
        accounts = currentFunds?.let { listOf(it.asLegacyDefaultAccount()) }.orEmpty(),
        activities = activities,
        recurringItems = recurringItems,
        customCategories = customCategories,
    )
}

internal interface BackupDataRepository {
    suspend fun loadBackupData(): BackupData

    suspend fun replaceBackupData(data: BackupData)
}
