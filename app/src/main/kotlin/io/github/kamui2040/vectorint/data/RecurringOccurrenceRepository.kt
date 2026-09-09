package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.RecurringItem

internal interface RecurringOccurrenceRepository {
    suspend fun loadRecurringItems(): List<RecurringItem>

    suspend fun ensureRecurringOccurrences(activities: List<ActivityEntry>): List<ActivityEntry>
}
