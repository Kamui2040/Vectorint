package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val utc = ZoneOffset.UTC

    @Test
    fun `occurrence reminder uses the next exact occurrence`() {
        val item = item(firstOccurrence = LocalDate.of(2026, 1, 31), occurrenceLead = 0)

        val before = planner.plan(listOf(item), emptySet(), Instant.parse("2026-02-28T10:00:00Z"), utc)
        val after = planner.plan(listOf(item), emptySet(), Instant.parse("2026-03-01T10:00:00Z"), utc)

        assertEquals(
            LocalDate.of(2026, 2, 28),
            before.due
                .single()
                .key.anchorDate,
        )
        assertEquals(Instant.parse("2026-03-31T09:00:00Z"), after.nextEvaluationAt)
    }

    @Test
    fun `occurrence reminder uses the final day of a date range`() {
        val item =
            item(firstOccurrence = LocalDate.of(2026, 2, 5), occurrenceLead = 0).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 2, 5),
                        timing = OccurrenceTiming.DateRange(4),
                    ),
            )

        val plan = planner.plan(listOf(item), emptySet(), Instant.parse("2026-02-09T10:00:00Z"), utc)

        assertEquals(
            LocalDate.of(2026, 2, 9),
            plan.due
                .single()
                .key.anchorDate,
        )
    }

    @Test
    fun `remind and end notifications use one direction-neutral plan`() {
        val income =
            item(
                id = "income",
                direction = Direction.INCOME,
                remindOn = LocalDate.of(2026, 3, 1),
                remindLead = 0,
            )
        val expense =
            item(
                id = "expense",
                direction = Direction.EXPENSE,
                endsOn = LocalDate.of(2026, 3, 1),
                endLead = 0,
            )

        val plan = planner.plan(listOf(income, expense), emptySet(), Instant.parse("2026-03-01T10:00:00Z"), utc)

        assertEquals(setOf(ReminderKind.REMIND, ReminderKind.END), plan.due.map { it.key.kind }.toSet())
        assertEquals(setOf(Direction.INCOME, Direction.EXPENSE), plan.due.map { it.direction }.toSet())
    }

    @Test
    fun `remind me date remains eligible after the economic end date`() {
        val item =
            item(
                endsOn = LocalDate.of(2026, 2, 1),
                remindOn = LocalDate.of(2026, 3, 1),
                remindLead = 0,
            )

        val plan = planner.plan(listOf(item), emptySet(), Instant.parse("2026-03-01T10:00:00Z"), utc)

        assertEquals(listOf(ReminderKind.REMIND), plan.due.map { it.key.kind })
    }

    @Test
    fun `economic end prevents a later occurrence reminder`() {
        val item =
            item(
                firstOccurrence = LocalDate.of(2026, 1, 30),
                endsOn = LocalDate.of(2026, 3, 6),
                occurrenceLead = 0,
            )

        val plan = planner.plan(listOf(item), emptySet(), Instant.parse("2026-03-06T10:00:00Z"), utc)

        assertTrue(plan.due.isEmpty())
        assertTrue(plan.relevantKeys.isEmpty())
        assertEquals(null, plan.nextEvaluationAt)
    }

    @Test
    fun `acknowledged occurrence does not redeliver and retires the next day`() {
        val item = item(firstOccurrence = LocalDate.of(2026, 2, 15), occurrenceLead = 0)
        val now = Instant.parse("2026-02-15T10:00:00Z")
        val due = planner.plan(listOf(item), emptySet(), now, utc).due.single()

        val acknowledged = planner.plan(listOf(item), setOf(due.key.encoded), now, utc)

        assertTrue(acknowledged.due.isEmpty())
        assertEquals(Instant.parse("2026-02-16T00:05:00Z"), acknowledged.nextEvaluationAt)
        assertEquals(setOf(due.key.encoded), acknowledged.relevantKeys)
    }

    @Test
    fun `changing a reminder lead creates a new exact key`() {
        val oldItem = item(firstOccurrence = LocalDate.of(2026, 2, 15), occurrenceLead = 1)
        val newItem = item(firstOccurrence = LocalDate.of(2026, 2, 15), occurrenceLead = 2)
        val now = Instant.parse("2026-02-15T10:00:00Z")
        val oldKey =
            planner
                .plan(listOf(oldItem), emptySet(), now, utc)
                .due
                .single()
                .key.encoded

        val changed = planner.plan(listOf(newItem), setOf(oldKey), now, utc)

        assertEquals(1, changed.due.size)
        assertNotEquals(
            oldKey,
            changed.due
                .single()
                .key.encoded,
        )
    }

    @Test
    fun `local reminder time survives daylight-saving and zone changes`() {
        val item =
            item(
                remindOn = LocalDate.of(2026, 3, 29),
                remindLead = 0,
            )
        val berlin = ZoneId.of("Europe/Berlin")

        val berlinReminder =
            planner.plan(listOf(item), emptySet(), Instant.parse("2026-03-29T08:00:00Z"), berlin).due.single()
        val utcReminder =
            planner.plan(listOf(item), emptySet(), Instant.parse("2026-03-29T10:00:00Z"), utc).due.single()

        assertEquals(LocalTime.of(9, 0), berlinReminder.notifyAt.atZone(berlin).toLocalTime())
        assertEquals(LocalTime.of(9, 0), utcReminder.notifyAt.atZone(utc).toLocalTime())
        assertNotEquals(berlinReminder.notifyAt, utcReminder.notifyAt)
    }

    @Test
    fun `far-future first occurrence can produce a bounded catch-up reminder`() {
        val item =
            item(
                firstOccurrence = LocalDate.of(2036, 2, 1),
                occurrenceLead = ReminderLead.MAX_DAYS_BEFORE,
            )

        val plan = planner.plan(listOf(item), emptySet(), Instant.parse("2026-02-15T10:00:00Z"), utc)

        assertEquals(
            LocalDate.of(2036, 2, 1),
            plan.due
                .single()
                .key.anchorDate,
        )
    }

    private fun item(
        id: String = "item",
        direction: Direction = Direction.EXPENSE,
        firstOccurrence: LocalDate = LocalDate.of(2026, 1, 1),
        endsOn: LocalDate? = null,
        remindOn: LocalDate? = null,
        occurrenceLead: Int? = null,
        remindLead: Int? = null,
        endLead: Int? = null,
    ): RecurringItem =
        RecurringItem(
            id = RecurringItemId(id),
            name = "Synthetic item",
            direction = direction,
            amount = Money(1_000, CurrencyCode.of("EUR")),
            schedule =
                RecurringSchedule(
                    firstOccurrence = firstOccurrence,
                    endsOn = endsOn,
                    remindOn = remindOn,
                ),
            reminders =
                ReminderSettings(
                    occurrence = occurrenceLead?.let(::ReminderLead),
                    remind = remindLead?.let(::ReminderLead),
                    end = endLead?.let(::ReminderLead),
                ),
        )
}
