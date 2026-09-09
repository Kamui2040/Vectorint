package io.github.kamui2040.vectorint.presentation.home

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import io.github.kamui2040.vectorint.data.RecurringOccurrenceUpdater
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

class HomeStateLoaderTest {
    private val eur = CurrencyCode.of("EUR")
    private val september = BudgetMonth(YearMonth.of(2026, 9))
    private val baselineTime = Instant.parse("2026-09-01T08:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC)
    private val formatter = FakeHomeValueFormatter()

    @Test
    fun `missing Current funds produces an explicit setup state`() =
        runBlocking {
            val loader = loader(snapshot = null)

            assertEquals(HomeUiState.NeedsCurrentFunds("month:2026-09"), loader.load())
        }

    @Test
    fun `ready state shows confirmed funds reserves and excludes expected income by default`() =
        runBlocking {
            val loader =
                loader(
                    snapshot =
                        snapshot(
                            plannedExpense(25_000),
                            plannedIncome(10_000),
                            confirmedExpense(10_000),
                        ),
                )

            assertEquals(
                HomeUiState.Ready(
                    monthLabel = "month:2026-09",
                    availableNow = "EUR:65000",
                    currentFunds = "EUR:90000",
                    reservedExpenses = "EUR:25000",
                    expectedIncome = ExpectedIncomeUi.Excluded,
                ),
                loader.load(),
            )
        }

    @Test
    fun `ready state includes expected income only after opt in`() =
        runBlocking {
            val loader =
                loader(
                    snapshot = snapshot(plannedExpense(25_000), plannedIncome(10_000), confirmedExpense(10_000)),
                    settings = UserSettings(includeExpectedIncome = true),
                )

            assertEquals(
                HomeUiState.Ready(
                    monthLabel = "month:2026-09",
                    availableNow = "EUR:75000",
                    currentFunds = "EUR:90000",
                    reservedExpenses = "EUR:25000",
                    expectedIncome = ExpectedIncomeUi.Included("EUR:10000"),
                ),
                loader.load(),
            )
        }

    @Test
    fun `negative Available now remains visible when calculation is safe`() =
        runBlocking {
            val loader = loader(snapshot = snapshot(plannedExpense(125_000)))

            assertEquals("EUR:-25000", (loader.load() as HomeUiState.Ready).availableNow)
        }

    @Test
    fun `fresh recurring occurrences are included in the same Home calculation`() =
        runBlocking {
            var refreshCalls = 0
            val loader =
                HomeStateLoader(
                    budgetRepository = FakeBudgetRepository(snapshot()),
                    settingsRepository = FakeSettingsRepository(flowOf(UserSettings())),
                    formatter = formatter,
                    clock = clock,
                    occurrenceUpdater =
                        RecurringOccurrenceUpdater { month, currency ->
                            refreshCalls++
                            assertEquals(september, month)
                            assertEquals(eur, currency)
                            listOf(plannedExpense(25_000))
                        },
                )

            val state = loader.load() as HomeUiState.Ready

            assertEquals(1, refreshCalls)
            assertEquals("EUR:75000", state.availableNow)
            assertEquals("EUR:25000", state.reservedExpenses)
        }

    @Test
    fun `unsafe accounting data never produces a formatted amount`() =
        runBlocking {
            val mixedCurrency =
                plannedExpense(
                    minorUnits = 100,
                    currency = CurrencyCode.of("USD"),
                )
            val loader = loader(snapshot = snapshot(mixedCurrency))

            assertEquals(
                HomeUiState.Unsafe(
                    monthLabel = "month:2026-09",
                    reasons = setOf(UnsafeReason.CURRENCY_MISMATCH),
                ),
                loader.load(),
            )
            assertEquals(emptyList<String>(), formatter.formattedMoney)
        }

    @Test
    fun `current month follows the injected local clock`() =
        runBlocking {
            val localClock =
                Clock.fixed(
                    Instant.parse("2026-10-01T00:30:00Z"),
                    ZoneOffset.ofHours(-1),
                )
            val loader =
                HomeStateLoader(
                    budgetRepository = FakeBudgetRepository(snapshot()),
                    settingsRepository = FakeSettingsRepository(flowOf(UserSettings())),
                    formatter = formatter,
                    clock = localClock,
                )

            assertEquals("month:2026-09", (loader.load() as HomeUiState.Ready).monthLabel)
        }

    @Test
    fun `another month is shown as a flow overview without claiming Available now`() =
        runBlocking {
            val october = BudgetMonth(YearMonth.of(2026, 10))
            val octoberIncome =
                ActivityEntry(
                    id = ActivityId("october-income"),
                    direction = Direction.INCOME,
                    amount = Money(200_000, eur),
                    state = ActivityState.PLANNED,
                    budgetMonth = october,
                )
            val octoberExpense =
                ActivityEntry(
                    id = ActivityId("october-expense"),
                    direction = Direction.EXPENSE,
                    amount = Money(125_000, eur),
                    state = ActivityState.CONFIRMED,
                    budgetMonth = october,
                    bookedAt = baselineTime.plusSeconds(1),
                )
            val loader =
                HomeStateLoader(
                    budgetRepository = FakeBudgetRepository(snapshot(octoberIncome, octoberExpense)),
                    settingsRepository = FakeSettingsRepository(flow { error("Month overview does not need policy") }),
                    formatter = formatter,
                    clock = clock,
                )

            assertEquals(
                HomeUiState.MonthOverview(
                    monthLabel = "month:2026-10",
                    relation = HomeMonthRelation.FUTURE,
                    income = "EUR:200000",
                    expenses = "EUR:125000",
                    net = "EUR:75000",
                    netIsNegative = false,
                ),
                loader.load(october),
            )
        }

    @Test
    fun `future month previews recurring entries without booking or saving them`() =
        runBlocking {
            val october = BudgetMonth(YearMonth.of(2026, 10))
            var refreshCalls = 0
            val salary =
                RecurringItem.monthly(
                    id = RecurringItemId("salary"),
                    name = "Salary",
                    direction = Direction.INCOME,
                    amount = Money(200_000, eur),
                    firstOccurrence = LocalDate.of(2026, 9, 5),
                )
            val loader =
                HomeStateLoader(
                    budgetRepository = FakeBudgetRepository(snapshot(), recurringItems = listOf(salary)),
                    settingsRepository = FakeSettingsRepository(flow { error("Month overview does not need policy") }),
                    formatter = formatter,
                    clock = clock,
                    occurrenceUpdater =
                        RecurringOccurrenceUpdater { _, _ ->
                            refreshCalls++
                            error("Month preview must not persist occurrences")
                        },
                )

            assertEquals(
                HomeUiState.MonthOverview(
                    monthLabel = "month:2026-10",
                    relation = HomeMonthRelation.FUTURE,
                    income = "EUR:200000",
                    expenses = "EUR:0",
                    net = "EUR:200000",
                    netIsNegative = false,
                ),
                loader.load(october),
            )
            assertEquals(0, refreshCalls)
        }

    @Test
    fun `repository failures produce a retryable load failure`() =
        runBlocking {
            val loader =
                HomeStateLoader(
                    budgetRepository = FakeBudgetRepository(failure = IOException("synthetic read failure")),
                    settingsRepository = FakeSettingsRepository(flowOf(UserSettings())),
                    formatter = formatter,
                    clock = clock,
                )

            assertEquals(HomeUiState.LoadFailed("month:2026-09"), loader.load())
        }

    @Test
    fun `settings failures produce a retryable load failure without reading budget data`() =
        runBlocking {
            val budgetRepository = FakeBudgetRepository(snapshot())
            val loader =
                HomeStateLoader(
                    budgetRepository = budgetRepository,
                    settingsRepository =
                        FakeSettingsRepository(
                            flow { throw IOException("synthetic settings failure") },
                        ),
                    formatter = formatter,
                    clock = clock,
                )

            assertEquals(HomeUiState.LoadFailed("month:2026-09"), loader.load())
            assertEquals(0, budgetRepository.loadCalls)
        }

    @Test
    fun `formatting failures produce a load failure instead of partial numbers`() =
        runBlocking {
            val loader =
                HomeStateLoader(
                    budgetRepository = FakeBudgetRepository(snapshot()),
                    settingsRepository = FakeSettingsRepository(flowOf(UserSettings())),
                    formatter = FailingHomeValueFormatter,
                    clock = clock,
                )

            assertEquals(HomeUiState.LoadFailed("month:2026-09"), loader.load())
        }

    @Test
    fun `cancellation is never converted into a load failure`() {
        val cancellation = CancellationException("synthetic cancellation")
        val loader =
            HomeStateLoader(
                budgetRepository = FakeBudgetRepository(failure = cancellation),
                settingsRepository = FakeSettingsRepository(flowOf(UserSettings())),
                formatter = formatter,
                clock = clock,
            )

        val thrown =
            assertThrows(CancellationException::class.java) {
                runBlocking { loader.load() }
            }
        assertEquals(cancellation, thrown)
    }

    private fun loader(
        snapshot: BudgetSnapshot?,
        settings: UserSettings = UserSettings(),
    ): HomeStateLoader =
        HomeStateLoader(
            budgetRepository = FakeBudgetRepository(snapshot),
            settingsRepository = FakeSettingsRepository(flowOf(settings)),
            formatter = formatter,
            clock = clock,
        )

    private fun snapshot(vararg activities: ActivityEntry): BudgetSnapshot =
        BudgetSnapshot(
            currentFunds = CurrentFunds(Money(100_000, eur), baselineTime),
            activities = activities.toList(),
        )

    private fun plannedExpense(
        minorUnits: Long,
        currency: CurrencyCode = eur,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId("expense-$minorUnits-${currency.value}"),
            direction = Direction.EXPENSE,
            amount = Money(minorUnits, currency),
            state = ActivityState.PLANNED,
            budgetMonth = september,
        )

    private fun plannedIncome(minorUnits: Long): ActivityEntry =
        ActivityEntry(
            id = ActivityId("income-$minorUnits"),
            direction = Direction.INCOME,
            amount = Money(minorUnits, eur),
            state = ActivityState.PLANNED,
            budgetMonth = september,
        )

    private fun confirmedExpense(minorUnits: Long): ActivityEntry =
        ActivityEntry(
            id = ActivityId("confirmed-expense-$minorUnits"),
            direction = Direction.EXPENSE,
            amount = Money(minorUnits, eur),
            state = ActivityState.CONFIRMED,
            budgetMonth = september,
            bookedAt = baselineTime.plusSeconds(1),
            source = ActivitySource.OneOff,
        )
}

private class FakeHomeValueFormatter : HomeValueFormatter {
    val formattedMoney = mutableListOf<String>()

    override fun formatMoney(money: Money): String = "${money.currency.value}:${money.minorUnits}".also(formattedMoney::add)

    override fun formatMonth(month: BudgetMonth): String = "month:${month.value}"
}

private data object FailingHomeValueFormatter : HomeValueFormatter {
    override fun formatMoney(money: Money): String = throw IllegalArgumentException("synthetic format failure")

    override fun formatMonth(month: BudgetMonth): String = "month:${month.value}"
}

private class FakeSettingsRepository(
    override val settings: Flow<UserSettings>,
) : SettingsRepository {
    override suspend fun setIncludeExpectedIncome(include: Boolean) = error("Not used by Home")

    override suspend fun save(settings: UserSettings) = error("Not used by Home")
}

private class FakeBudgetRepository(
    private val snapshot: BudgetSnapshot? = null,
    private val failure: Exception? = null,
    private val recurringItems: List<RecurringItem> = emptyList(),
) : BudgetRepository {
    var loadCalls: Int = 0
        private set

    override suspend fun loadBudgetSnapshot(): BudgetSnapshot? {
        loadCalls++
        failure?.let { throw it }
        return snapshot
    }

    override suspend fun saveCurrentFunds(currentFunds: CurrentFunds) = error("Not used by Home")

    override suspend fun clearCurrentFunds() = error("Not used by Home")

    override suspend fun loadActivities(): List<ActivityEntry> = error("Not used by Home")

    override suspend fun loadActivity(activityId: ActivityId): ActivityEntry? = error("Not used by Home")

    override suspend fun createActivity(activity: ActivityEntry) = error("Not used by Home")

    override suspend fun saveActivity(activity: ActivityEntry) = error("Not used by Home")

    override suspend fun updateActivity(activity: ActivityEntry): Boolean = error("Not used by Home")

    override suspend fun updateActivityDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used by Home")

    override suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? = error("Not used by Home")

    override suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId?,
    ): ActivityEntry? = error("Not used by Home")

    override suspend fun deleteActivity(activityId: ActivityId) = error("Not used by Home")

    override suspend fun loadRecurringItems(): List<RecurringItem> = recurringItems

    override suspend fun saveRecurringItem(item: RecurringItem) = error("Not used by Home")

    override suspend fun deleteRecurringItem(recurringItemId: RecurringItemId) = error("Not used by Home")

    override suspend fun loadCustomCategories(): List<CustomCategory> = emptyList()

    override suspend fun createCustomCategory(category: CustomCategory) = error("Not used by Home")

    override suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean = error("Not used by Home")
}
