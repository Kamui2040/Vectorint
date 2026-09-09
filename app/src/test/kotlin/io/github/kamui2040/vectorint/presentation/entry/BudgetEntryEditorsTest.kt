package io.github.kamui2040.vectorint.presentation.entry

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.AvailableFundsCalculator
import io.github.kamui2040.vectorint.core.AvailableFundsResult
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CalculationPolicy
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

class BudgetEntryEditorsTest {
    private val usd = CurrencyCode.of("USD")
    private val initialTime = Instant.parse("2026-09-01T08:00:00Z")
    private val editTime = Instant.parse("2026-09-15T12:34:56.123456789Z")
    private val clock = Clock.fixed(editTime, ZoneOffset.UTC)
    private val moneyAdapter = RegionalEntryMoneyAdapter { Locale.US }

    @Test
    fun `Current funds setup defaults to the regional currency`() =
        runBlocking {
            val result = CurrentFundsEditor(FakeBudgetRepository(), moneyAdapter, clock).load()

            assertEquals(
                CurrentFundsLoadResult.Ready(
                    CurrentFundsFormSeed(
                        amountInput = "",
                        currencyCodeInput = "USD",
                        isEditing = false,
                    ),
                ),
                result,
            )
        }

    @Test
    fun `Current funds editing uses ungrouped regional input and preserves currency`() =
        runBlocking {
            val repository = FakeBudgetRepository(currentFunds = funds(123_456))

            assertEquals(
                CurrentFundsLoadResult.Ready(
                    CurrentFundsFormSeed(
                        amountInput = "1234.56",
                        currencyCodeInput = "USD",
                        isEditing = true,
                    ),
                ),
                CurrentFundsEditor(repository, moneyAdapter, clock).load(),
            )
        }

    @Test
    fun `Current funds save accepts debt and captures a new exact baseline`() =
        runBlocking {
            val repository = FakeBudgetRepository()
            val editor = CurrentFundsEditor(repository, moneyAdapter, clock)

            assertEquals(EntrySaveResult.Saved, editor.save("-12.34", "usd"))
            assertEquals(
                CurrentFunds(Money(-1_234, usd), editTime),
                repository.currentFunds,
            )
        }

    @Test
    fun `invalid Current funds input never writes`() =
        runBlocking {
            val repository = FakeBudgetRepository()
            val editor = CurrentFundsEditor(repository, moneyAdapter, clock)

            assertEquals(EntrySaveResult.InvalidCurrency, editor.save("10.00", "US"))
            assertEquals(EntrySaveResult.InvalidAmount, editor.save("10,00", "USD"))
            assertEquals(0, repository.currentFundsWrites)
        }

    @Test
    fun `confirmed one-off expense changes funds once and a later baseline does not replay it`() =
        runBlocking {
            val repository = FakeBudgetRepository(currentFunds = funds(100_000))
            val activityEditor = activityEditor(repository)

            assertEquals(
                EntrySaveResult.Saved,
                activityEditor.save("25.00", usd, Direction.EXPENSE, ActivityState.CONFIRMED),
            )
            assertAvailableNow(97_500, repository)

            val laterClock = Clock.fixed(editTime.plusSeconds(60), ZoneOffset.UTC)
            assertEquals(
                EntrySaveResult.Saved,
                CurrentFundsEditor(repository, moneyAdapter, laterClock).save("975.00", "USD"),
            )
            assertAvailableNow(97_500, repository)
            assertEquals(1, repository.activities.size)
        }

    @Test
    fun `one-off category and tags are saved without changing the accounting result`() =
        runBlocking {
            val untaggedRepository = FakeBudgetRepository(currentFunds = funds(100_000))
            val taggedRepository = FakeBudgetRepository(currentFunds = funds(100_000))
            val tags = setOf(Tag("household"), Tag("shared"))

            activityEditor(untaggedRepository).save(
                "25.00",
                usd,
                Direction.EXPENSE,
                ActivityState.PLANNED,
            )
            activityEditor(taggedRepository).save(
                "25.00",
                usd,
                Direction.EXPENSE,
                ActivityState.PLANNED,
                tags,
                PredefinedCategory.GROCERIES.id,
            )

            assertEquals(PredefinedCategory.GROCERIES.id, taggedRepository.activities.single().categoryId)
            assertEquals(tags, taggedRepository.activities.single().tags)
            assertEquals(
                calculation(untaggedRepository).availableNow,
                calculation(taggedRepository).availableNow,
            )
        }

    @Test
    fun `planned one-off expense reserves the current month without changing confirmed funds`() =
        runBlocking {
            val repository = FakeBudgetRepository(currentFunds = funds(100_000))

            assertEquals(
                EntrySaveResult.Saved,
                activityEditor(repository).save("25.00", usd, Direction.EXPENSE, ActivityState.PLANNED),
            )

            val activity = repository.activities.single()
            assertEquals(BudgetMonth(YearMonth.of(2026, 9)), activity.budgetMonth)
            assertEquals(LocalDate.of(2026, 9, 15), activity.expectedOn)
            assertEquals(null, activity.bookedAt)
            val result = calculation(repository)
            assertEquals(100_000, result.confirmedFunds.minorUnits)
            assertEquals(2_500, result.reservedExpenses.minorUnits)
            assertEquals(97_500, result.availableNow.minorUnits)
        }

    @Test
    fun `planned one-off income remains excluded without an explicit opt in`() =
        runBlocking {
            val repository = FakeBudgetRepository(currentFunds = funds(100_000))
            activityEditor(repository).save("25.00", usd, Direction.INCOME, ActivityState.PLANNED)

            assertEquals(100_000, calculation(repository).availableNow.minorUnits)
            val included = calculation(repository, CalculationPolicy(includeExpectedIncome = true))
            assertEquals(102_500, included.availableNow.minorUnits)
        }

    @Test
    fun `one-off activity requires Current funds and a positive amount`() =
        runBlocking {
            val repository = FakeBudgetRepository()
            val editor = activityEditor(repository)

            assertEquals(OneOffActivityLoadResult.NeedsCurrentFunds, editor.load())
            assertEquals(
                EntrySaveResult.AmountMustBePositive,
                editor.save("0.00", usd, Direction.EXPENSE, ActivityState.CONFIRMED),
            )
            assertEquals(emptyList<ActivityEntry>(), repository.activities)
        }

    @Test
    fun `one-off activity requires a name and saves it trimmed`() =
        runBlocking {
            val repository = FakeBudgetRepository(currentFunds = funds(100_000))
            val editor = activityEditor(repository)

            assertEquals(
                EntrySaveResult.InvalidName,
                editor.save("   ", "25.00", usd, Direction.EXPENSE, ActivityState.CONFIRMED),
            )
            assertEquals(
                EntrySaveResult.Saved,
                editor.save("  Groceries  ", "25.00", usd, Direction.EXPENSE, ActivityState.CONFIRMED),
            )
            assertEquals("Groceries", repository.activities.single().name)
        }

    @Test
    fun `activity creation failure remains visible and preserves existing data`() =
        runBlocking {
            val existing =
                ActivityEntry(
                    id = ActivityId("existing"),
                    direction = Direction.EXPENSE,
                    amount = Money(500, usd),
                    state = ActivityState.PLANNED,
                    budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                )
            val repository =
                FakeBudgetRepository(
                    currentFunds = funds(100_000),
                    activities = mutableListOf(existing),
                    failCreate = true,
                )

            assertEquals(
                EntrySaveResult.StorageFailed,
                activityEditor(repository).save("25.00", usd, Direction.EXPENSE, ActivityState.CONFIRMED),
            )
            assertEquals(listOf(existing), repository.activities)
        }

    private fun activityEditor(repository: FakeBudgetRepository): OneOffActivityEditor =
        OneOffActivityEditor(
            budgetRepository = repository,
            moneyAdapter = moneyAdapter,
            clock = clock,
            idFactory = ActivityIdFactory { ActivityId("new-one-off") },
        )

    private suspend fun OneOffActivityEditor.save(
        amountInput: String,
        currencyCode: CurrencyCode,
        direction: Direction,
        state: ActivityState,
        tags: Set<Tag> = emptySet(),
        categoryId: CategoryId? = null,
    ): EntrySaveResult =
        save(
            nameInput = "Test entry",
            amountInput = amountInput,
            currencyCode = currencyCode,
            direction = direction,
            state = state,
            tags = tags,
            categoryId = categoryId,
        )

    private fun funds(minorUnits: Long): CurrentFunds = CurrentFunds(Money(minorUnits, usd), initialTime)

    private fun assertAvailableNow(
        expected: Long,
        repository: FakeBudgetRepository,
    ) {
        assertEquals(expected, calculation(repository).availableNow.minorUnits)
    }

    private fun calculation(
        repository: FakeBudgetRepository,
        policy: CalculationPolicy = CalculationPolicy(),
    ): AvailableFundsResult.Available {
        val snapshot = requireNotNull(repository.loadSnapshot())
        val result =
            AvailableFundsCalculator.calculate(
                currentFunds = snapshot.currentFunds,
                month = BudgetMonth(YearMonth.of(2026, 9)),
                activity = snapshot.activities,
                policy = policy,
            )
        assertTrue(result is AvailableFundsResult.Available)
        return result as AvailableFundsResult.Available
    }
}

private class FakeBudgetRepository(
    var currentFunds: CurrentFunds? = null,
    val activities: MutableList<ActivityEntry> = mutableListOf(),
    private val failCreate: Boolean = false,
) : BudgetRepository {
    var currentFundsWrites: Int = 0
        private set

    fun loadSnapshot(): BudgetSnapshot? = currentFunds?.let { BudgetSnapshot(it, activities.toList()) }

    override suspend fun loadBudgetSnapshot(): BudgetSnapshot? = loadSnapshot()

    override suspend fun saveCurrentFunds(currentFunds: CurrentFunds) {
        currentFundsWrites++
        this.currentFunds = currentFunds
    }

    override suspend fun clearCurrentFunds() {
        currentFunds = null
    }

    override suspend fun loadActivities(): List<ActivityEntry> = activities.toList()

    override suspend fun loadActivity(activityId: ActivityId): ActivityEntry? = activities.singleOrNull { it.id == activityId }

    override suspend fun createActivity(activity: ActivityEntry) {
        if (failCreate) error("synthetic create failure")
        check(activities.none { it.id == activity.id })
        activities += activity
    }

    override suspend fun saveActivity(activity: ActivityEntry) {
        activities.removeAll { it.id == activity.id }
        activities += activity
    }

    override suspend fun updateActivity(activity: ActivityEntry): Boolean {
        val index = activities.indexOfFirst { it.id == activity.id }
        if (index < 0) return false
        activities[index] = activity
        return true
    }

    override suspend fun updateActivityDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used by entry editors")

    override suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? = error("Not used by entry editors")

    override suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used by entry editors")

    override suspend fun deleteActivity(activityId: ActivityId) {
        activities.removeAll { it.id == activityId }
    }

    override suspend fun loadRecurringItems(): List<RecurringItem> = emptyList()

    override suspend fun saveRecurringItem(item: RecurringItem) = error("Not used by entry editors")

    override suspend fun deleteRecurringItem(recurringItemId: RecurringItemId) = error("Not used by entry editors")

    override suspend fun loadCustomCategories(): List<CustomCategory> = emptyList()

    override suspend fun createCustomCategory(category: CustomCategory) = error("Not used by entry editors")

    override suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean = error("Not used by entry editors")
}
