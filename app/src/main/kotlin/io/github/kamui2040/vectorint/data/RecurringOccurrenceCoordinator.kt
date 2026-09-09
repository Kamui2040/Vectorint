package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.RecurringOccurrenceGenerator
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal fun interface RecurringOccurrenceActivityIdFactory {
    fun create(): ActivityId
}

internal fun interface RecurringOccurrenceUpdater {
    suspend fun refresh(
        month: BudgetMonth,
        currency: CurrencyCode,
    ): List<ActivityEntry>
}

internal class RecurringOccurrenceCoordinator(
    private val repository: RecurringOccurrenceRepository,
    private val idFactory: RecurringOccurrenceActivityIdFactory =
        RecurringOccurrenceActivityIdFactory { ActivityId(UUID.randomUUID().toString()) },
    private val clock: Clock = Clock.systemDefaultZone(),
) : RecurringOccurrenceUpdater {
    override suspend fun refresh(
        month: BudgetMonth,
        currency: CurrencyCode,
    ): List<ActivityEntry> {
        val occurrences =
            repository.loadRecurringItems().flatMap { item ->
                require(item.amount.currency == currency) {
                    "Recurring activity currency must match Current funds"
                }
                RecurringOccurrenceGenerator.generate(
                    item = item,
                    month = month,
                    today = LocalDate.now(clock),
                    autoBookedAt = { date -> date.atStartOfDay(clock.zone).toInstant() },
                    activityId = idFactory::create,
                )
            }
        return if (occurrences.isEmpty()) {
            emptyList()
        } else {
            repository.ensureRecurringOccurrences(occurrences)
        }
    }
}
