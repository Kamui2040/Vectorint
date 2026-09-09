package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class RecurringOccurrenceCoordinatorTest {
    private val eur = CurrencyCode.of("EUR")
    private val month = BudgetMonth(YearMonth.of(2026, 9))

    @Test
    fun `active items are generated and ensured as one batch`() =
        runBlocking {
            val repository = FakeRecurringOccurrenceRepository(listOf(item("rent"), item("salary", Direction.INCOME)))
            var nextId = 0
            val coordinator =
                RecurringOccurrenceCoordinator(
                    repository,
                    RecurringOccurrenceActivityIdFactory { ActivityId("activity-${nextId++}") },
                )

            val refreshed = coordinator.refresh(month, eur)

            assertEquals(listOf("activity-0", "activity-1"), refreshed.map { it.id.value })
            assertEquals(1, repository.ensureCalls)
            assertEquals(refreshed, repository.lastEnsured)
        }

    @Test
    fun `inactive items create no activity and do not write an empty batch`() =
        runBlocking {
            val future =
                item("future").copy(
                    schedule = RecurringSchedule(firstOccurrence = LocalDate.of(2026, 10, 1)),
                )
            val repository = FakeRecurringOccurrenceRepository(listOf(future))
            val coordinator =
                RecurringOccurrenceCoordinator(
                    repository,
                    RecurringOccurrenceActivityIdFactory { ActivityId("unused") },
                )

            assertEquals(emptyList<ActivityEntry>(), coordinator.refresh(month, eur))
            assertEquals(0, repository.ensureCalls)
        }

    @Test
    fun `currency mismatch fails closed before any occurrence is stored`() {
        val mismatched = item("rent").copy(amount = Money(40_000, CurrencyCode.of("USD")))
        val repository = FakeRecurringOccurrenceRepository(listOf(mismatched))
        val coordinator = RecurringOccurrenceCoordinator(repository)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { coordinator.refresh(month, eur) }
        }
        assertEquals(0, repository.ensureCalls)
    }

    @Test
    fun `stored occurrence returned by the repository remains authoritative`() =
        runBlocking {
            val stored =
                ActivityEntry(
                    id = ActivityId("stored"),
                    direction = Direction.EXPENSE,
                    amount = Money(41_000, eur),
                    state = ActivityState.CONFIRMED,
                    budgetMonth = month,
                    bookedAt = Instant.parse("2026-09-02T08:00:00Z"),
                )
            val repository = FakeRecurringOccurrenceRepository(listOf(item("rent")), ensuredResult = listOf(stored))

            assertEquals(listOf(stored), RecurringOccurrenceCoordinator(repository).refresh(month, eur))
        }

    private fun item(
        id: String,
        direction: Direction = Direction.EXPENSE,
    ): RecurringItem =
        RecurringItem(
            id = RecurringItemId(id),
            name = id,
            direction = direction,
            amount = Money(40_000, eur),
            schedule = RecurringSchedule(LocalDate.of(2026, 1, 1)),
        )
}

private class FakeRecurringOccurrenceRepository(
    private val items: List<RecurringItem>,
    private val ensuredResult: List<ActivityEntry>? = null,
) : RecurringOccurrenceRepository {
    var ensureCalls = 0
    var lastEnsured: List<ActivityEntry> = emptyList()

    override suspend fun loadRecurringItems(): List<RecurringItem> = items

    override suspend fun ensureRecurringOccurrences(activities: List<ActivityEntry>): List<ActivityEntry> {
        ensureCalls++
        lastEnsured = activities
        return ensuredResult ?: activities
    }
}
