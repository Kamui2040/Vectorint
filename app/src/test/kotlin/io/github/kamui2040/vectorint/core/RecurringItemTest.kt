package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringItemTest {
    @Test
    fun `monthly helper uses its first occurrence as the schedule anchor`() {
        val first = LocalDate.of(2026, 9, 30)

        val item =
            RecurringItem.monthly(
                id = RecurringItemId("rent"),
                name = "Rent",
                direction = Direction.EXPENSE,
                amount = Money(100_000, CurrencyCode.of("EUR")),
                firstOccurrence = first,
            )

        assertEquals(first, item.schedule.firstOccurrence)
        assertEquals(OccurrenceTiming.SpecificDate, item.schedule.timing)
        assertEquals(RecurrenceInterval.Monthly, item.schedule.interval)
        assertEquals(BudgetMonthAssignment.OCCURRENCE_MONTH, item.schedule.countsToward)
        assertEquals(false, item.schedule.requireManualConfirmation)
        assertNull(item.schedule.endsOn)
        assertNull(item.schedule.remindOn)
    }

    @Test
    fun `day and week intervals can produce multiple occurrences in a month`() {
        val daily =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 2, 1),
                interval = RecurrenceInterval(10, RecurrenceUnit.DAYS),
            )
        val fortnightly =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 2, 2),
                interval = RecurrenceInterval(2, RecurrenceUnit.WEEKS),
            )

        assertEquals(
            listOf(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 11), LocalDate.of(2026, 2, 21)),
            daily.occurrencesIn(YearMonth.of(2026, 2)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 16)),
            fortnightly.occurrencesIn(YearMonth.of(2026, 2)),
        )
    }

    @Test
    fun `calendar intervals stay anchored to the first occurrence`() {
        val quarterly =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 1, 31),
                interval = RecurrenceInterval(3, RecurrenceUnit.MONTHS),
            )
        val yearly =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2024, 2, 29),
                interval = RecurrenceInterval(1, RecurrenceUnit.YEARS),
            )

        assertEquals(emptyList<LocalDate>(), quarterly.occurrencesIn(YearMonth.of(2026, 2)))
        assertEquals(listOf(LocalDate.of(2026, 4, 30)), quarterly.occurrencesIn(YearMonth.of(2026, 4)))
        assertEquals(listOf(LocalDate.of(2025, 2, 28)), yearly.occurrencesIn(YearMonth.of(2025, 2)))
    }

    @Test
    fun `end date is inclusive and reminder date does not end the schedule`() {
        val schedule =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 1, 15),
                endsOn = LocalDate.of(2026, 3, 15),
                remindOn = LocalDate.of(2026, 2, 1),
            )

        assertEquals(listOf(LocalDate.of(2026, 3, 15)), schedule.occurrencesIn(YearMonth.of(2026, 3)))
        assertEquals(emptyList<LocalDate>(), schedule.occurrencesIn(YearMonth.of(2026, 4)))
    }

    @Test
    fun `date range books on its final date and any time books at month end`() {
        val range =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 2, 10),
                timing = OccurrenceTiming.DateRange(endOffsetDays = 5),
            )
        val anyTime =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 2, 10),
                timing = OccurrenceTiming.AnyTimeInMonth,
            )

        assertEquals(LocalDate.of(2026, 2, 15), range.nextOccurrenceOnOrAfter(LocalDate.of(2026, 2, 11)))
        assertEquals(LocalDate.of(2026, 2, 28), anyTime.nextOccurrenceOnOrAfter(LocalDate.of(2026, 2, 11)))
        assertNull(anyTime.expectedDateFor(LocalDate.of(2026, 2, 10)))
    }

    @Test
    fun `next occurrence handles long intervals without month scanning`() {
        val schedule =
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 6, 1),
                interval = RecurrenceInterval(5, RecurrenceUnit.YEARS),
            )

        assertEquals(LocalDate.of(2031, 6, 1), schedule.nextOccurrenceOnOrAfter(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `invalid schedule values fail closed`() {
        assertThrows(IllegalArgumentException::class.java) { RecurrenceInterval(0, RecurrenceUnit.MONTHS) }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceInterval(1_000, RecurrenceUnit.MONTHS) }
        assertThrows(IllegalArgumentException::class.java) { OccurrenceTiming.DateRange(31) }
        assertThrows(IllegalArgumentException::class.java) {
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 2, 1),
                endsOn = LocalDate.of(2026, 1, 31),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurringSchedule(
                firstOccurrence = LocalDate.of(2026, 2, 1),
                remindOn = LocalDate.of(2026, 1, 31),
            )
        }
    }
}
