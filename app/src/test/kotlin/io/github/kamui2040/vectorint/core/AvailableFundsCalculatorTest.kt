package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class AvailableFundsCalculatorTest {
    private val eur = CurrencyCode.of("EUR")
    private val month = BudgetMonth(YearMonth.of(2026, 9))
    private val baselineInstant = Instant.parse("2026-09-10T12:00:00Z")
    private val baseline = CurrentFunds(Money(100_000, eur), baselineInstant)

    @Test
    fun `baseline is available when there is no activity`() {
        val result = available()

        assertEquals(Money(100_000, eur), result.confirmedFunds)
        assertEquals(Money(100_000, eur), result.availableNow)
    }

    @Test
    fun `excluded accounts and their entries do not affect Available now`() {
        val bank = account("bank", "Bank", 100_000, included = true)
        val savings = account("savings", "Savings", 500_000, included = false)
        val bankExpense =
            planned("bank-expense", Direction.EXPENSE, 10_000)
                .copy(accountId = bank.id)
                .confirm(baselineInstant.plusSeconds(1))
        val savingsExpense =
            planned("savings-expense", Direction.EXPENSE, 40_000)
                .copy(accountId = savings.id)

        val result = available(listOf(bank, savings), bankExpense, savingsExpense)

        assertEquals(Money(90_000, eur), result.confirmedFunds)
        assertEquals(Money.zero(eur), result.reservedExpenses)
        assertEquals(Money(90_000, eur), result.availableNow)
    }

    @Test
    fun `all accounts may be excluded explicitly`() {
        val savings = account("savings", "Savings", 500_000, included = false)

        val result = available(listOf(savings))

        assertEquals(Money.zero(eur), result.confirmedFunds)
        assertEquals(Money.zero(eur), result.availableNow)
    }

    @Test
    fun `each account uses its own balance capture time`() {
        val bank = account("bank", "Bank", 100_000, capturedAt = baselineInstant)
        val cash =
            account(
                "cash",
                "Cash",
                20_000,
                capturedAt = baselineInstant.minusSeconds(100),
            )
        val bookingTime = baselineInstant.minusSeconds(50)
        val bankExpense =
            planned("bank-expense", Direction.EXPENSE, 10_000)
                .copy(accountId = bank.id)
                .confirm(bookingTime)
        val cashExpense =
            planned("cash-expense", Direction.EXPENSE, 5_000)
                .copy(accountId = cash.id)
                .confirm(bookingTime)

        val result = available(listOf(bank, cash), bankExpense, cashExpense)

        assertEquals(Money(115_000, eur), result.confirmedFunds)
        assertEquals(Money(115_000, eur), result.availableNow)
    }

    @Test
    fun `unknown account assignment fails closed`() {
        val bank = account("bank", "Bank", 100_000)
        val activity = planned("purchase", Direction.EXPENSE, 2_000)

        val result = AvailableFundsCalculator.calculate(listOf(bank), month, listOf(activity))

        assertEquals(
            AvailableFundsResult.Unsafe(eur, setOf(UnsafeReason.UNKNOWN_ACCOUNT_ASSIGNMENT)),
            result,
        )
    }

    @Test
    fun `duplicate account names fail closed without changing case sensitivity`() {
        val first = account("bank", "Bank", 100_000)
        val duplicateName = account("cash", "bank", 20_000)

        val result = AvailableFundsCalculator.calculate(listOf(first, duplicateName), month, emptyList())

        assertEquals(
            AvailableFundsResult.Unsafe(eur, setOf(UnsafeReason.DUPLICATE_ACCOUNT_NAME)),
            result,
        )
    }

    @Test
    fun `planned expense is reserved once`() {
        val result = available(planned("rent", Direction.EXPENSE, 40_000))

        assertEquals(Money(40_000, eur), result.reservedExpenses)
        assertEquals(Money(60_000, eur), result.availableNow)
    }

    @Test
    fun `planned income is included once`() {
        val result =
            available(
                planned("salary", Direction.INCOME, 150_000),
                policy = CalculationPolicy(includeExpectedIncome = true),
            )

        assertEquals(Money(150_000, eur), result.plannedIncome)
        assertEquals(Money(250_000, eur), result.availableNow)
    }

    @Test
    fun `conservative default excludes expected income but still reserves expenses`() {
        val result =
            available(
                planned("salary", Direction.INCOME, 150_000),
                planned("rent", Direction.EXPENSE, 40_000),
            )

        assertEquals(Money.zero(eur), result.plannedIncome)
        assertEquals(Money(40_000, eur), result.reservedExpenses)
        assertEquals(Money(60_000, eur), result.availableNow)
    }

    @Test
    fun `expense confirmation replaces reservation without double counting`() {
        val planned = recurring("rent", Direction.EXPENSE, 40_000)
        val before = available(planned)
        val after = available(planned.confirm(Instant.parse("2026-09-11T08:00:00Z")))

        assertEquals(Money(60_000, eur), before.availableNow)
        assertEquals(Money(60_000, eur), after.availableNow)
        assertEquals(Money.zero(eur), after.reservedExpenses)
        assertEquals(Money(60_000, eur), after.confirmedFunds)
    }

    @Test
    fun `income confirmation replaces expectation without double counting`() {
        val planned = recurring("salary", Direction.INCOME, 150_000)
        val policy = CalculationPolicy(includeExpectedIncome = true)
        val before = available(planned, policy = policy)
        val after =
            available(
                planned.confirm(Instant.parse("2026-09-11T08:00:00Z")),
                policy = policy,
            )

        assertEquals(Money(250_000, eur), before.availableNow)
        assertEquals(Money(250_000, eur), after.availableNow)
        assertEquals(Money.zero(eur), after.plannedIncome)
        assertEquals(Money(250_000, eur), after.confirmedFunds)
    }

    @Test
    fun `confirmed activity at the Current funds baseline is not replayed`() {
        val entry = planned("purchase", Direction.EXPENSE, 10_000).confirm(baselineInstant)

        assertEquals(Money(100_000, eur), available(entry).availableNow)
    }

    @Test
    fun `confirmed activity before the Current funds baseline is not replayed`() {
        val entry =
            planned("purchase", Direction.EXPENSE, 10_000)
                .confirm(Instant.parse("2026-09-09T12:00:00Z"))

        assertEquals(Money(100_000, eur), available(entry).availableNow)
    }

    @Test
    fun `confirmed activity after the Current funds baseline changes funds once`() {
        val expense =
            planned("purchase", Direction.EXPENSE, 10_000)
                .confirm(Instant.parse("2026-09-10T12:00:01Z"))
        val income =
            planned("refund", Direction.INCOME, 2_500)
                .confirm(Instant.parse("2026-09-10T12:00:02Z"))

        assertEquals(Money(92_500, eur), available(expense, income).availableNow)
    }

    @Test
    fun `planned activity from another calendar month is excluded`() {
        val october =
            planned("october", Direction.EXPENSE, 50_000).copy(
                budgetMonth = BudgetMonth(YearMonth.of(2026, 10)),
            )

        assertEquals(Money(100_000, eur), available(october).availableNow)
    }

    @Test
    fun `booking time affects pooled funds independently from budget month assignment`() {
        val augustAssigned =
            planned("late", Direction.INCOME, 10_000)
                .copy(
                    budgetMonth = BudgetMonth(YearMonth.of(2026, 8)),
                ).confirm(Instant.parse("2026-09-11T08:00:00Z"))

        assertEquals(Money(110_000, eur), available(augustAssigned).availableNow)
    }

    @Test
    fun `negative Available now is a valid result`() {
        val result = available(planned("large bill", Direction.EXPENSE, 120_000))

        assertEquals(Money(-20_000, eur), result.availableNow)
    }

    @Test
    fun `one-off planned activity uses the same direction model`() {
        val result =
            available(
                planned("reimbursement", Direction.INCOME, 5_000),
                planned("purchase", Direction.EXPENSE, 2_000),
                policy = CalculationPolicy(includeExpectedIncome = true),
            )

        assertEquals(Money(103_000, eur), result.availableNow)
    }

    @Test
    fun `tags are calculation-neutral metadata`() {
        val untagged = planned("purchase", Direction.EXPENSE, 2_000)
        val tagged = untagged.copy(tags = setOf(Tag("household"), Tag("shared")))

        assertEquals(available(untagged), available(tagged))
    }

    @Test
    fun `duplicate activity identity fails closed`() {
        val entry = planned("purchase", Direction.EXPENSE, 2_000)

        val result = AvailableFundsCalculator.calculate(baseline, month, listOf(entry, entry))

        assertEquals(
            AvailableFundsResult.Unsafe(eur, setOf(UnsafeReason.DUPLICATE_ACTIVITY_ID)),
            result,
        )
    }

    @Test
    fun `duplicate recurring occurrence identity fails closed`() {
        val first = recurring("rent-a", Direction.EXPENSE, 40_000)
        val duplicateEconomicEvent = first.copy(id = ActivityId("rent-b"))

        val result =
            AvailableFundsCalculator.calculate(
                baseline,
                month,
                listOf(first, duplicateEconomicEvent),
            )

        assertEquals(
            AvailableFundsResult.Unsafe(
                eur,
                setOf(UnsafeReason.DUPLICATE_RECURRING_OCCURRENCE),
            ),
            result,
        )
    }

    @Test
    fun `mixed currency input fails closed`() {
        val usdEntry =
            planned("usd", Direction.INCOME, 5_000).copy(
                amount = Money(5_000, CurrencyCode.of("USD")),
            )

        val result = AvailableFundsCalculator.calculate(baseline, month, listOf(usdEntry))

        assertEquals(
            AvailableFundsResult.Unsafe(eur, setOf(UnsafeReason.CURRENCY_MISMATCH)),
            result,
        )
    }

    @Test
    fun `arithmetic overflow fails closed`() {
        val highBaseline = CurrentFunds(Money(Long.MAX_VALUE, eur), baselineInstant)
        val income = planned("income", Direction.INCOME, 1).confirm(baselineInstant.plusSeconds(1))

        val result = AvailableFundsCalculator.calculate(highBaseline, month, listOf(income))

        assertEquals(
            AvailableFundsResult.Unsafe(eur, setOf(UnsafeReason.ARITHMETIC_OVERFLOW)),
            result,
        )
    }

    @Test
    fun `calculation does not depend on process locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val german =
                available(
                    planned("income", Direction.INCOME, 12_345),
                    policy = CalculationPolicy(includeExpectedIncome = true),
                )
            Locale.setDefault(Locale.US)
            val us =
                available(
                    planned("income", Direction.INCOME, 12_345),
                    policy = CalculationPolicy(includeExpectedIncome = true),
                )

            assertEquals(german, us)
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun available(
        vararg activity: ActivityEntry,
        policy: CalculationPolicy = CalculationPolicy(),
    ): AvailableFundsResult.Available {
        val result = AvailableFundsCalculator.calculate(baseline, month, activity.toList(), policy)
        assertTrue("Expected a safe Available now result but got $result", result is AvailableFundsResult.Available)
        return result as AvailableFundsResult.Available
    }

    private fun available(
        accounts: List<Account>,
        vararg activity: ActivityEntry,
    ): AvailableFundsResult.Available {
        val result = AvailableFundsCalculator.calculate(accounts, month, activity.toList())
        assertTrue("Expected a safe Available now result but got $result", result is AvailableFundsResult.Available)
        return result as AvailableFundsResult.Available
    }

    private fun account(
        id: String,
        name: String,
        minorUnits: Long,
        included: Boolean = true,
        capturedAt: Instant = baselineInstant,
    ): Account =
        Account(
            id = AccountId(id),
            name = name,
            currentFunds = CurrentFunds(Money(minorUnits, eur), capturedAt),
            includeInAvailableNow = included,
        )

    private fun planned(
        id: String,
        direction: Direction,
        minorUnits: Long,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = direction,
            amount = Money(minorUnits, eur),
            state = ActivityState.PLANNED,
            budgetMonth = month,
            expectedOn = LocalDate.of(2026, 9, 20),
        )

    private fun recurring(
        id: String,
        direction: Direction,
        minorUnits: Long,
    ): ActivityEntry =
        planned(id, direction, minorUnits).copy(
            source =
                ActivitySource.Recurring(
                    itemId = RecurringItemId("item-$id"),
                    occurrenceKey = "2026-09",
                ),
        )
}
