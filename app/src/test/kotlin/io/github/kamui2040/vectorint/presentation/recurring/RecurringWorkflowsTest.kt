package io.github.kamui2040.vectorint.presentation.recurring

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.OccurrenceTiming
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderSettings
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import io.github.kamui2040.vectorint.presentation.entry.EntryMoneyAdapter
import io.github.kamui2040.vectorint.presentation.format.AmountSignPolicy
import io.github.kamui2040.vectorint.presentation.format.MoneyInputRejection
import io.github.kamui2040.vectorint.presentation.format.MoneyInputResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class RecurringWorkflowsTest {
    private val eur = CurrencyCode.of("EUR")
    private val clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC)
    private val moneyAdapter = FakeRecurringEntryMoneyAdapter()

    @Test
    fun `list sorts names and carries a formatted first occurrence`() =
        runBlocking {
            val customCategory =
                CustomCategory(
                    id = CategoryId("custom_annual"),
                    name = "Annual bills",
                    icon = CategoryIcon.INSURANCE,
                )
            val repository =
                FakeRecurringBudgetRepository(
                    recurringItems =
                        mutableListOf(
                            item("z", "Zoo", 3_000),
                            item("a", "alpha", 1_000),
                            item("b", "Alpha", 2_000).copy(
                                schedule =
                                    RecurringSchedule(
                                        firstOccurrence = LocalDate.of(2026, 1, 1),
                                        timing = OccurrenceTiming.AnyTimeInMonth,
                                    ),
                                categoryId = customCategory.id,
                                tags = setOf(Tag("work"), Tag("Annual")),
                            ),
                        ),
                    customCategories = listOf(customCategory),
                )
            val loader =
                RecurringListLoader(
                    repository,
                    RecurringMoneyFormatter { "minor:${it.minorUnits}" },
                    FakeRecurringScheduleInputAdapter,
                )

            val state = loader.load() as RecurringListUiState.Ready

            assertEquals(listOf("Alpha", "alpha", "Zoo"), state.items.map { it.name })
            assertEquals(listOf("minor:2000", "minor:1000", "minor:3000"), state.items.map { it.amount })
            assertEquals("2026-01", state.items.first().firstOccurrenceLabel)
            assertEquals(customCategory.id, state.items.first().categoryId)
            assertEquals(listOf("Annual", "work"), state.items.first().tags)
            assertEquals(listOf(customCategory), state.customCategories)
        }

    @Test
    fun `new item seed uses Current funds and monthly defaults without forcing more setup`() =
        runBlocking {
            val editor = RecurringItemEditor(FakeRecurringBudgetRepository(), moneyAdapter, clock)

            val seed = (editor.load(null) as RecurringItemLoadResult.Ready).seed

            assertNull(seed.item)
            assertEquals(eur, seed.currencyCode)
            assertEquals(Direction.EXPENSE, seed.direction)
            assertEquals(LocalDate.of(2026, 9, 15), seed.firstOccurrence)
            assertEquals(RecurringTimingChoice.SPECIFIC_DATE, seed.timingChoice)
            assertNull(seed.firstPeriodEndsOn)
            assertEquals("1", seed.repeatEveryInput)
            assertEquals(RecurrenceUnit.MONTHS, seed.repeatUnit)
            assertEquals(BudgetMonthAssignment.OCCURRENCE_MONTH, seed.countsToward)
            assertEquals(false, seed.requireManualConfirmation)
            assertNull(seed.categoryId)
            assertNull(seed.endsOn)
            assertNull(seed.remindOn)
            assertEquals(RecurringReminderInput(false, "1"), seed.occurrenceReminder)
            assertEquals(RecurringReminderInput(false, "7"), seed.remindReminder)
            assertEquals(RecurringReminderInput(false, "7"), seed.endReminder)
        }

    @Test
    fun `income and expense share the same complete schedule model`() =
        runBlocking {
            val repository = FakeRecurringBudgetRepository()
            val editor =
                RecurringItemEditor(
                    budgetRepository = repository,
                    moneyAdapter = moneyAdapter,
                    clock = clock,
                    idFactory = RecurringItemIdFactory { RecurringItemId("salary") },
                    scheduleAdapter = FakeRecurringScheduleInputAdapter,
                )
            val seed = (editor.load(null) as RecurringItemLoadResult.Ready).seed
            val first = LocalDate.of(2026, 9, 30)
            val periodEnd = LocalDate.of(2026, 10, 2)
            val ends = LocalDate.of(2027, 9, 30)
            val remind = LocalDate.of(2027, 6, 1)

            val result =
                editor.save(
                    seed = seed,
                    nameInput = "  Salary  ",
                    amountInput = "25.00",
                    direction = Direction.INCOME,
                    firstOccurrence = first,
                    timingChoice = RecurringTimingChoice.DATE_RANGE,
                    firstPeriodEndsOn = periodEnd,
                    repeatEveryInput = "3",
                    repeatUnit = RecurrenceUnit.MONTHS,
                    countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                    requireManualConfirmation = true,
                    endsOn = ends,
                    remindOn = remind,
                    occurrenceReminder = RecurringReminderInput(true, "0"),
                    remindReminder = RecurringReminderInput(true, "14"),
                    endReminder = RecurringReminderInput(true, "30"),
                    categoryId = PredefinedCategory.SALARY.id,
                    tags = setOf(Tag("work")),
                )

            assertEquals(RecurringItemMutationResult.Saved, result)
            val saved = repository.recurringItems.single()
            assertEquals(Direction.INCOME, saved.direction)
            assertEquals(
                RecurringSchedule(
                    firstOccurrence = first,
                    timing = OccurrenceTiming.DateRange(2),
                    interval = RecurrenceInterval(3, RecurrenceUnit.MONTHS),
                    countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                    requireManualConfirmation = true,
                    endsOn = ends,
                    remindOn = remind,
                ),
                saved.schedule,
            )
            assertEquals(
                ReminderSettings(ReminderLead(0), ReminderLead(14), ReminderLead(30)),
                saved.reminders,
            )
            assertEquals(setOf(Tag("work")), saved.tags)
            assertEquals(PredefinedCategory.SALARY.id, saved.categoryId)
        }

    @Test
    fun `invalid interval or dates fail before storage`() =
        runBlocking {
            val repository = FakeRecurringBudgetRepository()
            val editor = RecurringItemEditor(repository, moneyAdapter, clock, scheduleAdapter = FakeRecurringScheduleInputAdapter)
            val seed = (editor.load(null) as RecurringItemLoadResult.Ready).seed
            val first = LocalDate.of(2026, 10, 1)

            assertEquals(
                RecurringItemMutationResult.InvalidSchedule,
                editor.save(seed, "Rent", "25.00", Direction.EXPENSE, repeatEveryInput = "0"),
            )
            assertEquals(
                RecurringItemMutationResult.InvalidSchedule,
                editor.save(seed, "Rent", "25.00", Direction.EXPENSE, firstOccurrence = first, endsOn = first.minusDays(1)),
            )
            assertEquals(
                RecurringItemMutationResult.InvalidSchedule,
                editor.save(seed, "Rent", "25.00", Direction.EXPENSE, firstOccurrence = first, remindOn = first.minusDays(1)),
            )
            assertEquals(emptyList<RecurringItem>(), repository.recurringItems)
        }

    @Test
    fun `orphaned or invalid reminders fail before storage`() =
        runBlocking {
            val repository = FakeRecurringBudgetRepository()
            val editor = RecurringItemEditor(repository, moneyAdapter, clock, scheduleAdapter = FakeRecurringScheduleInputAdapter)
            val seed = (editor.load(null) as RecurringItemLoadResult.Ready).seed

            assertEquals(
                RecurringItemMutationResult.InvalidReminder,
                editor.save(
                    seed,
                    "Rent",
                    "25.00",
                    Direction.EXPENSE,
                    occurrenceReminder = RecurringReminderInput(true, "3661"),
                ),
            )
            assertEquals(
                RecurringItemMutationResult.InvalidReminder,
                editor.save(
                    seed,
                    "Rent",
                    "25.00",
                    Direction.EXPENSE,
                    remindReminder = RecurringReminderInput(true, "7"),
                ),
            )
            assertEquals(emptyList<RecurringItem>(), repository.recurringItems)
        }

    @Test
    fun `invalid basic fields and storage failures remain distinct`() =
        runBlocking {
            val repository = FakeRecurringBudgetRepository()
            val editor = RecurringItemEditor(repository, moneyAdapter, clock)
            val seed = (editor.load(null) as RecurringItemLoadResult.Ready).seed

            assertEquals(RecurringItemMutationResult.InvalidName, editor.save(seed, "  ", "25.00", Direction.EXPENSE))
            assertEquals(RecurringItemMutationResult.InvalidAmount, editor.save(seed, "Rent", "bad", Direction.EXPENSE))
            assertEquals(RecurringItemMutationResult.AmountMustBePositive, editor.save(seed, "Rent", "0", Direction.EXPENSE))

            val existing = item("rent", "Rent", 25_000)
            val failing = FakeRecurringBudgetRepository(mutableListOf(existing), failWrites = true)
            val failingEditor = RecurringItemEditor(failing, moneyAdapter, clock)
            val editSeed = (failingEditor.load(existing.id) as RecurringItemLoadResult.Ready).seed
            assertEquals(
                RecurringItemMutationResult.StorageFailed,
                failingEditor.save(editSeed, "Changed", "25.00", Direction.INCOME),
            )
            assertEquals(listOf(existing), failing.recurringItems)
        }

    @Test
    fun `missing Current funds and missing edit target remain distinct`() =
        runBlocking {
            val withoutFunds = FakeRecurringBudgetRepository(currentFunds = null)
            assertEquals(
                RecurringItemLoadResult.NeedsCurrentFunds,
                RecurringItemEditor(withoutFunds, moneyAdapter, clock).load(null),
            )
            assertEquals(
                RecurringItemLoadResult.Missing,
                RecurringItemEditor(FakeRecurringBudgetRepository(), moneyAdapter, clock).load(RecurringItemId("missing")),
            )
        }

    private fun item(
        id: String,
        name: String,
        minorUnits: Long,
    ): RecurringItem =
        RecurringItem.monthly(
            id = RecurringItemId(id),
            name = name,
            direction = Direction.EXPENSE,
            amount = Money(minorUnits, eur),
            firstOccurrence = LocalDate.of(2026, 1, 1),
        )
}

private class FakeRecurringEntryMoneyAdapter : EntryMoneyAdapter {
    override fun defaultCurrencyCode(): CurrencyCode = CurrencyCode.of("EUR")

    override fun formatInput(money: Money): String = "${money.minorUnits / 100}.${money.minorUnits % 100}"

    override fun parse(
        input: String,
        currencyCode: CurrencyCode,
        signPolicy: AmountSignPolicy,
    ): MoneyInputResult =
        when (input) {
            "25.00" -> MoneyInputResult.Accepted(Money(2_500, currencyCode))
            "0" -> MoneyInputResult.Accepted(Money(0, currencyCode))
            else -> MoneyInputResult.Rejected(MoneyInputRejection.INVALID_CHARACTER)
        }
}

private data object FakeRecurringScheduleInputAdapter : RecurringScheduleInputAdapter {
    override fun formatDate(date: LocalDate): String = date.toString()

    override fun formatMonth(month: YearMonth): String = month.toString()

    override fun formatWholeNumber(value: Int): String = value.toString()

    override fun parseRepeatEvery(input: String): Int? = input.toIntOrNull()?.takeIf { it in 1..999 }

    override fun parseReminderDays(input: String): Int? = input.toIntOrNull()?.takeIf { it in 0..ReminderLead.MAX_DAYS_BEFORE }
}

private class FakeRecurringBudgetRepository(
    val recurringItems: MutableList<RecurringItem> = mutableListOf(),
    private val currentFunds: CurrentFunds? =
        CurrentFunds(
            amount = Money(100_000, CurrencyCode.of("EUR")),
            capturedAt = Instant.parse("2026-09-01T08:00:00Z"),
        ),
    private val failWrites: Boolean = false,
    private val customCategories: List<CustomCategory> = emptyList(),
) : BudgetRepository {
    override suspend fun loadBudgetSnapshot(): BudgetSnapshot? = currentFunds?.let { BudgetSnapshot(it, emptyList()) }

    override suspend fun loadActivities(): List<ActivityEntry> = error("Not used")

    override suspend fun loadActivity(activityId: ActivityId): ActivityEntry? = error("Not used")

    override suspend fun createActivity(activity: ActivityEntry) = error("Not used")

    override suspend fun saveActivity(activity: ActivityEntry) = error("Not used")

    override suspend fun updateActivity(activity: ActivityEntry): Boolean = error("Not used")

    override suspend fun updateActivityDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used")

    override suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? = error("Not used")

    override suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used")

    override suspend fun deleteActivity(activityId: ActivityId) = error("Not used")

    override suspend fun loadRecurringItems(): List<RecurringItem> = recurringItems.toList()

    override suspend fun saveRecurringItem(item: RecurringItem) {
        if (failWrites) throw IOException("synthetic write failure")
        recurringItems.removeAll { it.id == item.id }
        recurringItems += item
    }

    override suspend fun createRecurringItem(item: RecurringItem) {
        if (recurringItems.any { it.id == item.id }) throw IOException("synthetic identity collision")
        saveRecurringItem(item)
    }

    override suspend fun updateRecurringItem(item: RecurringItem): Boolean {
        if (recurringItems.none { it.id == item.id }) return false
        saveRecurringItem(item)
        return true
    }

    override suspend fun deleteRecurringItem(recurringItemId: RecurringItemId) {
        if (failWrites) throw IOException("synthetic write failure")
        recurringItems.removeAll { it.id == recurringItemId }
    }

    override suspend fun loadCustomCategories(): List<CustomCategory> = customCategories

    override suspend fun createCustomCategory(category: CustomCategory) = error("Not used")

    override suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean = error("Not used")
}
