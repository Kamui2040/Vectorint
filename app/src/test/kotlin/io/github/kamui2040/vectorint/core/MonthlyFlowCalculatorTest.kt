package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthlyFlowCalculatorTest {
    private val eur = CurrencyCode.of("EUR")
    private val september = BudgetMonth(YearMonth.of(2026, 9))

    @Test
    fun `summary includes planned and confirmed flow assigned to the selected month`() {
        val result =
            MonthlyFlowCalculator.calculate(
                currency = eur,
                month = september,
                activity =
                    listOf(
                        entry("income", Direction.INCOME, 150_000),
                        entry("expense", Direction.EXPENSE, 90_000),
                        entry("other-month", Direction.EXPENSE, 50_000, BudgetMonth(YearMonth.of(2026, 10))),
                    ),
            )

        assertEquals(
            MonthlyFlowResult.Summary(
                income = Money(150_000, eur),
                expenses = Money(90_000, eur),
                net = Money(60_000, eur),
            ),
            result,
        )
    }

    @Test
    fun `negative monthly net remains valid`() {
        val result =
            MonthlyFlowCalculator.calculate(
                currency = eur,
                month = september,
                activity = listOf(entry("expense", Direction.EXPENSE, 90_000)),
            ) as MonthlyFlowResult.Summary

        assertEquals(Money(-90_000, eur), result.net)
    }

    @Test
    fun `mixed currency fails closed`() {
        val result =
            MonthlyFlowCalculator.calculate(
                currency = eur,
                month = september,
                activity = listOf(entry("usd", Direction.INCOME, 100, currency = CurrencyCode.of("USD"))),
            )

        assertEquals(MonthlyFlowResult.Unsafe(eur, setOf(UnsafeReason.CURRENCY_MISMATCH)), result)
    }

    @Test
    fun `overflow fails closed`() {
        val result =
            MonthlyFlowCalculator.calculate(
                currency = eur,
                month = september,
                activity =
                    listOf(
                        entry("first", Direction.INCOME, Long.MAX_VALUE),
                        entry("second", Direction.INCOME, 1),
                    ),
            )

        assertEquals(MonthlyFlowResult.Unsafe(eur, setOf(UnsafeReason.ARITHMETIC_OVERFLOW)), result)
    }

    private fun entry(
        id: String,
        direction: Direction,
        amount: Long,
        month: BudgetMonth = september,
        currency: CurrencyCode = eur,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = direction,
            amount = Money(amount, currency),
            state = ActivityState.PLANNED,
            budgetMonth = month,
        )
}
