package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class RecurringOccurrenceGeneratorTest {
    private val eur = CurrencyCode.of("EUR")

    @Test
    fun `monthly occurrence uses its exact booking date and stable month key`() {
        val item = item(firstOccurrence = LocalDate.of(2026, 1, 31))

        val occurrence = generate(item, YearMonth.of(2026, 2)).single()

        assertEquals(item.name, occurrence.name)
        assertEquals(LocalDate.of(2026, 2, 28), occurrence.expectedOn)
        assertEquals(BudgetMonth(YearMonth.of(2026, 2)), occurrence.budgetMonth)
        assertEquals(ActivitySource.Recurring(item.id, "2026-02"), occurrence.source)
    }

    @Test
    fun `following-month assignment separates booking date from budget month`() {
        val item =
            item(firstOccurrence = LocalDate.of(2026, 1, 31)).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 1, 31),
                        countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                    ),
            )

        val occurrence = generate(item, YearMonth.of(2026, 2)).single()

        assertEquals(LocalDate.of(2026, 1, 31), occurrence.expectedOn)
        assertEquals(BudgetMonth(YearMonth.of(2026, 2)), occurrence.budgetMonth)
        assertEquals(ActivitySource.Recurring(item.id, "2026-01"), occurrence.source)
    }

    @Test
    fun `weekly recurrence creates every occurrence assigned to the month`() {
        val item =
            item(firstOccurrence = LocalDate.of(2026, 2, 2)).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 2, 2),
                        interval = RecurrenceInterval(1, RecurrenceUnit.WEEKS),
                    ),
            )
        var id = 0

        val occurrences =
            RecurringOccurrenceGenerator.generate(
                item = item,
                month = BudgetMonth(YearMonth.of(2026, 2)),
                today = LocalDate.of(2026, 1, 1),
                autoBookedAt = { date -> date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant() },
                activityId = { ActivityId("activity-${id++}") },
            )

        assertEquals(4, occurrences.size)
        assertEquals(
            listOf("2026-02-02", "2026-02-09", "2026-02-16", "2026-02-23"),
            occurrences.map { (it.source as ActivitySource.Recurring).occurrenceKey },
        )
    }

    @Test
    fun `income and expense use the same scheduling path`() {
        val expense = generate(item("expense", Direction.EXPENSE), YearMonth.of(2026, 2)).single()
        val income = generate(item("income", Direction.INCOME), YearMonth.of(2026, 2)).single()

        assertEquals(Direction.EXPENSE, expense.direction)
        assertEquals(Direction.INCOME, income.direction)
        assertEquals(expense.amount, income.amount)
    }

    @Test
    fun `every generated occurrence keeps its recurring category`() {
        val categorized = item().copy(categoryId = PredefinedCategory.INSURANCE.id)

        val occurrence = generate(categorized, YearMonth.of(2026, 2)).single()

        assertEquals(PredefinedCategory.INSURANCE.id, occurrence.categoryId)
    }

    @Test
    fun `every generated occurrence keeps its recurring account`() {
        val accountId = AccountId("paypal")
        val assigned = item().copy(accountId = accountId)

        val occurrence = generate(assigned, YearMonth.of(2026, 2)).single()

        assertEquals(accountId, occurrence.accountId)
    }

    @Test
    fun `following-month expected income remains an explicit calculation choice`() {
        val salary =
            item("salary", Direction.INCOME, LocalDate.of(2026, 1, 31)).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 1, 31),
                        countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                    ),
            )
        val february = BudgetMonth(YearMonth.of(2026, 2))
        val occurrence = generate(salary, february.value, today = LocalDate.of(2026, 1, 30)).single()
        val funds = CurrentFunds(Money(100_000, eur), Instant.parse("2026-01-01T00:00:00Z"))

        val conservative =
            AvailableFundsCalculator.calculate(funds, february, listOf(occurrence)) as AvailableFundsResult.Available
        val optedIn =
            AvailableFundsCalculator.calculate(
                funds,
                february,
                listOf(occurrence),
                CalculationPolicy(includeExpectedIncome = true),
            ) as AvailableFundsResult.Available

        assertEquals(Money(100_000, eur), conservative.availableNow)
        assertEquals(Money(101_000, eur), optedIn.availableNow)
    }

    @Test
    fun `income stays expected before its date and confirms when that date arrives`() {
        val salary = item("salary", Direction.INCOME, LocalDate.of(2026, 9, 5))
        val month = YearMonth.of(2026, 9)
        val funds = CurrentFunds(Money(100_000, eur), Instant.parse("2026-09-01T00:00:00Z"))

        val before = generate(salary, month, LocalDate.of(2026, 9, 3)).single()
        val due = generate(salary, month, LocalDate.of(2026, 9, 5)).single()
        val beforeResult =
            AvailableFundsCalculator.calculate(funds, BudgetMonth(month), listOf(before)) as AvailableFundsResult.Available
        val dueResult =
            AvailableFundsCalculator.calculate(funds, BudgetMonth(month), listOf(due)) as AvailableFundsResult.Available

        assertEquals(ActivityState.PLANNED, before.state)
        assertEquals(Money(100_000, eur), beforeResult.availableNow)
        assertEquals(ActivityState.CONFIRMED, due.state)
        assertEquals(Instant.parse("2026-09-05T00:00:00Z"), due.bookedAt)
        assertEquals(Money(101_000, eur), dueResult.availableNow)
    }

    @Test
    fun `date range and any-time occurrences confirm on their final day`() {
        val range =
            item(firstOccurrence = LocalDate.of(2026, 9, 5)).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 9, 5),
                        timing = OccurrenceTiming.DateRange(4),
                    ),
            )
        val anyTime =
            item(firstOccurrence = LocalDate.of(2026, 9, 5)).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 9, 5),
                        timing = OccurrenceTiming.AnyTimeInMonth,
                    ),
            )

        val rangeBefore = generate(range, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 8)).single()
        val rangeDue = generate(range, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 9)).single()
        val anyTimeBefore = generate(anyTime, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 29)).single()
        val anyTimeDue = generate(anyTime, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 30)).single()

        assertEquals(ActivityState.PLANNED, rangeBefore.state)
        assertEquals(LocalDate.of(2026, 9, 9), rangeBefore.expectedOn)
        assertEquals(ActivityState.CONFIRMED, rangeDue.state)
        assertEquals(Instant.parse("2026-09-09T00:00:00Z"), rangeDue.bookedAt)
        assertEquals(ActivityState.PLANNED, anyTimeBefore.state)
        assertEquals(null, anyTimeBefore.expectedOn)
        assertEquals(ActivityState.CONFIRMED, anyTimeDue.state)
        assertEquals(Instant.parse("2026-09-30T00:00:00Z"), anyTimeDue.bookedAt)
    }

    @Test
    fun `manual confirmation toggle prevents automatic confirmation`() {
        val salary =
            item("salary", Direction.INCOME, LocalDate.of(2026, 9, 5)).copy(
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 9, 5),
                        requireManualConfirmation = true,
                    ),
            )

        val occurrence = generate(salary, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 30)).single()

        assertEquals(ActivityState.PLANNED, occurrence.state)
        assertEquals(null, occurrence.bookedAt)
    }

    @Test
    fun `months outside schedule produce no occurrence`() {
        val item = item(firstOccurrence = LocalDate.of(2026, 3, 1))

        assertTrue(generate(item, YearMonth.of(2026, 2)).isEmpty())
    }

    private fun generate(
        item: RecurringItem,
        month: YearMonth,
        today: LocalDate = LocalDate.of(2026, 1, 1),
    ): List<ActivityEntry> =
        RecurringOccurrenceGenerator.generate(
            item = item,
            month = BudgetMonth(month),
            today = today,
            autoBookedAt = { date -> date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant() },
            activityId = { ActivityId("activity-$month") },
        )

    private fun item(
        id: String = "item",
        direction: Direction = Direction.EXPENSE,
        firstOccurrence: LocalDate = LocalDate.of(2026, 1, 1),
    ): RecurringItem =
        RecurringItem(
            id = RecurringItemId(id),
            name = "Synthetic item",
            direction = direction,
            amount = Money(1_000, eur),
            schedule = RecurringSchedule(firstOccurrence),
            tags = setOf(Tag("fixed")),
        )
}
