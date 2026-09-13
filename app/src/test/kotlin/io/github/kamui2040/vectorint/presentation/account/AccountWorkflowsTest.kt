package io.github.kamui2040.vectorint.presentation.account

import androidx.room.Room
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.data.local.RoomBudgetRepository
import io.github.kamui2040.vectorint.data.local.VectorintDatabase
import io.github.kamui2040.vectorint.presentation.entry.RegionalEntryMoneyAdapter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountWorkflowsTest {
    private val now = Instant.parse("2026-09-13T09:30:00.123456789Z")
    private lateinit var database: VectorintDatabase
    private lateinit var repository: RoomBudgetRepository
    private lateinit var manager: AccountManager
    private var nextId = 0

    @Before
    fun createManager() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    VectorintDatabase::class.java,
                ).build()
        repository = RoomBudgetRepository(database)
        manager =
            AccountManager(
                budgetRepository = repository,
                moneyAdapter = RegionalEntryMoneyAdapter { Locale.GERMANY },
                clock = Clock.fixed(now, ZoneOffset.UTC),
                idFactory = AccountIdFactory { AccountId("account-${++nextId}") },
            )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `first account accepts debt and becomes the complete setup`() =
        runBlocking {
            assertEquals(
                AccountMutationResult.Saved,
                manager.save(
                    existingId = null,
                    nameInput = "  Cash  ",
                    amountInput = "-12,34",
                    currencyCodeInput = "eur",
                    includeInAvailableNow = true,
                ),
            )

            val account = repository.loadAccounts().single()
            assertEquals("Cash", account.name)
            assertEquals(Money(-1_234, CurrencyCode.of("EUR")), account.currentFunds.amount)
            assertEquals(now, account.currentFunds.capturedAt)
            assertEquals(true, account.includeInAvailableNow)
            val loaded = manager.load() as AccountLoadResult.Ready
            assertEquals(
                "-12,34",
                loaded.seed.accounts
                    .single()
                    .amountInput,
            )
        }

    @Test
    fun `renaming and inclusion changes preserve the balance capture time`() =
        runBlocking {
            manager.save(null, "Bank", "100,00", "EUR", true)
            val original = repository.loadAccounts().single()

            assertEquals(
                AccountMutationResult.Saved,
                manager.save(original.id, "Everyday", "100,00", "EUR", false),
            )

            val updated = repository.loadAccounts().single()
            assertEquals("Everyday", updated.name)
            assertEquals(false, updated.includeInAvailableNow)
            assertEquals(original.currentFunds.capturedAt, updated.currentFunds.capturedAt)
        }

    @Test
    fun `names are unique and accounts in use cannot be deleted`() =
        runBlocking {
            manager.save(null, "Bank", "100,00", "EUR", true)
            manager.save(null, "Cash", "20,00", "EUR", true)
            val accounts = repository.loadAccounts()
            val bank = accounts.single { it.name == "Bank" }
            val cash = accounts.single { it.name == "Cash" }

            assertEquals(
                AccountMutationResult.DuplicateName,
                manager.save(cash.id, "bank", "20,00", "EUR", true),
            )

            repository.createActivity(
                ActivityEntry(
                    id = ActivityId("purchase"),
                    name = "Purchase",
                    accountId = bank.id,
                    direction = Direction.EXPENSE,
                    amount = Money(500, CurrencyCode.of("EUR")),
                    state = ActivityState.PLANNED,
                    budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                    expectedOn = LocalDate.of(2026, 9, 13),
                ),
            )

            assertEquals(AccountMutationResult.InUse, manager.delete(bank.id))
            assertEquals(AccountMutationResult.Deleted, manager.delete(cash.id))
            assertEquals(listOf(bank), repository.loadAccounts())
        }
}
