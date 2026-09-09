package io.github.kamui2040.vectorint.core

data class ExpenseCategoryTotal(
    val categoryId: CategoryId?,
    val amount: Money,
)

sealed interface ExpenseBreakdownResult {
    data class Summary(
        val total: Money,
        val categories: List<ExpenseCategoryTotal>,
    ) : ExpenseBreakdownResult

    data class Unsafe(
        val currency: CurrencyCode,
        val reasons: Set<UnsafeReason>,
    ) : ExpenseBreakdownResult
}

object ExpenseBreakdownCalculator {
    fun calculate(
        currency: CurrencyCode,
        month: BudgetMonth,
        activity: List<ActivityEntry>,
        knownCategoryIds: Set<CategoryId>,
    ): ExpenseBreakdownResult {
        val reasons =
            buildSet {
                if (activity.map(ActivityEntry::id).distinct().size != activity.size) {
                    add(UnsafeReason.DUPLICATE_ACTIVITY_ID)
                }
                val recurringKeys =
                    activity.mapNotNull { entry ->
                        (entry.source as? ActivitySource.Recurring)?.let { source ->
                            source.itemId to source.occurrenceKey
                        }
                    }
                if (recurringKeys.distinct().size != recurringKeys.size) {
                    add(UnsafeReason.DUPLICATE_RECURRING_OCCURRENCE)
                }
                if (activity.any { it.amount.currency != currency }) {
                    add(UnsafeReason.CURRENCY_MISMATCH)
                }
                if (activity.any { it.categoryId != null && it.categoryId !in knownCategoryIds }) {
                    add(UnsafeReason.UNKNOWN_CATEGORY_ASSIGNMENT)
                }
            }
        if (reasons.isNotEmpty()) return ExpenseBreakdownResult.Unsafe(currency, reasons)

        return try {
            var totalMinorUnits = 0L
            val totalsByCategory = linkedMapOf<CategoryId?, Long>()
            activity
                .asSequence()
                .filter { it.budgetMonth == month && it.direction == Direction.EXPENSE }
                .filter { it.amount.minorUnits > 0L }
                .forEach { entry ->
                    totalMinorUnits = Math.addExact(totalMinorUnits, entry.amount.minorUnits)
                    totalsByCategory[entry.categoryId] =
                        Math.addExact(
                            totalsByCategory[entry.categoryId] ?: 0L,
                            entry.amount.minorUnits,
                        )
                }
            ExpenseBreakdownResult.Summary(
                total = Money(totalMinorUnits, currency),
                categories =
                    totalsByCategory
                        .map { (categoryId, minorUnits) ->
                            ExpenseCategoryTotal(
                                categoryId = categoryId,
                                amount = Money(minorUnits, currency),
                            )
                        }.sortedWith(
                            compareByDescending<ExpenseCategoryTotal> { it.amount.minorUnits }
                                .thenBy { it.categoryId?.value.orEmpty() },
                        ),
            )
        } catch (_: ArithmeticException) {
            ExpenseBreakdownResult.Unsafe(currency, setOf(UnsafeReason.ARITHMETIC_OVERFLOW))
        }
    }
}
