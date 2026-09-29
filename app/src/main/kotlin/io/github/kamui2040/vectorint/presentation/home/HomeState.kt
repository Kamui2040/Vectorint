package io.github.kamui2040.vectorint.presentation.home

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.AvailableFundsCalculator
import io.github.kamui2040.vectorint.core.AvailableFundsResult
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.MonthlyFlowCalculator
import io.github.kamui2040.vectorint.core.MonthlyFlowResult
import io.github.kamui2040.vectorint.core.RecurringItem
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
import java.util.Locale
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
        val showUpcomingEntries: Boolean = false,
        val upcomingEntries: List<UpcomingEntryUi> = emptyList(),
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

internal data class UpcomingEntryUi(
    val name: String,
    val amount: String,
    val date: String,
    val direction: Direction,
)

internal interface HomeValueFormatter {
    fun formatMoney(money: Money): String

    fun formatMonth(month: BudgetMonth): String

    fun formatDate(date: LocalDate): String
}

internal class RegionalHomeValueFormatter(
    private val regionalFormatter: RegionalFormatter = RegionalFormatter(),
) : HomeValueFormatter {
    override fun formatMoney(money: Money): String = regionalFormatter.formatMoney(money)

    override fun formatMonth(month: BudgetMonth): String = regionalFormatter.formatMonth(month)

    override fun formatDate(date: LocalDate): String = regionalFormatter.formatDate(date)
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
        if (initialSnapshot.accounts.isEmpty()) return HomeUiState.LoadFailed(monthLabel)
        val accountCurrencies = initialSnapshot.accounts.map { it.currentFunds.amount.currency }.distinct()
        if (accountCurrencies.size != 1) {
            return HomeUiState.Unsafe(monthLabel, setOf(UnsafeReason.CURRENCY_MISMATCH))
        }
        val currency = accountCurrencies.single()
        if (!isCurrentMonth) {
            return loadMonthOverview(
                month = month,
                monthLabel = monthLabel,
                storedActivity = initialSnapshot.activities,
                currency = currency,
            )
        }
        val recurringOccurrences = occurrenceUpdater.refresh(month, currency)
        val snapshot =
            initialSnapshot.copy(
                activities =
                    (recurringOccurrences + initialSnapshot.activities)
                        .distinctBy { it.id },
            )
        val currentSettings = checkNotNull(settings)
        val upcomingEntries =
            if (currentSettings.showUpcomingEntries) {
                loadUpcomingEntries(
                    activities = snapshot.activities,
                    recurringItems = budgetRepository.loadRecurringItems(),
                    today = LocalDate.now(clock),
                )
            } else {
                emptyList()
            }

        return when (
            val result =
                AvailableFundsCalculator.calculate(
                    accounts = snapshot.accounts,
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
                    showUpcomingEntries = currentSettings.showUpcomingEntries,
                    upcomingEntries = upcomingEntries,
                )

            is AvailableFundsResult.Unsafe ->
                HomeUiState.Unsafe(
                    monthLabel = monthLabel,
                    reasons = result.reasons,
                )
        }
    }

    private fun loadUpcomingEntries(
        activities: List<ActivityEntry>,
        recurringItems: List<RecurringItem>,
        today: LocalDate,
    ): List<UpcomingEntryUi> {
        val recordedRecurringDates =
            activities
                .mapNotNull { entry ->
                    val source = entry.source as? ActivitySource.Recurring ?: return@mapNotNull null
                    source.itemId to entry.upcomingDate()
                }.toSet()
        val storedCandidates =
            activities
                .asSequence()
                .filter { it.state == ActivityState.PLANNED }
                .map { entry -> UpcomingCandidate(entry, entry.upcomingDate(), entry.id.value) }
                .filter { candidate -> !candidate.date.isBefore(today) }
                .toList()
        val previewCandidates =
            recurringItems.flatMap { item ->
                buildList {
                    var searchFrom = today
                    while (size < UPCOMING_ENTRY_LIMIT) {
                        val date = item.schedule.nextOccurrenceOnOrAfter(searchFrom) ?: break
                        if ((item.id to date) !in recordedRecurringDates) {
                            add(
                                UpcomingCandidate(
                                    name = item.name,
                                    amount = item.amount,
                                    direction = item.direction,
                                    date = date,
                                    stableKey = "${item.id.value}:$date",
                                ),
                            )
                        }
                        if (date == LocalDate.MAX) break
                        searchFrom = date.plusDays(1)
                    }
                }
            }
        return (storedCandidates + previewCandidates)
            .sortedWith(compareBy<UpcomingCandidate>({ it.date }, { it.name.lowercase(Locale.ROOT) }, { it.stableKey }))
            .take(UPCOMING_ENTRY_LIMIT)
            .map { candidate ->
                UpcomingEntryUi(
                    name = candidate.name,
                    amount = formatter.formatMoney(candidate.amount),
                    date = formatter.formatDate(candidate.date),
                    direction = candidate.direction,
                )
            }
    }

    private suspend fun loadMonthOverview(
        month: BudgetMonth,
        monthLabel: String,
        storedActivity: List<ActivityEntry>,
        currency: CurrencyCode,
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
                    currency = currency,
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

private const val UPCOMING_ENTRY_LIMIT = 3

private data class UpcomingCandidate(
    val name: String,
    val amount: Money,
    val direction: Direction,
    val date: LocalDate,
    val stableKey: String,
) {
    constructor(entry: ActivityEntry, date: LocalDate, stableKey: String) : this(
        name = entry.name,
        amount = entry.amount,
        direction = entry.direction,
        date = date,
        stableKey = stableKey,
    )
}

private fun ActivityEntry.upcomingDate(): LocalDate = expectedOn ?: budgetMonth.value.atEndOfMonth()

private fun ActivityEntry.recurringKey(): Pair<RecurringItemId, String>? =
    (source as? ActivitySource.Recurring)?.let { it.itemId to it.occurrenceKey }
