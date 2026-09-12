package io.github.kamui2040.vectorint.presentation.overview

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
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
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import io.github.kamui2040.vectorint.data.RecurringOccurrenceUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class OverviewStateLoaderTest {
    private val eur = CurrencyCode.of("EUR")
    private val september = BudgetMonth(YearMonth.of(2026, 9))
    private val baselineTime = Instant.parse("2026-09-01T08:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC)
    private val formatter = FakeOverviewValueFormatter()

    @Test
    fun missingCurrentFundsProducesSetupState() =
        runBlocking {
            val loader = loader(snapshot = null)

            assertEquals(OverviewUiState.NeedsCurrentFunds("month:2026-09"), loader.load())
        }

    @Test
    fun currentMonthGroupsStoredAndFreshExpensesOnce() =
        runBlocking {
            val customCategory =
                CustomCategory(
                    id = CategoryId("custom_pets"),
                    name = "Pets",
                    icon = CategoryIcon.PETS,
                )
            var refreshCalls = 0
            val loader =
                OverviewStateLoader(
                    budgetRepository =
                        FakeOverviewBudgetRepository(
                            snapshot =
                                snapshot(
                                    activity("rent", 50_000, Direction.EXPENSE, PredefinedCategory.HOUSING.id),
                                    activity("other", 20_000, Direction.EXPENSE),
                                    activity("salary", 200_000, Direction.INCOME, PredefinedCategory.SALARY.id),
                                ),
                            customCategories = listOf(customCategory),
                        ),
                    formatter = formatter,
                    clock = clock,
                    occurrenceUpdater =
                        RecurringOccurrenceUpdater { month, currency ->
                            refreshCalls++
                            assertEquals(september, month)
                            assertEquals(eur, currency)
                            listOf(activity("vet", 30_000, Direction.EXPENSE, customCategory.id))
                        },
                )

            val state = loader.load() as OverviewUiState.Ready

            assertEquals(1, refreshCalls)
            assertEquals("EUR:100000", state.totalExpenses)
            assertEquals(
                listOf(PredefinedCategory.HOUSING.id, customCategory.id, null),
                state.slices.map(OverviewSliceUi::categoryId),
            )
            assertEquals(listOf("EUR:50000", "EUR:30000", "EUR:20000"), state.slices.map(OverviewSliceUi::amount))
            assertEquals(listOf("50000/100000", "30000/100000", "20000/100000"), state.slices.map(OverviewSliceUi::share))
            assertEquals(listOf(0.5f, 0.3f, 0.2f), state.slices.map(OverviewSliceUi::fraction))
            assertEquals(listOf(customCategory), state.customCategories)
        }

    @Test
    fun futureMonthPreviewsRecurringExpensesWithoutPersistingThem() =
        runBlocking {
            val october = BudgetMonth(YearMonth.of(2026, 10))
            val groceries =
                RecurringItem
                    .monthly(
                        id = RecurringItemId("groceries"),
                        name = "Groceries",
                        direction = Direction.EXPENSE,
                        amount = Money(30_000, eur),
                        firstOccurrence = LocalDate.of(2026, 9, 5),
                    ).copy(categoryId = PredefinedCategory.GROCERIES.id)
            var refreshCalls = 0
            val loader =
                OverviewStateLoader(
                    budgetRepository =
                        FakeOverviewBudgetRepository(
                            snapshot = snapshot(),
                            recurringItems = listOf(groceries),
                        ),
                    formatter = formatter,
                    clock = clock,
                    occurrenceUpdater =
                        RecurringOccurrenceUpdater { _, _ ->
                            refreshCalls++
                            error("A preview must not persist occurrences")
                        },
                    previewActivityId = { ActivityId("preview-groceries") },
                )

            val state = loader.load(october) as OverviewUiState.Ready

            assertEquals(0, refreshCalls)
            assertEquals("EUR:30000", state.totalExpenses)
            assertEquals(PredefinedCategory.GROCERIES.id, state.slices.single().categoryId)
            assertEquals("30000/30000", state.slices.single().share)
        }

    @Test
    fun storedRecurringExpenseReplacesItsPreviewInsteadOfBeingCountedTwice() =
        runBlocking {
            val october = BudgetMonth(YearMonth.of(2026, 10))
            val groceries =
                RecurringItem
                    .monthly(
                        id = RecurringItemId("groceries"),
                        name = "Groceries",
                        direction = Direction.EXPENSE,
                        amount = Money(30_000, eur),
                        firstOccurrence = LocalDate.of(2026, 9, 5),
                    ).copy(categoryId = PredefinedCategory.GROCERIES.id)
            val occurrenceDate = LocalDate.of(2026, 10, 5)
            val stored =
                activity(
                    id = "stored-groceries",
                    minorUnits = 30_000,
                    direction = Direction.EXPENSE,
                    categoryId = groceries.categoryId,
                    month = october,
                    source =
                        ActivitySource.Recurring(
                            itemId = groceries.id,
                            occurrenceKey = groceries.schedule.occurrenceKey(occurrenceDate),
                        ),
                )
            val loader =
                OverviewStateLoader(
                    budgetRepository =
                        FakeOverviewBudgetRepository(
                            snapshot = snapshot(stored),
                            recurringItems = listOf(groceries),
                        ),
                    formatter = formatter,
                    clock = clock,
                    previewActivityId = { ActivityId("preview-groceries") },
                )

            val state = loader.load(october) as OverviewUiState.Ready

            assertEquals("EUR:30000", state.totalExpenses)
            assertEquals(1, state.slices.size)
        }

    @Test
    fun monthWithoutPositiveExpensesProducesExplicitEmptyState() =
        runBlocking {
            val loader =
                loader(
                    snapshot =
                        snapshot(
                            activity("salary", 200_000, Direction.INCOME),
                            activity("zero", 0, Direction.EXPENSE),
                        ),
                )

            assertEquals(OverviewUiState.Empty("month:2026-09"), loader.load())
        }

    @Test
    fun unknownCategoryFailsClosedWithoutFormattingAmounts() =
        runBlocking {
            val loader =
                loader(
                    snapshot =
                        snapshot(
                            activity(
                                id = "unknown",
                                minorUnits = 10_000,
                                direction = Direction.EXPENSE,
                                categoryId = CategoryId("custom_missing"),
                            ),
                        ),
                )

            assertEquals(
                OverviewUiState.Unsafe(
                    monthLabel = "month:2026-09",
                    reasons = setOf(UnsafeReason.UNKNOWN_CATEGORY_ASSIGNMENT),
                ),
                loader.load(),
            )
            assertEquals(emptyList<String>(), formatter.formattedMoney)
        }

    @Test
    fun repositoryFailureProducesRetryableState() =
        runBlocking {
            val loader =
                OverviewStateLoader(
                    budgetRepository = FakeOverviewBudgetRepository(failure = IOException("synthetic read failure")),
                    formatter = formatter,
                    clock = clock,
                )

            assertEquals(OverviewUiState.LoadFailed("month:2026-09"), loader.load())
        }

    @Test
    fun cancellationIsNotConvertedIntoLoadFailure() {
        val cancellation = CancellationException("synthetic cancellation")
        val loader =
            OverviewStateLoader(
                budgetRepository = FakeOverviewBudgetRepository(failure = cancellation),
                formatter = formatter,
                clock = clock,
            )

        val thrown =
            assertThrows(CancellationException::class.java) {
                runBlocking { loader.load() }
            }
        assertEquals(cancellation, thrown)
    }

    private fun loader(snapshot: BudgetSnapshot?): OverviewStateLoader =
        OverviewStateLoader(
            budgetRepository = FakeOverviewBudgetRepository(snapshot = snapshot),
            formatter = formatter,
            clock = clock,
        )

    private fun snapshot(vararg activities: ActivityEntry): BudgetSnapshot =
        BudgetSnapshot(
            currentFunds = CurrentFunds(Money(100_000, eur), baselineTime),
            activities = activities.toList(),
        )

    private fun activity(
        id: String,
        minorUnits: Long,
        direction: Direction,
        categoryId: CategoryId? = null,
        month: BudgetMonth = september,
        source: ActivitySource = ActivitySource.OneOff,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = direction,
            amount = Money(minorUnits, eur),
            state = ActivityState.PLANNED,
            budgetMonth = month,
            source = source,
            categoryId = categoryId,
        )
}

private class FakeOverviewValueFormatter : OverviewValueFormatter {
    val formattedMoney = mutableListOf<String>()

    override fun formatMoney(money: Money): String = (money.currency.value + ":" + money.minorUnits).also(formattedMoney::add)

    override fun formatMonth(month: BudgetMonth): String = "month:" + month.value

    override fun formatShare(
        part: Money,
        total: Money,
    ): String = part.minorUnits.toString() + "/" + total.minorUnits
}

private class FakeOverviewBudgetRepository(
    private val snapshot: BudgetSnapshot? = null,
    private val customCategories: List<CustomCategory> = emptyList(),
    private val recurringItems: List<RecurringItem> = emptyList(),
    private val failure: Exception? = null,
) : BudgetRepository {
    override suspend fun loadBudgetSnapshot(): BudgetSnapshot? {
        failure?.let { throw it }
        return snapshot
    }

    override suspend fun loadActivities(): List<ActivityEntry> = error("Not used by Overview")

    override suspend fun loadActivity(activityId: ActivityId): ActivityEntry? = error("Not used by Overview")

    override suspend fun createActivity(activity: ActivityEntry) = error("Not used by Overview")

    override suspend fun saveActivity(activity: ActivityEntry) = error("Not used by Overview")

    override suspend fun updateActivity(activity: ActivityEntry): Boolean = error("Not used by Overview")

    override suspend fun updateActivityDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used by Overview")

    override suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? = error("Not used by Overview")

    override suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used by Overview")

    override suspend fun deleteActivity(activityId: ActivityId) = error("Not used by Overview")

    override suspend fun loadRecurringItems(): List<RecurringItem> = recurringItems

    override suspend fun saveRecurringItem(item: RecurringItem) = error("Not used by Overview")

    override suspend fun deleteRecurringItem(recurringItemId: RecurringItemId) = error("Not used by Overview")

    override suspend fun loadCustomCategories(): List<CustomCategory> = customCategories

    override suspend fun createCustomCategory(category: CustomCategory) = error("Not used by Overview")

    override suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean = error("Not used by Overview")
}
