package io.github.kamui2040.vectorint.presentation.activity

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.RecurringOccurrenceUpdater
import io.github.kamui2040.vectorint.presentation.entry.EntryMoneyAdapter
import io.github.kamui2040.vectorint.presentation.format.AmountSignPolicy
import io.github.kamui2040.vectorint.presentation.format.MoneyInputResult
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal sealed interface ActivityHistoryUiState {
    data object Loading : ActivityHistoryUiState

    data object Empty : ActivityHistoryUiState

    data class Ready(
        val items: List<ActivityHistoryItemUi>,
        val customCategories: List<CustomCategory> = emptyList(),
    ) : ActivityHistoryUiState

    data object LoadFailed : ActivityHistoryUiState
}

internal data class ActivityHistoryItemUi(
    val id: ActivityId,
    val name: String,
    val direction: Direction,
    val amount: String,
    val timing: ActivityTimingUi,
    val categoryId: CategoryId? = null,
    val tags: List<String> = emptyList(),
)

internal sealed interface ActivityTimingUi {
    data class PlannedDate(
        val date: String,
    ) : ActivityTimingUi

    data class PlannedMonth(
        val month: String,
    ) : ActivityTimingUi

    data class ConfirmedDate(
        val date: String,
    ) : ActivityTimingUi
}

internal interface ActivityDisplayFormatter {
    fun formatMoney(money: Money): String

    fun formatDate(date: LocalDate): String

    fun formatMonth(month: BudgetMonth): String
}

internal class RegionalActivityDisplayFormatter(
    private val formatter: RegionalFormatter = RegionalFormatter(),
) : ActivityDisplayFormatter {
    override fun formatMoney(money: Money): String = formatter.formatMoney(money)

    override fun formatDate(date: LocalDate): String = formatter.formatDate(date)

    override fun formatMonth(month: BudgetMonth): String = formatter.formatMonth(month)
}

internal class ActivityHistoryLoader(
    private val budgetRepository: BudgetRepository,
    private val formatter: ActivityDisplayFormatter,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val occurrenceUpdater: RecurringOccurrenceUpdater = RecurringOccurrenceUpdater { _, _ -> emptyList() },
) {
    suspend fun load(): ActivityHistoryUiState =
        try {
            val zoneId = zoneIdProvider()
            val generatedOccurrences =
                budgetRepository
                    .loadBudgetSnapshot()
                    ?.currentFunds
                    ?.let { currentFunds ->
                        occurrenceUpdater.refresh(
                            month = BudgetMonth(YearMonth.now(clock)),
                            currency = currentFunds.amount.currency,
                        )
                    }.orEmpty()
            val activities =
                (generatedOccurrences + budgetRepository.loadActivities())
                    .distinctBy { it.id }
            if (activities.isEmpty()) {
                ActivityHistoryUiState.Empty
            } else {
                ActivityHistoryUiState.Ready(
                    items =
                        activities
                            .sortedWith(
                                compareByDescending<ActivityEntry> { it.historyDate(zoneId) }
                                    .thenByDescending { it.bookedAt }
                                    .thenBy { it.id.value },
                            ).map { activity ->
                                ActivityHistoryItemUi(
                                    id = activity.id,
                                    name = activity.name,
                                    direction = activity.direction,
                                    amount = formatter.formatMoney(activity.amount),
                                    timing = activity.toTimingUi(formatter, zoneId),
                                    categoryId = activity.categoryId,
                                    tags = activity.tags.toSortedLabels(),
                                )
                            },
                    customCategories = budgetRepository.loadCustomCategories(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ActivityHistoryUiState.LoadFailed
        }
}

internal data class ActivityEditSeed(
    val activity: ActivityEntry,
    val amountInput: String,
    val timing: ActivityTimingUi,
)

internal sealed interface ActivityEditLoadResult {
    data class Ready(
        val seed: ActivityEditSeed,
    ) : ActivityEditLoadResult

    data object Missing : ActivityEditLoadResult

    data object Failed : ActivityEditLoadResult
}

internal sealed interface ActivityMutationResult {
    data object Saved : ActivityMutationResult

    data object Confirmed : ActivityMutationResult

    data object Deleted : ActivityMutationResult

    data object InvalidName : ActivityMutationResult

    data object InvalidAmount : ActivityMutationResult

    data object AmountMustBePositive : ActivityMutationResult

    data object Missing : ActivityMutationResult

    data object StorageFailed : ActivityMutationResult
}

internal class ActivityEditor(
    private val budgetRepository: BudgetRepository,
    private val moneyAdapter: EntryMoneyAdapter,
    private val formatter: ActivityDisplayFormatter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun load(activityId: ActivityId): ActivityEditLoadResult =
        try {
            val activity =
                budgetRepository.loadActivity(activityId)
                    ?: return ActivityEditLoadResult.Missing
            ActivityEditLoadResult.Ready(
                ActivityEditSeed(
                    activity = activity,
                    amountInput = moneyAdapter.formatInput(activity.amount),
                    timing = activity.toTimingUi(formatter, zoneIdProvider()),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ActivityEditLoadResult.Failed
        }

    suspend fun save(
        seed: ActivityEditSeed,
        nameInput: String,
        amountInput: String,
        direction: Direction,
        tags: Set<Tag> = seed.activity.tags,
        categoryId: CategoryId? = seed.activity.categoryId,
    ): ActivityMutationResult =
        mutate {
            val name = nameInput.trim()
            if (name.isEmpty()) return@mutate ActivityMutationResult.InvalidName
            when (val amount = parseAmount(seed, amountInput)) {
                ActivityAmountResult.Invalid -> ActivityMutationResult.InvalidAmount
                ActivityAmountResult.MustBePositive -> ActivityMutationResult.AmountMustBePositive
                is ActivityAmountResult.Valid -> {
                    val updated =
                        budgetRepository.updateActivityDetails(
                            activityId = seed.activity.id,
                            name = name,
                            direction = direction,
                            amount = amount.money,
                            categoryId = categoryId,
                            tags = tags,
                        )
                    if (updated != null) {
                        ActivityMutationResult.Saved
                    } else {
                        ActivityMutationResult.Missing
                    }
                }
            }
        }

    suspend fun confirm(
        seed: ActivityEditSeed,
        nameInput: String,
        amountInput: String,
        direction: Direction,
        tags: Set<Tag> = seed.activity.tags,
        categoryId: CategoryId? = seed.activity.categoryId,
    ): ActivityMutationResult =
        mutate {
            val name = nameInput.trim()
            if (name.isEmpty()) return@mutate ActivityMutationResult.InvalidName
            when (val amount = parseAmount(seed, amountInput)) {
                ActivityAmountResult.Invalid -> ActivityMutationResult.InvalidAmount
                ActivityAmountResult.MustBePositive -> ActivityMutationResult.AmountMustBePositive
                is ActivityAmountResult.Valid -> {
                    val confirmed =
                        budgetRepository.updateAndConfirmActivity(
                            activityId = seed.activity.id,
                            name = name,
                            direction = direction,
                            amount = amount.money,
                            categoryId = categoryId,
                            tags = tags,
                            bookedAt = clock.instant(),
                        )
                    if (confirmed == null) {
                        ActivityMutationResult.Missing
                    } else {
                        ActivityMutationResult.Confirmed
                    }
                }
            }
        }

    suspend fun delete(activityId: ActivityId): ActivityMutationResult =
        mutate {
            budgetRepository.deleteActivity(activityId)
            ActivityMutationResult.Deleted
        }

    private fun parseAmount(
        seed: ActivityEditSeed,
        amountInput: String,
    ): ActivityAmountResult =
        when (
            val parsed =
                moneyAdapter.parse(
                    input = amountInput,
                    currencyCode = seed.activity.amount.currency,
                    signPolicy = AmountSignPolicy.NON_NEGATIVE,
                )
        ) {
            is MoneyInputResult.Rejected -> ActivityAmountResult.Invalid
            is MoneyInputResult.Accepted ->
                if (parsed.money.minorUnits == 0L) {
                    ActivityAmountResult.MustBePositive
                } else {
                    ActivityAmountResult.Valid(parsed.money)
                }
        }

    private suspend fun mutate(block: suspend () -> ActivityMutationResult): ActivityMutationResult =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ActivityMutationResult.StorageFailed
        }
}

private sealed interface ActivityAmountResult {
    data class Valid(
        val money: Money,
    ) : ActivityAmountResult

    data object Invalid : ActivityAmountResult

    data object MustBePositive : ActivityAmountResult
}

private fun ActivityEntry.historyDate(zoneId: ZoneId): LocalDate =
    bookedAt?.atZone(zoneId)?.toLocalDate()
        ?: expectedOn
        ?: budgetMonth.value.atDay(1)

private fun ActivityEntry.toTimingUi(
    formatter: ActivityDisplayFormatter,
    zoneId: ZoneId,
): ActivityTimingUi =
    when (state) {
        ActivityState.CONFIRMED ->
            ActivityTimingUi.ConfirmedDate(
                formatter.formatDate(requireNotNull(bookedAt).atZone(zoneId).toLocalDate()),
            )

        ActivityState.PLANNED ->
            expectedOn?.let { date ->
                ActivityTimingUi.PlannedDate(formatter.formatDate(date))
            } ?: ActivityTimingUi.PlannedMonth(formatter.formatMonth(budgetMonth))
    }

private fun Set<Tag>.toSortedLabels(): List<String> = map(Tag::value).sortedWith(compareBy({ it.lowercase(java.util.Locale.ROOT) }, { it }))
