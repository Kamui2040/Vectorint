package io.github.kamui2040.vectorint.core

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class RecurrenceUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

data class RecurrenceInterval(
    val every: Int,
    val unit: RecurrenceUnit,
) {
    init {
        require(every in 1..MAX_EVERY) { "Repeat interval must be between 1 and $MAX_EVERY" }
    }

    companion object {
        const val MAX_EVERY = 999

        val Monthly = RecurrenceInterval(every = 1, unit = RecurrenceUnit.MONTHS)
    }
}

enum class BudgetMonthAssignment {
    OCCURRENCE_MONTH,
    FOLLOWING_MONTH,
    ;

    fun sourceMonthFor(budgetMonth: YearMonth): YearMonth =
        when (this) {
            OCCURRENCE_MONTH -> budgetMonth
            FOLLOWING_MONTH -> budgetMonth.minusMonths(1)
        }
}

sealed interface OccurrenceTiming {
    fun bookingDateFor(occurrence: LocalDate): LocalDate

    fun expectedDateFor(occurrence: LocalDate): LocalDate? = bookingDateFor(occurrence)

    data object SpecificDate : OccurrenceTiming {
        override fun bookingDateFor(occurrence: LocalDate): LocalDate = occurrence
    }

    data class DateRange(
        val endOffsetDays: Int,
    ) : OccurrenceTiming {
        init {
            require(endOffsetDays in 0..MAX_END_OFFSET_DAYS) {
                "A date range must end within 30 days of its first date"
            }
        }

        override fun bookingDateFor(occurrence: LocalDate): LocalDate = occurrence.plusDays(endOffsetDays.toLong())

        companion object {
            const val MAX_END_OFFSET_DAYS = 30
        }
    }

    data object AnyTimeInMonth : OccurrenceTiming {
        override fun bookingDateFor(occurrence: LocalDate): LocalDate = YearMonth.from(occurrence).atEndOfMonth()

        override fun expectedDateFor(occurrence: LocalDate): LocalDate? = null
    }
}

data class RecurringSchedule(
    val firstOccurrence: LocalDate,
    val timing: OccurrenceTiming = OccurrenceTiming.SpecificDate,
    val interval: RecurrenceInterval = RecurrenceInterval.Monthly,
    val countsToward: BudgetMonthAssignment = BudgetMonthAssignment.OCCURRENCE_MONTH,
    val requireManualConfirmation: Boolean = false,
    val endsOn: LocalDate? = null,
    val remindOn: LocalDate? = null,
) {
    init {
        require(endsOn == null || !endsOn.isBefore(bookingDateFor(firstOccurrence))) {
            "End date must not precede the first booking date"
        }
        require(remindOn == null || !remindOn.isBefore(firstOccurrence)) {
            "Reminder date must not precede the first occurrence"
        }
    }

    fun occurrencesIn(month: YearMonth): List<LocalDate> {
        val rangeStart = maxOf(month.atDay(1), firstOccurrence)
        val rangeEnd = month.atEndOfMonth()
        if (rangeStart.isAfter(rangeEnd)) return emptyList()

        return (
            when (interval.unit) {
                RecurrenceUnit.DAYS -> fixedDayOccurrences(rangeStart, rangeEnd, interval.every.toLong())
                RecurrenceUnit.WEEKS -> fixedDayOccurrences(rangeStart, rangeEnd, interval.every.toLong() * 7L)
                RecurrenceUnit.MONTHS -> calendarMonthOccurrence(month)
                RecurrenceUnit.YEARS -> calendarYearOccurrence(month)
            }
        ).filter(::isActive)
    }

    fun nextOccurrenceOnOrAfter(date: LocalDate): LocalDate? {
        if (endsOn?.isBefore(date) == true) return null
        val searchFrom =
            when (val value = timing) {
                OccurrenceTiming.SpecificDate -> date
                is OccurrenceTiming.DateRange -> date.minusDays(value.endOffsetDays.toLong())
                OccurrenceTiming.AnyTimeInMonth -> YearMonth.from(date).atDay(1)
            }
        var anchor = nextAnchorOnOrAfter(searchFrom) ?: return null
        while (bookingDateFor(anchor).isBefore(date)) {
            anchor = nextAnchorOnOrAfter(anchor.plusDays(1)) ?: return null
        }
        return bookingDateFor(anchor).takeIf { endsOn == null || !it.isAfter(endsOn) }
    }

    fun bookingDateFor(occurrence: LocalDate): LocalDate = timing.bookingDateFor(occurrence)

    fun expectedDateFor(occurrence: LocalDate): LocalDate? = timing.expectedDateFor(occurrence)

    fun occurrenceKey(date: LocalDate): String =
        when (interval.unit) {
            RecurrenceUnit.MONTHS,
            RecurrenceUnit.YEARS,
            -> YearMonth.from(date).toString()

            RecurrenceUnit.DAYS,
            RecurrenceUnit.WEEKS,
            -> date.toString()
        }

    private fun fixedDayOccurrences(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        periodDays: Long,
    ): List<LocalDate> {
        val elapsedDays = ChronoUnit.DAYS.between(firstOccurrence, rangeStart)
        val intervalsToStart = if (elapsedDays <= 0L) 0L else ((elapsedDays - 1L) / periodDays) + 1L
        var occurrence = firstOccurrence.plusDays(Math.multiplyExact(intervalsToStart, periodDays))
        return buildList {
            while (!occurrence.isAfter(rangeEnd)) {
                add(occurrence)
                occurrence =
                    try {
                        occurrence.plusDays(periodDays)
                    } catch (_: DateTimeException) {
                        break
                    }
            }
        }
    }

    private fun calendarMonthOccurrence(month: YearMonth): List<LocalDate> {
        val elapsedMonths = ChronoUnit.MONTHS.between(YearMonth.from(firstOccurrence), month)
        if (elapsedMonths < 0L || elapsedMonths % interval.every.toLong() != 0L) return emptyList()
        val occurrence = firstOccurrence.plusMonths(elapsedMonths)
        return listOf(occurrence)
    }

    private fun calendarYearOccurrence(month: YearMonth): List<LocalDate> {
        val elapsedYears = month.year.toLong() - firstOccurrence.year.toLong()
        if (elapsedYears < 0L || elapsedYears % interval.every.toLong() != 0L) return emptyList()
        val occurrence = firstOccurrence.plusYears(elapsedYears)
        return occurrence.takeIf { YearMonth.from(it) == month }?.let(::listOf).orEmpty()
    }

    private fun nextAnchorOnOrAfter(date: LocalDate): LocalDate? {
        if (!date.isAfter(firstOccurrence)) return firstOccurrence
        return when (interval.unit) {
            RecurrenceUnit.DAYS -> nextFixedDayOccurrence(date, interval.every.toLong())
            RecurrenceUnit.WEEKS -> nextFixedDayOccurrence(date, interval.every.toLong() * 7L)
            RecurrenceUnit.MONTHS -> nextCalendarMonthOccurrence(date)
            RecurrenceUnit.YEARS -> nextCalendarYearOccurrence(date)
        }
    }

    private fun nextFixedDayOccurrence(
        date: LocalDate,
        periodDays: Long,
    ): LocalDate? =
        try {
            val elapsedDays = ChronoUnit.DAYS.between(firstOccurrence, date)
            val intervals = ((elapsedDays - 1L) / periodDays) + 1L
            firstOccurrence.plusDays(Math.multiplyExact(intervals, periodDays))
        } catch (_: ArithmeticException) {
            null
        } catch (_: DateTimeException) {
            null
        }

    private fun nextCalendarMonthOccurrence(date: LocalDate): LocalDate? =
        try {
            val elapsedMonths = ChronoUnit.MONTHS.between(YearMonth.from(firstOccurrence), YearMonth.from(date))
            var intervals = maxOf(0L, elapsedMonths / interval.every.toLong())
            var candidate = firstOccurrence.plusMonths(Math.multiplyExact(intervals, interval.every.toLong()))
            if (candidate.isBefore(date)) {
                intervals++
                candidate = firstOccurrence.plusMonths(Math.multiplyExact(intervals, interval.every.toLong()))
            }
            candidate
        } catch (_: ArithmeticException) {
            null
        } catch (_: DateTimeException) {
            null
        }

    private fun nextCalendarYearOccurrence(date: LocalDate): LocalDate? =
        try {
            val elapsedYears = date.year.toLong() - firstOccurrence.year.toLong()
            var intervals = maxOf(0L, elapsedYears / interval.every.toLong())
            var candidate = firstOccurrence.plusYears(Math.multiplyExact(intervals, interval.every.toLong()))
            if (candidate.isBefore(date)) {
                intervals++
                candidate = firstOccurrence.plusYears(Math.multiplyExact(intervals, interval.every.toLong()))
            }
            candidate
        } catch (_: ArithmeticException) {
            null
        } catch (_: DateTimeException) {
            null
        }

    private fun isActive(date: LocalDate): Boolean {
        val bookingDate = bookingDateFor(date)
        return !date.isBefore(firstOccurrence) && (endsOn == null || !bookingDate.isAfter(endsOn))
    }
}

data class ReminderLead(
    val daysBefore: Int,
) {
    init {
        require(daysBefore in 0..MAX_DAYS_BEFORE) { "Reminder lead must be between 0 and $MAX_DAYS_BEFORE days" }
    }

    companion object {
        const val MAX_DAYS_BEFORE = 3660
    }
}

data class ReminderSettings(
    val occurrence: ReminderLead? = null,
    val remind: ReminderLead? = null,
    val end: ReminderLead? = null,
)

data class RecurringItem(
    val id: RecurringItemId,
    val name: String,
    val accountId: AccountId = LEGACY_DEFAULT_ACCOUNT_ID,
    val direction: Direction,
    val amount: Money,
    val schedule: RecurringSchedule,
    val reminders: ReminderSettings = ReminderSettings(),
    val categoryId: CategoryId? = null,
    val tags: Set<Tag> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Recurring item name must not be blank" }
        require(amount.minorUnits >= 0) { "Recurring amount must be non-negative" }
    }

    companion object {
        fun monthly(
            id: RecurringItemId,
            name: String,
            direction: Direction,
            amount: Money,
            firstOccurrence: LocalDate,
        ): RecurringItem =
            RecurringItem(
                id = id,
                name = name,
                accountId = LEGACY_DEFAULT_ACCOUNT_ID,
                direction = direction,
                amount = amount,
                schedule = RecurringSchedule(firstOccurrence = firstOccurrence),
            )
    }
}
