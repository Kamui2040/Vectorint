package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class ActivityTest {
    private val eur = CurrencyCode.of("EUR")

    @Test
    fun `confirmation keeps the same economic identity and metadata`() {
        val planned =
            ActivityEntry(
                id = ActivityId("rent"),
                direction = Direction.EXPENSE,
                amount = Money(50_000, eur),
                state = ActivityState.PLANNED,
                budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                source = ActivitySource.Recurring(RecurringItemId("lease"), "2026-09"),
                tags = setOf(Tag("housing")),
            )
        val bookedAt = Instant.parse("2026-09-10T10:15:30Z")

        val confirmed = planned.confirm(bookedAt)

        assertEquals(planned.id, confirmed.id)
        assertEquals(planned.source, confirmed.source)
        assertEquals(planned.tags, confirmed.tags)
        assertEquals(ActivityState.CONFIRMED, confirmed.state)
        assertEquals(bookedAt, confirmed.bookedAt)
    }

    @Test
    fun `confirmation is idempotent`() {
        val firstBooking = Instant.parse("2026-09-10T10:15:30Z")
        val confirmed = planned().confirm(firstBooking)

        val retried = confirmed.confirm(Instant.parse("2026-09-11T10:15:30Z"))

        assertSame(confirmed, retried)
        assertEquals(firstBooking, retried.bookedAt)
    }

    @Test
    fun `planned activity cannot carry a booking instant`() {
        assertThrows(IllegalArgumentException::class.java) {
            planned().copy(bookedAt = Instant.parse("2026-09-10T10:15:30Z"))
        }
    }

    @Test
    fun `confirmed activity requires a booking instant`() {
        assertThrows(IllegalArgumentException::class.java) {
            planned().copy(state = ActivityState.CONFIRMED)
        }
    }

    @Test
    fun `activity amounts cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            planned().copy(amount = Money(-1, eur))
        }
    }

    private fun planned(): ActivityEntry =
        ActivityEntry(
            id = ActivityId("activity"),
            direction = Direction.INCOME,
            amount = Money(1_000, eur),
            state = ActivityState.PLANNED,
            budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
        )
}
