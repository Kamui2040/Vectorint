package io.github.kamui2040.vectorint.core

sealed interface MonthlyFlowResult {
    data class Summary(
        val income: Money,
        val expenses: Money,
        val net: Money,
    ) : MonthlyFlowResult

    data class Unsafe(
        val currency: CurrencyCode,
        val reasons: Set<UnsafeReason>,
    ) : MonthlyFlowResult
}

object MonthlyFlowCalculator {
    fun calculate(
        currency: CurrencyCode,
        month: BudgetMonth,
        activity: List<ActivityEntry>,
    ): MonthlyFlowResult {
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
            }
        if (reasons.isNotEmpty()) return MonthlyFlowResult.Unsafe(currency, reasons)

        return try {
            var incomeMinorUnits = 0L
            var expenseMinorUnits = 0L
            activity.filter { it.budgetMonth == month }.forEach { entry ->
                when (entry.direction) {
                    Direction.INCOME -> incomeMinorUnits = Math.addExact(incomeMinorUnits, entry.amount.minorUnits)
                    Direction.EXPENSE -> expenseMinorUnits = Math.addExact(expenseMinorUnits, entry.amount.minorUnits)
                }
            }
            MonthlyFlowResult.Summary(
                income = Money(incomeMinorUnits, currency),
                expenses = Money(expenseMinorUnits, currency),
                net = Money(Math.subtractExact(incomeMinorUnits, expenseMinorUnits), currency),
            )
        } catch (_: ArithmeticException) {
            MonthlyFlowResult.Unsafe(currency, setOf(UnsafeReason.ARITHMETIC_OVERFLOW))
        }
    }
}
