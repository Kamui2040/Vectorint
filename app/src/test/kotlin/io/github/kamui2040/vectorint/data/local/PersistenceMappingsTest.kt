package io.github.kamui2040.vectorint.data.local

import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderSettings
import io.github.kamui2040.vectorint.core.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class PersistenceMappingsTest {
    private val eur = CurrencyCode.of("EUR")

    @Test
    fun `account preserves its balance flags and exact sub-millisecond capture instant`() {
        val funds =
            CurrentFunds(
                amount = Money(123_456, eur),
                capturedAt = Instant.parse("2026-09-05T12:34:56.123456789Z"),
            )
        val account =
            Account(
                id = AccountId("cash"),
                name = "Cash",
                currentFunds = funds,
                includeInAvailableNow = false,
            )

        assertEquals(account, account.toEntity().toDomain())
    }

    @Test
    fun `planned one-off activity round trips with metadata`() {
        val activity =
            ActivityEntry(
                id = ActivityId("one-off"),
                direction = Direction.EXPENSE,
                amount = Money(4_500, eur),
                state = ActivityState.PLANNED,
                budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                expectedOn = LocalDate.of(2026, 9, 20),
                tags = setOf(Tag("household"), Tag("shared")),
            )

        assertEquals(activity, activity.toRecord().toDomain())
    }

    @Test
    fun `confirmed recurring activity preserves economic identity and exact booking time`() {
        val activity =
            ActivityEntry(
                id = ActivityId("rent-2026-09"),
                direction = Direction.EXPENSE,
                amount = Money(80_000, eur),
                state = ActivityState.CONFIRMED,
                budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                expectedOn = LocalDate.of(2026, 9, 1),
                bookedAt = Instant.parse("2026-09-01T07:30:00.000000001Z"),
                source =
                    ActivitySource.Recurring(
                        itemId = RecurringItemId("rent"),
                        occurrenceKey = "2026-09",
                    ),
                tags = setOf(Tag("housing")),
            )

        assertEquals(activity, activity.toRecord().toDomain())
    }

    @Test
    fun `all recurring interval units round trip with exact dates`() {
        val units = RecurrenceUnit.entries

        units.forEach { unit ->
            val item = recurringItem(unit)
            assertEquals(item, item.toRecord().toDomain())
        }
    }

    @Test
    fun `recurring lifecycle reminders and tags round trip independently`() {
        val item =
            recurringItem(RecurrenceUnit.MONTHS).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 1, 31),
                        interval = RecurrenceInterval(3, RecurrenceUnit.MONTHS),
                        countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                        endsOn = LocalDate.of(2027, 1, 31),
                        remindOn = LocalDate.of(2026, 11, 1),
                    ),
                reminders =
                    ReminderSettings(
                        occurrence = ReminderLead(2),
                        remind = ReminderLead(14),
                        end = ReminderLead(30),
                    ),
                tags = setOf(Tag("income"), Tag("annual review")),
            )

        assertEquals(item, item.toRecord().toDomain())
    }

    @Test
    fun `partial stored booking instant fails closed`() {
        val malformed =
            ActivityRecord(
                activity = validActivityEntity().copy(bookedAtEpochSecond = 1L),
                tags = emptyList(),
            )

        assertThrows(IllegalArgumentException::class.java) { malformed.toDomain() }
    }

    @Test
    fun `out-of-range stored nanoseconds fail closed`() {
        val malformedAccount =
            AccountEntity(
                id = "cash",
                name = "Cash",
                minorUnits = 1,
                currencyCode = "EUR",
                capturedAtEpochSecond = 1,
                capturedAtNano = 1_000_000_000,
            )
        val malformedActivity =
            ActivityRecord(
                activity =
                    validActivityEntity().copy(
                        state = "confirmed",
                        bookedAtEpochSecond = 1,
                        bookedAtNano = -1,
                    ),
                tags = emptyList(),
            )

        assertThrows(IllegalArgumentException::class.java) { malformedAccount.toDomain() }
        assertThrows(IllegalArgumentException::class.java) { malformedActivity.toDomain() }
    }

    @Test
    fun `one-off rows carrying recurring identity fail closed`() {
        val malformed =
            ActivityRecord(
                activity = validActivityEntity().copy(recurringItemId = "unexpected"),
                tags = emptyList(),
            )

        assertThrows(IllegalArgumentException::class.java) { malformed.toDomain() }
    }

    @Test
    fun `unknown stored enum values fail closed`() {
        val malformed =
            ActivityRecord(
                activity = validActivityEntity().copy(direction = "sideways"),
                tags = emptyList(),
            )

        assertThrows(IllegalStateException::class.java) { malformed.toDomain() }
    }

    @Test
    fun `malformed stored schedule values fail closed`() {
        val unknownUnit =
            RecurringItemRecord(
                item = validRecurringItemEntity().copy(repeatUnit = "fortnights"),
                tags = emptyList(),
            )
        val unknownAssignment =
            RecurringItemRecord(
                item = validRecurringItemEntity().copy(countsToward = "sometimes"),
                tags = emptyList(),
            )
        val unknownTiming =
            RecurringItemRecord(
                item = validRecurringItemEntity().copy(scheduleTimingKind = "eventually"),
                tags = emptyList(),
            )
        val malformedRange =
            RecurringItemRecord(
                item =
                    validRecurringItemEntity().copy(
                        scheduleTimingKind = "date_range",
                        periodEndOffsetDays = null,
                    ),
                tags = emptyList(),
            )

        assertThrows(IllegalStateException::class.java) { unknownUnit.toDomain() }
        assertThrows(IllegalStateException::class.java) { unknownAssignment.toDomain() }
        assertThrows(IllegalStateException::class.java) { unknownTiming.toDomain() }
        assertThrows(IllegalArgumentException::class.java) { malformedRange.toDomain() }
    }

    private fun recurringItem(unit: RecurrenceUnit): RecurringItem =
        RecurringItem(
            id = RecurringItemId("salary"),
            name = "Salary",
            direction = Direction.INCOME,
            amount = Money(250_000, eur),
            schedule =
                RecurringSchedule(
                    firstOccurrence = LocalDate.of(2026, 1, 1),
                    interval = RecurrenceInterval(2, unit),
                ),
        )

    private fun validActivityEntity(): ActivityEntity =
        ActivityEntity(
            id = "activity",
            direction = "expense",
            minorUnits = 1_000,
            currencyCode = "EUR",
            state = "planned",
            budgetMonth = "2026-09",
            expectedOnEpochDay = null,
            bookedAtEpochSecond = null,
            bookedAtNano = null,
            sourceKind = "one_off",
            recurringItemId = null,
            recurringOccurrenceKey = null,
        )

    private fun validRecurringItemEntity(): RecurringItemEntity =
        RecurringItemEntity(
            id = "item",
            name = "Item",
            direction = "expense",
            minorUnits = 1_000,
            currencyCode = "EUR",
            legacyTimingKind = "exact_recurrence",
            legacyTimingFirstDay = null,
            legacyTimingLastDay = null,
            legacyStartsOnEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            firstOccurrenceEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            repeatEvery = 1,
            repeatUnit = "months",
            countsToward = "occurrence_month",
            endsOnEpochDay = null,
            remindOnEpochDay = null,
            occurrenceReminderDays = null,
            remindReminderDays = null,
            endReminderDays = null,
        )
}
