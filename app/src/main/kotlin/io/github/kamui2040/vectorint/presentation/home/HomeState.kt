package io.github.kamui2040.vectorint.presentation.home

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.AvailableFundsCalculator
import io.github.kamui2040.vectorint.core.AvailableFundsResult
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.MonthlyFlowCalculator
import io.github.kamui2040.vectorint.core.MonthlyFlowResult
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringOccurrenceGenerator
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.RecurringOccurrenceUpdater
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal sealed interface HomeUiState {
    data class Loading(
        val monthLabel: String,
    ) : HomeUiState

    data class NeedsCurrentFunds(
        val monthLabel: String,
    ) : HomeUiState

    data class Ready(
        val monthLabel: String,
        val availableNow: String,
        val currentFunds: String,
        val reservedExpenses: String,
        val expectedIncome: ExpectedIncomeUi,
    ) : HomeUiState

    data class MonthOverview(
        val monthLabel: String,
        val relation: HomeMonthRelation,
        val income: String,
        val expenses: String,
        val net: String,
        val netIsNegative: Boolean,
    ) : HomeUiState

    data class Unsafe(
        val monthLabel: String,
        val reasons: Set<UnsafeReason>,
    ) : HomeUiState

    data class LoadFailed(
        val monthLabel: String,
    ) : HomeUiState
}

internal enum class HomeMonthRelation {
    PAST,
    FUTURE,
}

internal sealed interface ExpectedIncomeUi {
    data object Excluded : ExpectedIncomeUi

    data class Included(
        val amount: String,
    ) : ExpectedIncomeUi
}

internal interface HomeValueFormatter {
    fun formatMoney(money: Money): String

    fun formatMonth(month: BudgetMonth): String
}

internal class RegionalHomeValueFormatter(
    private val regionalFormatter: RegionalFormatter = RegionalFormatter(),
) : HomeValueFormatter {
    override fun formatMoney(money: Money): String = regionalFormatter.formatMoney(money)

    override fun formatMonth(month: BudgetMonth): String = regionalFormatter.formatMonth(month)
}

internal class HomeStateLoader(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    private val formatter: HomeValueFormatter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val occurrenceUpdater: RecurringOccurrenceUpdater = RecurringOccurrenceUpdater { _, _ -> emptyList() },
) {
    fun currentMonth(): BudgetMonth = BudgetMonth(YearMonth.now(clock))

    fun formatMonth(month: BudgetMonth): String = formatter.formatMonth(month)

    suspend fun load(month: BudgetMonth = currentMonth()): HomeUiState =
        try {
            loadOrThrow(month)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            HomeUiState.LoadFailed(formatter.formatMonth(month))
        }

    private suspend fun loadOrThrow(month: BudgetMonth): HomeUiState {
        val monthLabel = formatter.formatMonth(month)
        val isCurrentMonth = month == currentMonth()
        val settings = if (isCurrentMonth) settingsRepository.settings.first() else null
        val initialSnapshot =
            budgetRepository.loadBudgetSnapshot()
                ?: return HomeUiState.NeedsCurrentFunds(monthLabel)
        if (!isCurrentMonth) {
            return loadMonthOverview(
                month = month,
                monthLabel = monthLabel,
                storedActivity = initialSnapshot.activities,
                currentFunds = initialSnapshot.currentFunds.amount,
            )
        }
        val recurringOccurrences = occurrenceUpdater.refresh(month, initialSnapshot.currentFunds.amount.currency)
        val snapshot =
            initialSnapshot.copy(
                activities =
                    (recurringOccurrences + initialSnapshot.activities)
                        .distinctBy { it.id },
            )
        val currentSettings = checkNotNull(settings)

        return when (
            val result =
                AvailableFundsCalculator.calculate(
                    currentFunds = snapshot.currentFunds,
                    month = month,
                    activity = snapshot.activities,
                    policy = currentSettings.calculationPolicy,
                )
        ) {
            is AvailableFundsResult.Available ->
                HomeUiState.Ready(
                    monthLabel = monthLabel,
                    availableNow = formatter.formatMoney(result.availableNow),
                    currentFunds = formatter.formatMoney(result.confirmedFunds),
                    reservedExpenses = formatter.formatMoney(result.reservedExpenses),
                    expectedIncome =
                        if (currentSettings.includeExpectedIncome) {
                            ExpectedIncomeUi.Included(formatter.formatMoney(result.plannedIncome))
                        } else {
                            ExpectedIncomeUi.Excluded
                        },
                )

            is AvailableFundsResult.Unsafe ->
                HomeUiState.Unsafe(
                    monthLabel = monthLabel,
                    reasons = result.reasons,
                )
        }
    }

    private suspend fun loadMonthOverview(
        month: BudgetMonth,
        monthLabel: String,
        storedActivity: List<ActivityEntry>,
        currentFunds: Money,
    ): HomeUiState {
        val storedRecurringKeys = storedActivity.mapNotNull(ActivityEntry::recurringKey).toSet()
        val previewOccurrences =
            budgetRepository
                .loadRecurringItems()
                .flatMap { item ->
                    RecurringOccurrenceGenerator.generate(
                        item = item,
                        month = month,
                        today = LocalDate.MIN,
                        autoBookedAt = { error("Month previews must remain planned") },
                        activityId = { ActivityId("month-preview-${UUID.randomUUID()}") },
                    )
                }.filter { it.recurringKey() !in storedRecurringKeys }
        return when (
            val result =
                MonthlyFlowCalculator.calculate(
                    currency = currentFunds.currency,
                    month = month,
                    activity = storedActivity + previewOccurrences,
                )
        ) {
            is MonthlyFlowResult.Summary ->
                HomeUiState.MonthOverview(
                    monthLabel = monthLabel,
                    relation =
                        if (month.value.isBefore(currentMonth().value)) {
                            HomeMonthRelation.PAST
                        } else {
                            HomeMonthRelation.FUTURE
                        },
                    income = formatter.formatMoney(result.income),
                    expenses = formatter.formatMoney(result.expenses),
                    net = formatter.formatMoney(result.net),
                    netIsNegative = result.net.minorUnits < 0,
                )

            is MonthlyFlowResult.Unsafe -> HomeUiState.Unsafe(monthLabel, result.reasons)
        }
    }
}

private fun ActivityEntry.recurringKey(): Pair<RecurringItemId, String>? =
    (source as? ActivitySource.Recurring)?.let { it.itemId to it.occurrenceKey }
