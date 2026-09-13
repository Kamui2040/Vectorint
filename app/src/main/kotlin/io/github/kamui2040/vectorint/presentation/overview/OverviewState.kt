package io.github.kamui2040.vectorint.presentation.overview

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.ExpenseBreakdownCalculator
import io.github.kamui2040.vectorint.core.ExpenseBreakdownResult
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringOccurrenceGenerator
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.RecurringOccurrenceUpdater
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal data class OverviewSliceUi(
    val categoryId: CategoryId?,
    val amount: String,
    val share: String,
    val fraction: Float,
)

internal sealed interface OverviewUiState {
    val monthLabel: String

    data class Loading(
        override val monthLabel: String,
    ) : OverviewUiState

    data class NeedsCurrentFunds(
        override val monthLabel: String,
    ) : OverviewUiState

    data class Empty(
        override val monthLabel: String,
    ) : OverviewUiState

    data class Ready(
        override val monthLabel: String,
        val totalExpenses: String,
        val slices: List<OverviewSliceUi>,
        val customCategories: List<CustomCategory>,
    ) : OverviewUiState

    data class Unsafe(
        override val monthLabel: String,
        val reasons: Set<UnsafeReason>,
    ) : OverviewUiState

    data class LoadFailed(
        override val monthLabel: String,
    ) : OverviewUiState
}

internal interface OverviewValueFormatter {
    fun formatMoney(money: Money): String

    fun formatMonth(month: BudgetMonth): String

    fun formatShare(
        part: Money,
        total: Money,
    ): String
}

internal class RegionalOverviewValueFormatter(
    private val regionalFormatter: RegionalFormatter = RegionalFormatter(),
) : OverviewValueFormatter {
    override fun formatMoney(money: Money): String = regionalFormatter.formatMoney(money)

    override fun formatMonth(month: BudgetMonth): String = regionalFormatter.formatMonth(month)

    override fun formatShare(
        part: Money,
        total: Money,
    ): String {
        require(part.currency == total.currency) { "Currencies must match" }
        return regionalFormatter.formatPercentage(part.minorUnits, total.minorUnits)
    }
}

internal class OverviewStateLoader(
    private val budgetRepository: BudgetRepository,
    private val formatter: OverviewValueFormatter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val occurrenceUpdater: RecurringOccurrenceUpdater = RecurringOccurrenceUpdater { _, _ -> emptyList() },
    private val previewActivityId: () -> ActivityId = { ActivityId("overview-preview-${UUID.randomUUID()}") },
) {
    fun currentMonth(): BudgetMonth = BudgetMonth(YearMonth.now(clock))

    fun formatMonth(month: BudgetMonth): String = formatter.formatMonth(month)

    suspend fun load(month: BudgetMonth = currentMonth()): OverviewUiState =
        try {
            loadOrThrow(month)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            OverviewUiState.LoadFailed(formatter.formatMonth(month))
        }

    private suspend fun loadOrThrow(month: BudgetMonth): OverviewUiState {
        val monthLabel = formatter.formatMonth(month)
        val initialSnapshot =
            budgetRepository.loadBudgetSnapshot()
                ?: return OverviewUiState.NeedsCurrentFunds(monthLabel)
        if (initialSnapshot.accounts.isEmpty()) return OverviewUiState.LoadFailed(monthLabel)
        val accountCurrencies = initialSnapshot.accounts.map { it.currentFunds.amount.currency }.distinct()
        if (accountCurrencies.size != 1) {
            return OverviewUiState.Unsafe(monthLabel, setOf(UnsafeReason.CURRENCY_MISMATCH))
        }
        val currency = accountCurrencies.single()
        val customCategories = budgetRepository.loadCustomCategories()
        val activities =
            if (month == currentMonth()) {
                val refreshed = occurrenceUpdater.refresh(month, currency)
                (refreshed + initialSnapshot.activities).distinctBy(ActivityEntry::id)
            } else {
                initialSnapshot.activities + previewMissingOccurrences(month, initialSnapshot.activities)
            }
        val knownCategoryIds =
            PredefinedCategory.entries.map(PredefinedCategory::id).toSet() +
                customCategories.map(CustomCategory::id)

        return when (
            val result =
                ExpenseBreakdownCalculator.calculate(
                    currency = currency,
                    month = month,
                    activity = activities,
                    knownCategoryIds = knownCategoryIds,
                )
        ) {
            is ExpenseBreakdownResult.Summary -> {
                if (result.total.minorUnits == 0L) {
                    OverviewUiState.Empty(monthLabel)
                } else {
                    OverviewUiState.Ready(
                        monthLabel = monthLabel,
                        totalExpenses = formatter.formatMoney(result.total),
                        slices =
                            result.categories.map { category ->
                                OverviewSliceUi(
                                    categoryId = category.categoryId,
                                    amount = formatter.formatMoney(category.amount),
                                    share = formatter.formatShare(category.amount, result.total),
                                    fraction =
                                        category.amount.minorUnits
                                            .toDouble()
                                            .div(result.total.minorUnits)
                                            .toFloat(),
                                )
                            },
                        customCategories = customCategories,
                    )
                }
            }

            is ExpenseBreakdownResult.Unsafe ->
                OverviewUiState.Unsafe(
                    monthLabel = monthLabel,
                    reasons = result.reasons,
                )
        }
    }

    private suspend fun previewMissingOccurrences(
        month: BudgetMonth,
        storedActivity: List<ActivityEntry>,
    ): List<ActivityEntry> {
        val storedRecurringKeys = storedActivity.mapNotNull(ActivityEntry::recurringKey).toSet()
        return budgetRepository
            .loadRecurringItems()
            .flatMap { item ->
                RecurringOccurrenceGenerator.generate(
                    item = item,
                    month = month,
                    today = LocalDate.MIN,
                    autoBookedAt = { error("Overview previews must remain planned") },
                    activityId = previewActivityId,
                )
            }.filter { it.recurringKey() !in storedRecurringKeys }
    }
}

private fun ActivityEntry.recurringKey(): Pair<RecurringItemId, String>? =
    (source as? ActivitySource.Recurring)?.let { it.itemId to it.occurrenceKey }
