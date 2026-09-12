package io.github.kamui2040.vectorint.presentation.activity

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.AvailableFundsCalculator
import io.github.kamui2040.vectorint.core.AvailableFundsResult
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CategoryIcon
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
import io.github.kamui2040.vectorint.data.RecurringOccurrenceUpdater
import io.github.kamui2040.vectorint.presentation.entry.RegionalEntryMoneyAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

class ActivityWorkflowsTest {
    private val eur = CurrencyCode.of("EUR")
    private val september = BudgetMonth(YearMonth.of(2026, 9))
    private val baselineTime = Instant.parse("2026-09-01T08:00:00Z")
    private val confirmationTime = Instant.parse("2026-09-20T12:34:56.123456789Z")
    private val clock = Clock.fixed(confirmationTime, ZoneOffset.UTC)
    private val formatter = FakeActivityDisplayFormatter()
    private val moneyAdapter = RegionalEntryMoneyAdapter { Locale.US }

    @Test
    fun `history is newest first without inventing a day for month-only timing`() =
        runBlocking {
            val customCategory =
                CustomCategory(
                    id = CategoryId("custom_household"),
                    name = "Household",
                    icon = CategoryIcon.HOME,
                )
            val monthOnly = planned("month-only", expectedOn = null, month = BudgetMonth(YearMonth.of(2026, 10)))
            val confirmed =
                planned("confirmed", expectedOn = null).copy(
                    state = ActivityState.CONFIRMED,
                    bookedAt = Instant.parse("2026-09-20T09:00:00Z"),
                )
            val planned =
                planned("planned", expectedOn = LocalDate.of(2026, 9, 18)).copy(
                    categoryId = customCategory.id,
                    tags = setOf(Tag("shared"), Tag("Household")),
                )
            val repository =
                FakeActivityRepository(
                    activities = mutableListOf(planned, monthOnly, confirmed),
                    customCategories = listOf(customCategory),
                )

            val result =
                ActivityHistoryLoader(
                    budgetRepository = repository,
                    formatter = formatter,
                    zoneIdProvider = { ZoneOffset.UTC },
                ).load()

            assertTrue(result is ActivityHistoryUiState.Ready)
            val ready = result as ActivityHistoryUiState.Ready
            val items = ready.items
            assertEquals(listOf("month-only", "confirmed", "planned"), items.map { it.id.value })
            assertEquals(listOf("month-only", "confirmed", "planned"), items.map { it.name })
            assertEquals(ActivityTimingUi.PlannedMonth("month:2026-10"), items[0].timing)
            assertEquals(ActivityTimingUi.ConfirmedDate("date:2026-09-20"), items[1].timing)
            assertEquals(ActivityTimingUi.PlannedDate("date:2026-09-18"), items[2].timing)
            assertEquals(customCategory.id, items[2].categoryId)
            assertEquals(listOf("Household", "shared"), items[2].tags)
            assertEquals(listOf(customCategory), ready.customCategories)
        }

    @Test
    fun `history orders bookings on the same day from newest to oldest`() =
        runBlocking {
            val older =
                planned("older").copy(
                    state = ActivityState.CONFIRMED,
                    bookedAt = Instant.parse("2026-09-20T09:00:00Z"),
                )
            val newer =
                planned("newer").copy(
                    state = ActivityState.CONFIRMED,
                    bookedAt = Instant.parse("2026-09-20T17:00:00Z"),
                )

            val result =
                ActivityHistoryLoader(
                    budgetRepository = FakeActivityRepository(activities = mutableListOf(older, newer)),
                    formatter = formatter,
                    zoneIdProvider = { ZoneOffset.UTC },
                ).load() as ActivityHistoryUiState.Ready

            assertEquals(listOf("newer", "older"), result.items.map { it.id.value })
        }

    @Test
    fun `history distinguishes empty and failed loads`() =
        runBlocking {
            assertEquals(
                ActivityHistoryUiState.Empty,
                ActivityHistoryLoader(FakeActivityRepository(), formatter).load(),
            )
            assertEquals(
                ActivityHistoryUiState.LoadFailed,
                ActivityHistoryLoader(FakeActivityRepository(failLoads = true), formatter).load(),
            )
        }

    @Test
    fun `history refreshes recurring occurrences for the current month before displaying them`() =
        runBlocking {
            val repository =
                FakeActivityRepository(
                    currentFunds = CurrentFunds(Money(100_000, eur), baselineTime),
                )
            val generated =
                planned("due-income", expectedOn = LocalDate.of(2026, 9, 20))
                    .copy(
                        direction = Direction.INCOME,
                        state = ActivityState.CONFIRMED,
                        bookedAt = confirmationTime,
                    )
            var refreshedMonth: BudgetMonth? = null
            var refreshedCurrency: CurrencyCode? = null

            val result =
                ActivityHistoryLoader(
                    budgetRepository = repository,
                    formatter = formatter,
                    zoneIdProvider = { ZoneOffset.UTC },
                    clock = clock,
                    occurrenceUpdater =
                        RecurringOccurrenceUpdater { month, currency ->
                            refreshedMonth = month
                            refreshedCurrency = currency
                            listOf(generated)
                        },
                ).load()

            assertEquals(september, refreshedMonth)
            assertEquals(eur, refreshedCurrency)
            assertTrue(result is ActivityHistoryUiState.Ready)
            val item = (result as ActivityHistoryUiState.Ready).items.single()
            assertEquals(generated.id, item.id)
            assertEquals(ActivityTimingUi.ConfirmedDate("date:2026-09-20"), item.timing)
        }

    @Test
    fun `editing changes amount direction and tags without changing economic identity`() =
        runBlocking {
            val original =
                planned("activity").copy(
                    source = ActivitySource.Recurring(RecurringItemId("salary"), "2026-09"),
                    tags = setOf(Tag("work")),
                )
            val repository = FakeActivityRepository(activities = mutableListOf(original))
            val editor = editor(repository)
            val seed = readySeed(editor, original.id)

            assertEquals(
                ActivityMutationResult.Saved,
                editor.save(
                    seed,
                    "Renamed salary",
                    "30.00",
                    Direction.INCOME,
                    setOf(Tag("income"), Tag("work")),
                    PredefinedCategory.SALARY.id,
                ),
            )

            val updated = repository.activities.single()
            assertEquals(original.id, updated.id)
            assertEquals("Renamed salary", updated.name)
            assertEquals(original.source, updated.source)
            assertEquals(setOf(Tag("income"), Tag("work")), updated.tags)
            assertEquals(PredefinedCategory.SALARY.id, updated.categoryId)
            assertEquals(original.state, updated.state)
            assertEquals(original.budgetMonth, updated.budgetMonth)
            assertEquals(original.expectedOn, updated.expectedOn)
            assertEquals(Money(3_000, eur), updated.amount)
            assertEquals(Direction.INCOME, updated.direction)
        }

    @Test
    fun `confirmation applies edited planned expense once and a later baseline never replays it`() =
        runBlocking {
            val original = planned("activity", amount = 2_500)
            val repository =
                FakeActivityRepository(
                    currentFunds = CurrentFunds(Money(100_000, eur), baselineTime),
                    activities = mutableListOf(original),
                )
            val editor = editor(repository)
            val seed = readySeed(editor, original.id)

            assertAvailableNow(repository, 97_500)
            assertEquals(
                ActivityMutationResult.Confirmed,
                editor.confirm(seed, original.name, "30.00", Direction.EXPENSE, setOf(Tag("settled"))),
            )

            val confirmed = repository.activities.single()
            assertEquals(original.id, confirmed.id)
            assertEquals(ActivityState.CONFIRMED, confirmed.state)
            assertEquals(confirmationTime, confirmed.bookedAt)
            assertEquals(Money(3_000, eur), confirmed.amount)
            assertEquals(setOf(Tag("settled")), confirmed.tags)
            assertAvailableNow(repository, 97_000)

            repository.currentFunds =
                CurrentFunds(
                    amount = Money(97_000, eur),
                    capturedAt = confirmationTime.plusSeconds(1),
                )
            assertAvailableNow(repository, 97_000)
            assertEquals(1, repository.activities.size)
        }

    @Test
    fun `deleting one activity preserves every other entry`() =
        runBlocking {
            val deleted = planned("delete")
            val preserved = planned("preserve")
            val repository = FakeActivityRepository(activities = mutableListOf(deleted, preserved))

            assertEquals(ActivityMutationResult.Deleted, editor(repository).delete(deleted.id))
            assertEquals(listOf(preserved), repository.activities)
        }

    @Test
    fun `invalid edits missing rows and storage failures stay visible`() =
        runBlocking {
            val original = planned("activity")
            val repository = FakeActivityRepository(activities = mutableListOf(original))
            val editor = editor(repository)
            val seed = readySeed(editor, original.id)

            assertEquals(ActivityMutationResult.InvalidName, editor.save(seed, "   ", "1.00", Direction.EXPENSE))
            assertEquals(ActivityMutationResult.InvalidAmount, editor.save(seed, original.name, "1,00", Direction.EXPENSE))
            assertEquals(
                ActivityMutationResult.AmountMustBePositive,
                editor.save(seed, original.name, "0.00", Direction.EXPENSE),
            )
            repository.activities.clear()
            assertEquals(ActivityMutationResult.Missing, editor.save(seed, original.name, "1.00", Direction.EXPENSE))
            assertEquals(listOf<ActivityEntry>(), repository.activities)

            val failed = FakeActivityRepository(activities = mutableListOf(original), failMutations = true)
            assertEquals(
                ActivityMutationResult.StorageFailed,
                editor(failed).delete(original.id),
            )
            assertEquals(listOf(original), failed.activities)
        }

    @Test
    fun `cancellation is never converted into a load or mutation failure`() {
        val repository = FakeActivityRepository(cancelLoads = true)
        val loader = ActivityHistoryLoader(repository, formatter)

        assertThrows(CancellationException::class.java) {
            runBlocking { loader.load() }
        }
    }

    private fun editor(repository: BudgetRepository): ActivityEditor =
        ActivityEditor(
            budgetRepository = repository,
            moneyAdapter = moneyAdapter,
            formatter = formatter,
            clock = clock,
            zoneIdProvider = { ZoneOffset.UTC },
        )

    private suspend fun readySeed(
        editor: ActivityEditor,
        id: ActivityId,
    ): ActivityEditSeed {
        val result = editor.load(id)
        assertTrue(result is ActivityEditLoadResult.Ready)
        return (result as ActivityEditLoadResult.Ready).seed
    }

    private fun planned(
        id: String,
        amount: Long = 2_500,
        expectedOn: LocalDate? = LocalDate.of(2026, 9, 15),
        month: BudgetMonth = september,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            name = id,
            direction = Direction.EXPENSE,
            amount = Money(amount, eur),
            state = ActivityState.PLANNED,
            budgetMonth = month,
            expectedOn = expectedOn,
        )

    private fun assertAvailableNow(
        repository: FakeActivityRepository,
        expectedMinorUnits: Long,
    ) {
        val snapshot = requireNotNull(repository.loadSnapshot())
        val result =
            AvailableFundsCalculator.calculate(
                accounts = snapshot.accounts,
                month = september,
                activity = snapshot.activities,
            )
        assertTrue(result is AvailableFundsResult.Available)
        assertEquals(expectedMinorUnits, (result as AvailableFundsResult.Available).availableNow.minorUnits)
    }
}

private class FakeActivityDisplayFormatter : ActivityDisplayFormatter {
    override fun formatMoney(money: Money): String = "money:${money.minorUnits}"

    override fun formatDate(date: LocalDate): String = "date:$date"

    override fun formatMonth(month: BudgetMonth): String = "month:${month.value}"
}

private class FakeActivityRepository(
    var currentFunds: CurrentFunds? =
        CurrentFunds(
            Money(0, CurrencyCode.of("EUR")),
            Instant.EPOCH,
        ),
    val activities: MutableList<ActivityEntry> = mutableListOf(),
    private val failLoads: Boolean = false,
    private val failMutations: Boolean = false,
    private val cancelLoads: Boolean = false,
    private val customCategories: List<CustomCategory> = emptyList(),
) : BudgetRepository {
    fun loadSnapshot(): BudgetSnapshot? = currentFunds?.let { BudgetSnapshot(it, activities.toList()) }

    override suspend fun loadBudgetSnapshot(): BudgetSnapshot? = loadSnapshot()

    override suspend fun loadActivities(): List<ActivityEntry> {
        if (cancelLoads) throw CancellationException("synthetic cancellation")
        if (failLoads) throw IOException("synthetic load failure")
        return activities.toList()
    }

    override suspend fun loadActivity(activityId: ActivityId): ActivityEntry? {
        if (cancelLoads) throw CancellationException("synthetic cancellation")
        if (failLoads) throw IOException("synthetic load failure")
        return activities.singleOrNull { it.id == activityId }
    }

    override suspend fun createActivity(activity: ActivityEntry) {
        if (failMutations) throw IOException("synthetic create failure")
        check(activities.none { it.id == activity.id })
        activities += activity
    }

    override suspend fun saveActivity(activity: ActivityEntry) {
        if (failMutations) throw IOException("synthetic save failure")
        val index = activities.indexOfFirst { it.id == activity.id }
        if (index >= 0) activities[index] = activity else activities += activity
    }

    override suspend fun updateActivity(activity: ActivityEntry): Boolean {
        if (failMutations) throw IOException("synthetic update failure")
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
    ): ActivityEntry? {
        if (failMutations) throw IOException("synthetic update failure")
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return null
        return activities[index]
            .copy(
                name = name,
                direction = direction,
                amount = amount,
                categoryId = categoryId,
                tags = tags,
            ).also { activities[index] = it }
    }

    override suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? {
        if (failMutations) throw IOException("synthetic confirmation failure")
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return null
        return activities[index].confirm(bookedAt).also { activities[index] = it }
    }

    override suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId?,
    ): ActivityEntry? {
        if (failMutations) throw IOException("synthetic confirmation failure")
        val index = activities.indexOfFirst { it.id == activityId }
        if (index < 0) return null
        return activities[index]
            .copy(
                name = name,
                direction = direction,
                amount = amount,
                categoryId = categoryId,
                tags = tags,
            ).confirm(bookedAt)
            .also { activities[index] = it }
    }

    override suspend fun deleteActivity(activityId: ActivityId) {
        if (failMutations) throw IOException("synthetic delete failure")
        activities.removeAll { it.id == activityId }
    }

    override suspend fun loadRecurringItems(): List<RecurringItem> = emptyList()

    override suspend fun saveRecurringItem(item: RecurringItem) = error("Not used by Activity workflows")

    override suspend fun deleteRecurringItem(recurringItemId: RecurringItemId) = error("Not used by Activity workflows")

    override suspend fun loadCustomCategories(): List<CustomCategory> = customCategories

    override suspend fun createCustomCategory(category: CustomCategory) = error("Not used by Activity workflows")

    override suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean = error("Not used by Activity workflows")
}
