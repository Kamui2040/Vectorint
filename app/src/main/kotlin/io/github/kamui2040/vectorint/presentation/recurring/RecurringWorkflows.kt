package io.github.kamui2040.vectorint.presentation.recurring

import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.OccurrenceTiming
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderSettings
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.presentation.entry.EntryMoneyAdapter
import io.github.kamui2040.vectorint.presentation.format.AmountSignPolicy
import io.github.kamui2040.vectorint.presentation.format.MoneyInputResult
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import io.github.kamui2040.vectorint.presentation.format.RegionalWholeNumberInputParser
import io.github.kamui2040.vectorint.presentation.format.WholeNumberInputResult
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

internal sealed interface RecurringListUiState {
    data object Loading : RecurringListUiState

    data object Empty : RecurringListUiState

    data class Ready(
        val items: List<RecurringListItemUi>,
        val customCategories: List<CustomCategory> = emptyList(),
    ) : RecurringListUiState

    data object LoadFailed : RecurringListUiState
}

internal data class RecurringListItemUi(
    val id: RecurringItemId,
    val name: String,
    val direction: Direction,
    val amount: String,
    val schedule: RecurringSchedule,
    val firstOccurrenceLabel: String,
    val accountName: String = "Main",
    val categoryId: CategoryId? = null,
    val tags: List<String> = emptyList(),
)

internal fun interface RecurringMoneyFormatter {
    fun formatMoney(money: Money): String
}

internal class RegionalRecurringMoneyFormatter(
    private val formatter: RegionalFormatter = RegionalFormatter(),
) : RecurringMoneyFormatter {
    override fun formatMoney(money: Money): String = formatter.formatMoney(money)
}

internal interface RecurringScheduleInputAdapter {
    fun formatDate(date: LocalDate): String

    fun formatMonth(month: YearMonth): String

    fun formatWholeNumber(value: Int): String

    fun parseRepeatEvery(input: String): Int?

    fun parseReminderDays(input: String): Int?
}

internal class RegionalRecurringScheduleInputAdapter(
    private val formatter: RegionalFormatter = RegionalFormatter(),
    private val parser: RegionalWholeNumberInputParser = RegionalWholeNumberInputParser(),
) : RecurringScheduleInputAdapter {
    override fun formatDate(date: LocalDate): String = formatter.formatDate(date)

    override fun formatMonth(month: YearMonth): String = formatter.formatMonth(BudgetMonth(month))

    override fun formatWholeNumber(value: Int): String = formatter.formatWholeNumber(value)

    override fun parseRepeatEvery(input: String): Int? =
        when (val parsed = parser.parse(input, RecurrenceInterval.MAX_EVERY)) {
            is WholeNumberInputResult.Accepted -> parsed.value
            WholeNumberInputResult.Rejected -> null
        }

    override fun parseReminderDays(input: String): Int? =
        when (val parsed = parser.parse(input, ReminderLead.MAX_DAYS_BEFORE)) {
            is WholeNumberInputResult.Accepted -> parsed.value
            WholeNumberInputResult.Rejected -> null
        }
}

internal class RecurringListLoader(
    private val budgetRepository: BudgetRepository,
    private val formatter: RecurringMoneyFormatter,
    private val scheduleAdapter: RecurringScheduleInputAdapter = RegionalRecurringScheduleInputAdapter(),
) {
    suspend fun load(): RecurringListUiState =
        try {
            val items = budgetRepository.loadRecurringItems()
            val accounts = budgetRepository.loadAccounts()
            val accountNames = accounts.associate { it.id to it.name }
            require(items.all { it.accountId in accountNames }) { "Recurring account assignment is unknown" }
            if (items.isEmpty()) {
                RecurringListUiState.Empty
            } else {
                RecurringListUiState.Ready(
                    items =
                        items
                            .sortedWith(
                                compareBy<RecurringItem>(
                                    { it.name.lowercase(Locale.ROOT) },
                                    { it.name },
                                    { it.id.value },
                                ),
                            ).map { item ->
                                RecurringListItemUi(
                                    id = item.id,
                                    name = item.name,
                                    direction = item.direction,
                                    amount = formatter.formatMoney(item.amount),
                                    schedule = item.schedule,
                                    firstOccurrenceLabel = item.schedule.firstOccurrenceLabel(scheduleAdapter),
                                    accountName = checkNotNull(accountNames[item.accountId]),
                                    categoryId = item.categoryId,
                                    tags = item.tags.toSortedLabels(),
                                )
                            },
                    customCategories = budgetRepository.loadCustomCategories(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            RecurringListUiState.LoadFailed
        }
}

private fun RecurringSchedule.firstOccurrenceLabel(adapter: RecurringScheduleInputAdapter): String =
    when (timing) {
        OccurrenceTiming.SpecificDate -> adapter.formatDate(firstOccurrence)
        is OccurrenceTiming.DateRange ->
            "${adapter.formatDate(firstOccurrence)} – ${adapter.formatDate(bookingDateFor(firstOccurrence))}"

        OccurrenceTiming.AnyTimeInMonth -> adapter.formatMonth(YearMonth.from(firstOccurrence))
    }

internal data class RecurringReminderInput(
    val enabled: Boolean,
    val daysBeforeInput: String,
)

internal enum class RecurringTimingChoice {
    SPECIFIC_DATE,
    DATE_RANGE,
    ANY_TIME_IN_MONTH,
}

internal data class RecurringItemFormSeed(
    val item: RecurringItem?,
    val nameInput: String,
    val amountInput: String,
    val currencyCode: CurrencyCode,
    val direction: Direction,
    val firstOccurrence: LocalDate,
    val timingChoice: RecurringTimingChoice = RecurringTimingChoice.SPECIFIC_DATE,
    val firstPeriodEndsOn: LocalDate? = null,
    val repeatEveryInput: String,
    val repeatUnit: RecurrenceUnit,
    val countsToward: BudgetMonthAssignment,
    val requireManualConfirmation: Boolean = false,
    val endsOn: LocalDate?,
    val remindOn: LocalDate?,
    val occurrenceReminder: RecurringReminderInput,
    val remindReminder: RecurringReminderInput,
    val endReminder: RecurringReminderInput,
    val categoryId: CategoryId? = null,
    val tags: Set<Tag> = emptySet(),
    val accounts: List<Account> = emptyList(),
    val accountId: AccountId = io.github.kamui2040.vectorint.core.LEGACY_DEFAULT_ACCOUNT_ID,
) {
    val isEditing: Boolean = item != null
}

internal sealed interface RecurringItemLoadResult {
    data class Ready(
        val seed: RecurringItemFormSeed,
    ) : RecurringItemLoadResult

    data object NeedsCurrentFunds : RecurringItemLoadResult

    data object Missing : RecurringItemLoadResult

    data object Failed : RecurringItemLoadResult
}

internal sealed interface RecurringItemMutationResult {
    data object Saved : RecurringItemMutationResult

    data object Deleted : RecurringItemMutationResult

    data object InvalidName : RecurringItemMutationResult

    data object InvalidAmount : RecurringItemMutationResult

    data object AmountMustBePositive : RecurringItemMutationResult

    data object InvalidSchedule : RecurringItemMutationResult

    data object InvalidReminder : RecurringItemMutationResult

    data object Missing : RecurringItemMutationResult

    data object StorageFailed : RecurringItemMutationResult
}

internal fun interface RecurringItemIdFactory {
    fun create(): RecurringItemId
}

internal class RecurringItemEditor(
    private val budgetRepository: BudgetRepository,
    private val moneyAdapter: EntryMoneyAdapter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idFactory: RecurringItemIdFactory =
        RecurringItemIdFactory { RecurringItemId(UUID.randomUUID().toString()) },
    private val scheduleAdapter: RecurringScheduleInputAdapter = RegionalRecurringScheduleInputAdapter(),
) {
    suspend fun load(recurringItemId: RecurringItemId?): RecurringItemLoadResult =
        try {
            val accounts = budgetRepository.loadAccounts()
            if (accounts.isEmpty()) return RecurringItemLoadResult.NeedsCurrentFunds
            val currencies = accounts.map { it.currentFunds.amount.currency }.distinct()
            require(currencies.size == 1) { "Account currencies must match" }
            val currency = currencies.single()
            val item =
                recurringItemId?.let { id ->
                    budgetRepository.loadRecurringItems().singleOrNull { it.id == id }
                        ?: return RecurringItemLoadResult.Missing
                }
            if (item != null) {
                require(item.accountId in accounts.map(Account::id)) { "Recurring account assignment is unknown" }
                require(item.amount.currency == currency) {
                    "Recurring item currency must match its account"
                }
            }
            RecurringItemLoadResult.Ready(
                item.toSeed(
                    accounts = accounts,
                    currencyCode = currency,
                    moneyAdapter = moneyAdapter,
                    scheduleAdapter = scheduleAdapter,
                    defaultFirstOccurrence = LocalDate.now(clock),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            RecurringItemLoadResult.Failed
        }

    @Suppress("LongParameterList")
    suspend fun save(
        seed: RecurringItemFormSeed,
        nameInput: String,
        amountInput: String,
        direction: Direction,
        firstOccurrence: LocalDate = seed.firstOccurrence,
        timingChoice: RecurringTimingChoice = seed.timingChoice,
        firstPeriodEndsOn: LocalDate? = seed.firstPeriodEndsOn,
        repeatEveryInput: String = seed.repeatEveryInput,
        repeatUnit: RecurrenceUnit = seed.repeatUnit,
        countsToward: BudgetMonthAssignment = seed.countsToward,
        requireManualConfirmation: Boolean = seed.requireManualConfirmation,
        endsOn: LocalDate? = seed.endsOn,
        remindOn: LocalDate? = seed.remindOn,
        occurrenceReminder: RecurringReminderInput = seed.occurrenceReminder,
        remindReminder: RecurringReminderInput = seed.remindReminder,
        endReminder: RecurringReminderInput = seed.endReminder,
        categoryId: CategoryId? = seed.categoryId,
        tags: Set<Tag> = seed.tags,
        accountId: AccountId = seed.accountId,
    ): RecurringItemMutationResult {
        val name = nameInput.trim()
        if (name.isEmpty()) return RecurringItemMutationResult.InvalidName
        val amount =
            when (
                val parsed =
                    moneyAdapter.parse(
                        input = amountInput,
                        currencyCode = seed.currencyCode,
                        signPolicy = AmountSignPolicy.NON_NEGATIVE,
                    )
            ) {
                is MoneyInputResult.Accepted -> parsed.money
                is MoneyInputResult.Rejected -> return RecurringItemMutationResult.InvalidAmount
            }
        if (amount.minorUnits == 0L) return RecurringItemMutationResult.AmountMustBePositive

        val repeatEvery =
            scheduleAdapter.parseRepeatEvery(repeatEveryInput)
                ?: return RecurringItemMutationResult.InvalidSchedule
        val timing =
            timingOrNull(timingChoice, firstOccurrence, firstPeriodEndsOn)
                ?: return RecurringItemMutationResult.InvalidSchedule
        val schedule =
            try {
                RecurringSchedule(
                    firstOccurrence = firstOccurrence,
                    timing = timing,
                    interval = RecurrenceInterval(repeatEvery, repeatUnit),
                    countsToward = countsToward,
                    requireManualConfirmation = requireManualConfirmation,
                    endsOn = endsOn,
                    remindOn = remindOn,
                )
            } catch (_: IllegalArgumentException) {
                return RecurringItemMutationResult.InvalidSchedule
            }
        if (remindReminder.enabled && remindOn == null) return RecurringItemMutationResult.InvalidReminder
        if (endReminder.enabled && endsOn == null) return RecurringItemMutationResult.InvalidReminder
        val reminders =
            when (
                val parsed =
                    remindersOrNull(
                        occurrence = occurrenceReminder,
                        remind = remindReminder,
                        end = endReminder,
                        adapter = scheduleAdapter,
                    )
            ) {
                ReminderInputResult.Invalid -> return RecurringItemMutationResult.InvalidReminder
                is ReminderInputResult.Valid -> parsed.reminders
            }

        val item =
            seed.item?.copy(
                name = name,
                accountId = accountId,
                direction = direction,
                amount = amount,
                schedule = schedule,
                reminders = reminders,
                categoryId = categoryId,
                tags = tags,
            ) ?: RecurringItem(
                id = idFactory.create(),
                name = name,
                accountId = accountId,
                direction = direction,
                amount = amount,
                schedule = schedule,
                reminders = reminders,
                categoryId = categoryId,
                tags = tags,
            )
        return mutate {
            if (seed.item == null) {
                budgetRepository.createRecurringItem(item)
                RecurringItemMutationResult.Saved
            } else if (budgetRepository.updateRecurringItem(item)) {
                RecurringItemMutationResult.Saved
            } else {
                RecurringItemMutationResult.Missing
            }
        }
    }

    suspend fun delete(recurringItemId: RecurringItemId): RecurringItemMutationResult =
        mutate {
            budgetRepository.deleteRecurringItem(recurringItemId)
            RecurringItemMutationResult.Deleted
        }

    private suspend fun mutate(block: suspend () -> RecurringItemMutationResult): RecurringItemMutationResult =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            RecurringItemMutationResult.StorageFailed
        }
}

private fun RecurringItem?.toSeed(
    accounts: List<Account>,
    currencyCode: CurrencyCode,
    moneyAdapter: EntryMoneyAdapter,
    scheduleAdapter: RecurringScheduleInputAdapter,
    defaultFirstOccurrence: LocalDate,
): RecurringItemFormSeed {
    val item = this
    val schedule = item?.schedule ?: RecurringSchedule(firstOccurrence = defaultFirstOccurrence)
    return RecurringItemFormSeed(
        item = item,
        accounts = accounts,
        accountId = item?.accountId ?: accounts.firstOrNull(Account::includeInAvailableNow)?.id ?: accounts.first().id,
        nameInput = item?.name.orEmpty(),
        amountInput = item?.amount?.let(moneyAdapter::formatInput).orEmpty(),
        currencyCode = currencyCode,
        direction = item?.direction ?: Direction.EXPENSE,
        firstOccurrence = schedule.firstOccurrence,
        timingChoice = schedule.timing.toChoice(),
        firstPeriodEndsOn =
            (schedule.timing as? OccurrenceTiming.DateRange)?.let { timing ->
                schedule.firstOccurrence.plusDays(timing.endOffsetDays.toLong())
            },
        repeatEveryInput = scheduleAdapter.formatWholeNumber(schedule.interval.every),
        repeatUnit = schedule.interval.unit,
        countsToward = schedule.countsToward,
        requireManualConfirmation = schedule.requireManualConfirmation,
        endsOn = schedule.endsOn,
        remindOn = schedule.remindOn,
        occurrenceReminder =
            item?.reminders?.occurrence.toInput(
                defaultDays = 1,
                adapter = scheduleAdapter,
            ),
        remindReminder =
            item?.reminders?.remind.toInput(
                defaultDays = 7,
                adapter = scheduleAdapter,
            ),
        endReminder =
            item?.reminders?.end.toInput(
                defaultDays = 7,
                adapter = scheduleAdapter,
            ),
        categoryId = item?.categoryId,
        tags = item?.tags.orEmpty(),
    )
}

private fun OccurrenceTiming.toChoice(): RecurringTimingChoice =
    when (this) {
        OccurrenceTiming.SpecificDate -> RecurringTimingChoice.SPECIFIC_DATE
        is OccurrenceTiming.DateRange -> RecurringTimingChoice.DATE_RANGE
        OccurrenceTiming.AnyTimeInMonth -> RecurringTimingChoice.ANY_TIME_IN_MONTH
    }

private fun timingOrNull(
    choice: RecurringTimingChoice,
    firstOccurrence: LocalDate,
    firstPeriodEndsOn: LocalDate?,
): OccurrenceTiming? =
    try {
        when (choice) {
            RecurringTimingChoice.SPECIFIC_DATE -> OccurrenceTiming.SpecificDate
            RecurringTimingChoice.DATE_RANGE -> {
                val periodEnd = firstPeriodEndsOn ?: return null
                val offset = ChronoUnit.DAYS.between(firstOccurrence, periodEnd)
                if (offset < 0L || offset > Int.MAX_VALUE) return null
                OccurrenceTiming.DateRange(offset.toInt())
            }

            RecurringTimingChoice.ANY_TIME_IN_MONTH -> OccurrenceTiming.AnyTimeInMonth
        }
    } catch (_: IllegalArgumentException) {
        null
    }

private fun Set<Tag>.toSortedLabels(): List<String> = map(Tag::value).sortedWith(compareBy({ it.lowercase(Locale.ROOT) }, { it }))

private fun ReminderLead?.toInput(
    defaultDays: Int,
    adapter: RecurringScheduleInputAdapter,
): RecurringReminderInput =
    RecurringReminderInput(
        enabled = this != null,
        daysBeforeInput = adapter.formatWholeNumber(this?.daysBefore ?: defaultDays),
    )

private sealed interface ReminderInputResult {
    data class Valid(
        val reminders: ReminderSettings,
    ) : ReminderInputResult

    data object Invalid : ReminderInputResult
}

private fun remindersOrNull(
    occurrence: RecurringReminderInput,
    remind: RecurringReminderInput,
    end: RecurringReminderInput,
    adapter: RecurringScheduleInputAdapter,
): ReminderInputResult {
    val occurrenceLead = occurrence.toReminderLead(adapter) ?: if (occurrence.enabled) return ReminderInputResult.Invalid else null
    val remindLead = remind.toReminderLead(adapter) ?: if (remind.enabled) return ReminderInputResult.Invalid else null
    val endLead = end.toReminderLead(adapter) ?: if (end.enabled) return ReminderInputResult.Invalid else null
    return ReminderInputResult.Valid(
        ReminderSettings(
            occurrence = occurrenceLead,
            remind = remindLead,
            end = endLead,
        ),
    )
}

private fun RecurringReminderInput.toReminderLead(adapter: RecurringScheduleInputAdapter): ReminderLead? {
    if (!enabled) return null
    val days = adapter.parseReminderDays(daysBeforeInput) ?: return null
    return try {
        ReminderLead(days)
    } catch (_: IllegalArgumentException) {
        null
    }
}
