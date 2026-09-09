package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ExpenseBreakdownCalculatorTest {
    private val eur = CurrencyCode.of("EUR")
    private val september = BudgetMonth(YearMonth.of(2026, 9))
    private val housing = PredefinedCategory.HOUSING.id
    private val groceries = PredefinedCategory.GROCERIES.id
    private val knownCategories = PredefinedCategory.entries.map(PredefinedCategory::id).toSet()

    @Test
    fun `expenses are grouped once by category for the selected month`() {
        val activities =
            listOf(
                activity("rent", 50_000, Direction.EXPENSE, housing),
                activity("repair", 10_000, Direction.EXPENSE, housing),
                activity("food", 25_000, Direction.EXPENSE, groceries),
                activity("other", 5_000, Direction.EXPENSE),
                activity("salary", 200_000, Direction.INCOME, PredefinedCategory.SALARY.id),
                activity(
                    id = "october",
                    minorUnits = 99_000,
                    direction = Direction.EXPENSE,
                    categoryId = housing,
                    month = BudgetMonth(YearMonth.of(2026, 10)),
                ),
            )
        val result =
            ExpenseBreakdownCalculator.calculate(
                currency = eur,
                month = september,
                activity = activities,
                knownCategoryIds = knownCategories,
            )

        assertEquals(
            ExpenseBreakdownResult.Summary(
                total = Money(90_000, eur),
                categories =
                    listOf(
                        ExpenseCategoryTotal(housing, Money(60_000, eur)),
                        ExpenseCategoryTotal(groceries, Money(25_000, eur)),
                        ExpenseCategoryTotal(null, Money(5_000, eur)),
                    ),
            ),
            result,
        )
        assertEquals(
            (MonthlyFlowCalculator.calculate(eur, september, activities) as MonthlyFlowResult.Summary).expenses,
            (result as ExpenseBreakdownResult.Summary).total,
        )
    }

    @Test
    fun `zero expenses produce an empty exact summary`() {
        val result =
            ExpenseBreakdownCalculator.calculate(
                currency = eur,
                month = september,
                activity = listOf(activity("zero", 0, Direction.EXPENSE, housing)),
                knownCategoryIds = knownCategories,
            )

        assertEquals(
            ExpenseBreakdownResult.Summary(Money.zero(eur), emptyList()),
            result,
        )
    }

    @Test
    fun `unknown category assignment fails closed`() {
        val result =
            ExpenseBreakdownCalculator.calculate(
                currency = eur,
                month = september,
                activity = listOf(activity("unknown", 10_000, Direction.EXPENSE, CategoryId("custom_missing"))),
                knownCategoryIds = knownCategories,
            )

        assertEquals(
            ExpenseBreakdownResult.Unsafe(eur, setOf(UnsafeReason.UNKNOWN_CATEGORY_ASSIGNMENT)),
            result,
        )
    }

    @Test
    fun `duplicate identities and mixed currency fail closed`() {
        val recurringSource = ActivitySource.Recurring(RecurringItemId("rent"), "2026-09-01")
        val first = activity("duplicate", 10_000, Direction.EXPENSE, housing, source = recurringSource)
        val second =
            activity(
                id = "duplicate",
                minorUnits = 20_000,
                direction = Direction.EXPENSE,
                categoryId = housing,
                currency = CurrencyCode.of("USD"),
                source = recurringSource,
            )

        val result =
            ExpenseBreakdownCalculator.calculate(
                currency = eur,
                month = september,
                activity = listOf(first, second),
                knownCategoryIds = knownCategories,
            )

        assertEquals(
            ExpenseBreakdownResult.Unsafe(
                eur,
                setOf(
                    UnsafeReason.DUPLICATE_ACTIVITY_ID,
                    UnsafeReason.DUPLICATE_RECURRING_OCCURRENCE,
                    UnsafeReason.CURRENCY_MISMATCH,
                ),
            ),
            result,
        )
    }

    @Test
    fun `overflow fails closed instead of producing a partial total`() {
        val result =
            ExpenseBreakdownCalculator.calculate(
                currency = eur,
                month = september,
                activity =
                    listOf(
                        activity("first", Long.MAX_VALUE, Direction.EXPENSE, housing),
                        activity("second", 1, Direction.EXPENSE, groceries),
                    ),
                knownCategoryIds = knownCategories,
            )

        assertEquals(
            ExpenseBreakdownResult.Unsafe(eur, setOf(UnsafeReason.ARITHMETIC_OVERFLOW)),
            result,
        )
    }

    private fun activity(
        id: String,
        minorUnits: Long,
        direction: Direction,
        categoryId: CategoryId? = null,
        month: BudgetMonth = september,
        currency: CurrencyCode = eur,
        source: ActivitySource = ActivitySource.OneOff,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = direction,
            amount = Money(minorUnits, currency),
            state = ActivityState.PLANNED,
            budgetMonth = month,
            source = source,
            categoryId = categoryId,
        )
}
