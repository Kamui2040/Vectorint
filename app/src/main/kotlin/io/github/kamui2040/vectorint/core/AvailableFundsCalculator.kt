package io.github.kamui2040.vectorint.core

import java.time.Instant

data class CurrentFunds(
    val amount: Money,
    val capturedAt: Instant,
)

data class CalculationPolicy(
    val includeExpectedIncome: Boolean = false,
)

enum class UnsafeReason {
    DUPLICATE_ACTIVITY_ID,
    DUPLICATE_RECURRING_OCCURRENCE,
    CURRENCY_MISMATCH,
    UNKNOWN_CATEGORY_ASSIGNMENT,
    ARITHMETIC_OVERFLOW,
}

sealed interface AvailableFundsResult {
    data class Available(
        val confirmedFunds: Money,
        val plannedIncome: Money,
        val reservedExpenses: Money,
        val availableNow: Money,
    ) : AvailableFundsResult

    data class Unsafe(
        val currency: CurrencyCode,
        val reasons: Set<UnsafeReason>,
    ) : AvailableFundsResult
}

object AvailableFundsCalculator {
    fun calculate(
        currentFunds: CurrentFunds,
        month: BudgetMonth,
        activity: List<ActivityEntry>,
        policy: CalculationPolicy = CalculationPolicy(),
    ): AvailableFundsResult {
        val currency = currentFunds.amount.currency
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

        if (reasons.isNotEmpty()) {
            return AvailableFundsResult.Unsafe(currency, reasons)
        }

        return try {
            var confirmedMinorUnits = currentFunds.amount.minorUnits
            var plannedIncomeMinorUnits = 0L
            var reservedExpenseMinorUnits = 0L

            activity.forEach { entry ->
                when (entry.state) {
                    ActivityState.CONFIRMED -> {
                        if (entry.bookedAt!!.isAfter(currentFunds.capturedAt)) {
                            confirmedMinorUnits =
                                Math.addExact(
                                    confirmedMinorUnits,
                                    entry.direction.signedMinorUnits(entry.amount.minorUnits),
                                )
                        }
                    }

                    ActivityState.PLANNED -> {
                        if (entry.budgetMonth == month) {
                            when (entry.direction) {
                                Direction.INCOME -> {
                                    if (policy.includeExpectedIncome) {
                                        plannedIncomeMinorUnits =
                                            Math.addExact(plannedIncomeMinorUnits, entry.amount.minorUnits)
                                    }
                                }

                                Direction.EXPENSE ->
                                    reservedExpenseMinorUnits =
                                        Math.addExact(reservedExpenseMinorUnits, entry.amount.minorUnits)
                            }
                        }
                    }
                }
            }

            val availableMinorUnits =
                Math.subtractExact(
                    Math.addExact(confirmedMinorUnits, plannedIncomeMinorUnits),
                    reservedExpenseMinorUnits,
                )

            AvailableFundsResult.Available(
                confirmedFunds = Money(confirmedMinorUnits, currency),
                plannedIncome = Money(plannedIncomeMinorUnits, currency),
                reservedExpenses = Money(reservedExpenseMinorUnits, currency),
                availableNow = Money(availableMinorUnits, currency),
            )
        } catch (_: ArithmeticException) {
            AvailableFundsResult.Unsafe(currency, setOf(UnsafeReason.ARITHMETIC_OVERFLOW))
        }
    }
}
