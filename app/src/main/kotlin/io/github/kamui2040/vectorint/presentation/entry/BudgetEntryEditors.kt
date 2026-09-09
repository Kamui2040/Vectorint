package io.github.kamui2040.vectorint.presentation.entry

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.presentation.format.AmountSignPolicy
import io.github.kamui2040.vectorint.presentation.format.FormatLocaleProvider
import io.github.kamui2040.vectorint.presentation.format.MoneyInputResult
import io.github.kamui2040.vectorint.presentation.format.OsFormatLocaleProvider
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import io.github.kamui2040.vectorint.presentation.format.RegionalMoneyInputParser
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

internal interface EntryMoneyAdapter {
    fun defaultCurrencyCode(): CurrencyCode?

    fun formatInput(money: Money): String

    fun parse(
        input: String,
        currencyCode: CurrencyCode,
        signPolicy: AmountSignPolicy,
    ): MoneyInputResult
}

internal class RegionalEntryMoneyAdapter(
    private val localeProvider: FormatLocaleProvider = OsFormatLocaleProvider,
) : EntryMoneyAdapter {
    private val formatter = RegionalFormatter(localeProvider)
    private val parser = RegionalMoneyInputParser(localeProvider)

    override fun defaultCurrencyCode(): CurrencyCode? {
        val currency =
            try {
                Currency.getInstance(localeProvider.currentFormatLocale())
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (currency.defaultFractionDigits < 0) return null
        return CurrencyCode.of(currency.currencyCode)
    }

    override fun formatInput(money: Money): String = formatter.formatMoneyInput(money)

    override fun parse(
        input: String,
        currencyCode: CurrencyCode,
        signPolicy: AmountSignPolicy,
    ): MoneyInputResult = parser.parse(input, currencyCode, signPolicy)
}

internal data class CurrentFundsFormSeed(
    val amountInput: String,
    val currencyCodeInput: String,
    val isEditing: Boolean,
)

internal sealed interface CurrentFundsLoadResult {
    data class Ready(
        val seed: CurrentFundsFormSeed,
    ) : CurrentFundsLoadResult

    data object Failed : CurrentFundsLoadResult
}

internal sealed interface EntrySaveResult {
    data object Saved : EntrySaveResult

    data object InvalidName : EntrySaveResult

    data object InvalidCurrency : EntrySaveResult

    data object InvalidAmount : EntrySaveResult

    data object AmountMustBePositive : EntrySaveResult

    data object StorageFailed : EntrySaveResult
}

internal class CurrentFundsEditor(
    private val budgetRepository: BudgetRepository,
    private val moneyAdapter: EntryMoneyAdapter,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun load(): CurrentFundsLoadResult =
        try {
            val funds = budgetRepository.loadBudgetSnapshot()?.currentFunds
            CurrentFundsLoadResult.Ready(
                if (funds == null) {
                    CurrentFundsFormSeed(
                        amountInput = "",
                        currencyCodeInput = moneyAdapter.defaultCurrencyCode()?.value.orEmpty(),
                        isEditing = false,
                    )
                } else {
                    CurrentFundsFormSeed(
                        amountInput = moneyAdapter.formatInput(funds.amount),
                        currencyCodeInput = funds.amount.currency.value,
                        isEditing = true,
                    )
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CurrentFundsLoadResult.Failed
        }

    suspend fun save(
        amountInput: String,
        currencyCodeInput: String,
    ): EntrySaveResult {
        val currency = currencyCodeOrNull(currencyCodeInput) ?: return EntrySaveResult.InvalidCurrency
        val money =
            when (val parsed = moneyAdapter.parse(amountInput, currency, AmountSignPolicy.SIGNED)) {
                is MoneyInputResult.Accepted -> parsed.money
                is MoneyInputResult.Rejected -> return EntrySaveResult.InvalidAmount
            }

        return persist {
            budgetRepository.saveCurrentFunds(
                CurrentFunds(
                    amount = money,
                    capturedAt = clock.instant(),
                ),
            )
        }
    }
}

internal data class OneOffActivityFormSeed(
    val currencyCode: CurrencyCode,
)

internal sealed interface OneOffActivityLoadResult {
    data class Ready(
        val seed: OneOffActivityFormSeed,
    ) : OneOffActivityLoadResult

    data object NeedsCurrentFunds : OneOffActivityLoadResult

    data object Failed : OneOffActivityLoadResult
}

internal fun interface ActivityIdFactory {
    fun create(): ActivityId
}

internal class OneOffActivityEditor(
    private val budgetRepository: BudgetRepository,
    private val moneyAdapter: EntryMoneyAdapter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idFactory: ActivityIdFactory = ActivityIdFactory { ActivityId(UUID.randomUUID().toString()) },
) {
    suspend fun load(): OneOffActivityLoadResult =
        try {
            val funds =
                budgetRepository.loadBudgetSnapshot()?.currentFunds
                    ?: return OneOffActivityLoadResult.NeedsCurrentFunds
            OneOffActivityLoadResult.Ready(OneOffActivityFormSeed(funds.amount.currency))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            OneOffActivityLoadResult.Failed
        }

    suspend fun save(
        nameInput: String,
        amountInput: String,
        currencyCode: CurrencyCode,
        direction: Direction,
        state: ActivityState,
        tags: Set<Tag> = emptySet(),
        categoryId: CategoryId? = null,
    ): EntrySaveResult {
        val name = nameInput.trim()
        if (name.isEmpty()) return EntrySaveResult.InvalidName
        val money =
            when (val parsed = moneyAdapter.parse(amountInput, currencyCode, AmountSignPolicy.NON_NEGATIVE)) {
                is MoneyInputResult.Accepted -> parsed.money
                is MoneyInputResult.Rejected -> return EntrySaveResult.InvalidAmount
            }
        if (money.minorUnits == 0L) return EntrySaveResult.AmountMustBePositive

        val now = clock.instant()
        val activity =
            ActivityEntry(
                id = idFactory.create(),
                name = name,
                direction = direction,
                amount = money,
                state = state,
                budgetMonth = BudgetMonth(YearMonth.now(clock)),
                expectedOn = if (state == ActivityState.PLANNED) LocalDate.now(clock) else null,
                bookedAt = if (state == ActivityState.CONFIRMED) now else null,
                source = ActivitySource.OneOff,
                categoryId = categoryId,
                tags = tags,
            )
        return persist { budgetRepository.createActivity(activity) }
    }
}

private fun currencyCodeOrNull(input: String): CurrencyCode? =
    try {
        CurrencyCode.of(input)
    } catch (_: IllegalArgumentException) {
        null
    }

private suspend fun persist(block: suspend () -> Unit): EntrySaveResult =
    try {
        block()
        EntrySaveResult.Saved
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        EntrySaveResult.StorageFailed
    }
