package io.github.kamui2040.vectorint.presentation.account

import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.presentation.entry.EntryMoneyAdapter
import io.github.kamui2040.vectorint.presentation.format.AmountSignPolicy
import io.github.kamui2040.vectorint.presentation.format.MoneyInputResult
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.util.Locale
import java.util.UUID

internal data class AccountListItemUi(
    val account: Account,
    val amountInput: String,
)

internal data class AccountManagementSeed(
    val accounts: List<AccountListItemUi>,
    val currencyCode: CurrencyCode?,
)

internal sealed interface AccountLoadResult {
    data class Ready(
        val seed: AccountManagementSeed,
    ) : AccountLoadResult

    data object Failed : AccountLoadResult
}

internal sealed interface AccountMutationResult {
    data object Saved : AccountMutationResult

    data object Deleted : AccountMutationResult

    data object InvalidName : AccountMutationResult

    data object DuplicateName : AccountMutationResult

    data object InvalidCurrency : AccountMutationResult

    data object InvalidAmount : AccountMutationResult

    data object Missing : AccountMutationResult

    data object InUse : AccountMutationResult

    data object StorageFailed : AccountMutationResult
}

internal fun interface AccountIdFactory {
    fun create(): AccountId
}

internal class AccountManager(
    private val budgetRepository: BudgetRepository,
    private val moneyAdapter: EntryMoneyAdapter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idFactory: AccountIdFactory = AccountIdFactory { AccountId(UUID.randomUUID().toString()) },
) {
    suspend fun load(): AccountLoadResult =
        try {
            val accounts = budgetRepository.loadAccounts()
            val currencies = accounts.map { it.currentFunds.amount.currency }.distinct()
            require(currencies.size <= 1) { "Account currencies must match" }
            AccountLoadResult.Ready(
                AccountManagementSeed(
                    accounts =
                        accounts.map { account ->
                            AccountListItemUi(
                                account = account,
                                amountInput = moneyAdapter.formatInput(account.currentFunds.amount),
                            )
                        },
                    currencyCode = currencies.singleOrNull() ?: moneyAdapter.defaultCurrencyCode(),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AccountLoadResult.Failed
        }

    suspend fun save(
        existingId: AccountId?,
        nameInput: String,
        amountInput: String,
        currencyCodeInput: String,
        includeInAvailableNow: Boolean,
    ): AccountMutationResult {
        val name = nameInput.trim()
        if (name.isEmpty() || name.length > Account.MAX_NAME_LENGTH) return AccountMutationResult.InvalidName

        return mutate {
            val accounts = budgetRepository.loadAccounts()
            val existing = existingId?.let { id -> accounts.singleOrNull { it.id == id } }
            if (existingId != null && existing == null) return@mutate AccountMutationResult.Missing
            if (
                accounts.any { account ->
                    account.id != existingId && account.name.equals(name, ignoreCase = true)
                }
            ) {
                return@mutate AccountMutationResult.DuplicateName
            }

            val currency =
                existing?.currentFunds?.amount?.currency
                    ?: accounts
                        .firstOrNull()
                        ?.currentFunds
                        ?.amount
                        ?.currency
                    ?: currencyCodeOrNull(currencyCodeInput)
                    ?: return@mutate AccountMutationResult.InvalidCurrency
            val money =
                when (val parsed = moneyAdapter.parse(amountInput, currency, AmountSignPolicy.SIGNED)) {
                    is MoneyInputResult.Accepted -> parsed.money
                    is MoneyInputResult.Rejected -> return@mutate AccountMutationResult.InvalidAmount
                }
            val currentFunds =
                if (existing != null && existing.currentFunds.amount == money) {
                    existing.currentFunds
                } else {
                    CurrentFunds(money, clock.instant())
                }
            val account =
                Account(
                    id = existingId ?: idFactory.create(),
                    name = name,
                    currentFunds = currentFunds,
                    includeInAvailableNow = includeInAvailableNow,
                )
            if (existing == null) {
                budgetRepository.createAccount(account)
                AccountMutationResult.Saved
            } else if (budgetRepository.updateAccount(account)) {
                AccountMutationResult.Saved
            } else {
                AccountMutationResult.Missing
            }
        }
    }

    suspend fun setIncluded(
        accountId: AccountId,
        included: Boolean,
    ): AccountMutationResult =
        mutate {
            val account =
                budgetRepository.loadAccounts().singleOrNull { it.id == accountId }
                    ?: return@mutate AccountMutationResult.Missing
            if (account.includeInAvailableNow == included ||
                budgetRepository.updateAccount(account.copy(includeInAvailableNow = included))
            ) {
                AccountMutationResult.Saved
            } else {
                AccountMutationResult.Missing
            }
        }

    suspend fun delete(accountId: AccountId): AccountMutationResult =
        mutate {
            if (budgetRepository.loadAccounts().none { it.id == accountId }) {
                AccountMutationResult.Missing
            } else if (budgetRepository.deleteAccount(accountId)) {
                AccountMutationResult.Deleted
            } else {
                AccountMutationResult.InUse
            }
        }

    private suspend fun mutate(block: suspend () -> AccountMutationResult): AccountMutationResult =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AccountMutationResult.StorageFailed
        }
}

private fun currencyCodeOrNull(input: String): CurrencyCode? =
    try {
        CurrencyCode.of(input.uppercase(Locale.ROOT))
    } catch (_: IllegalArgumentException) {
        null
    }
