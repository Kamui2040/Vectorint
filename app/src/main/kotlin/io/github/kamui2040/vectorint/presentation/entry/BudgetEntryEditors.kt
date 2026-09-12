package io.github.kamui2040.vectorint.presentation.entry

import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
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

internal sealed interface EntrySaveResult {
    data object Saved : EntrySaveResult

    data object InvalidName : EntrySaveResult

    data object InvalidCurrency : EntrySaveResult

    data object InvalidAmount : EntrySaveResult

    data object AmountMustBePositive : EntrySaveResult

    data object StorageFailed : EntrySaveResult
}

internal data class OneOffActivityFormSeed(
    val currencyCode: CurrencyCode,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: AccountId = io.github.kamui2040.vectorint.core.LEGACY_DEFAULT_ACCOUNT_ID,
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
            val accounts = budgetRepository.loadAccounts()
            if (accounts.isEmpty()) return OneOffActivityLoadResult.NeedsCurrentFunds
            val currencies = accounts.map { it.currentFunds.amount.currency }.distinct()
            require(currencies.size == 1) { "Account currencies must match" }
            OneOffActivityLoadResult.Ready(
                OneOffActivityFormSeed(
                    accounts = accounts,
                    selectedAccountId = accounts.firstOrNull(Account::includeInAvailableNow)?.id ?: accounts.first().id,
                    currencyCode = currencies.single(),
                ),
            )
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
        accountId: AccountId = io.github.kamui2040.vectorint.core.LEGACY_DEFAULT_ACCOUNT_ID,
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
                accountId = accountId,
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

private suspend fun persist(block: suspend () -> Unit): EntrySaveResult =
    try {
        block()
        EntrySaveResult.Saved
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        EntrySaveResult.StorageFailed
    }
