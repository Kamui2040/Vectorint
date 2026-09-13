package io.github.kamui2040.vectorint.core

import java.time.Instant
import java.time.LocalDate

object RecurringOccurrenceGenerator {
    fun generate(
        item: RecurringItem,
        month: BudgetMonth,
        today: LocalDate,
        autoBookedAt: (LocalDate) -> Instant,
        activityId: () -> ActivityId,
    ): List<ActivityEntry> {
        val occurrenceMonth = item.schedule.countsToward.sourceMonthFor(month.value)
        return item.schedule.occurrencesIn(occurrenceMonth).map { occurrenceDate ->
            val bookingDate = item.schedule.bookingDateFor(occurrenceDate)
            val isAutomaticallyConfirmed =
                !item.schedule.requireManualConfirmation && !bookingDate.isAfter(today)
            ActivityEntry(
                id = activityId(),
                name = item.name,
                accountId = item.accountId,
                direction = item.direction,
                amount = item.amount,
                state = if (isAutomaticallyConfirmed) ActivityState.CONFIRMED else ActivityState.PLANNED,
                budgetMonth = month,
                expectedOn = item.schedule.expectedDateFor(occurrenceDate),
                bookedAt = if (isAutomaticallyConfirmed) autoBookedAt(bookingDate) else null,
                source =
                    ActivitySource.Recurring(
                        itemId = item.id,
                        occurrenceKey = item.schedule.occurrenceKey(occurrenceDate),
                    ),
                categoryId = item.categoryId,
                tags = item.tags,
            )
        }
    }
}
