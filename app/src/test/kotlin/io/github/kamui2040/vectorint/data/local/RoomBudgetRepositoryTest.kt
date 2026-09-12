package io.github.kamui2040.vectorint.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import io.github.kamui2040.vectorint.VectorintApplication
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
import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import io.github.kamui2040.vectorint.data.RecurringOccurrenceActivityIdFactory
import io.github.kamui2040.vectorint.data.RecurringOccurrenceCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomBudgetRepositoryTest {
    private val eur = CurrencyCode.of("EUR")
    private val month = BudgetMonth(YearMonth.of(2026, 9))
    private lateinit var database: VectorintDatabase
    private lateinit var repository: RoomBudgetRepository

    @Before
    fun createRepository() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    VectorintDatabase::class.java,
                ).build()
        repository = RoomBudgetRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `repository keeps blocking Room work off the Android main thread`() {
        val funds = currentFunds(100_000, "2026-09-01T10:00:00Z")

        assertThrows(IllegalStateException::class.java) {
            database.currentFundsDao().save(funds)
        }

        runBlocking {
            repository.saveCurrentFunds(funds)
            assertEquals(BudgetSnapshot(funds, emptyList()), repository.loadBudgetSnapshot())
        }
    }

    @Test
    fun `repository exposes the complete local data lifecycle`() =
        runBlocking {
            val funds = currentFunds(100_000, "2026-09-01T10:00:00Z")
            val activity = plannedExpense("groceries", 8_000)
            val item = recurringExpense("rent", 40_000)

            repository.saveCurrentFunds(funds)
            repository.createActivity(activity)
            repository.saveRecurringItem(item)

            assertEquals(BudgetSnapshot(funds, listOf(activity)), repository.loadBudgetSnapshot())
            assertEquals(listOf(activity), repository.loadActivities())
            assertEquals(activity, repository.loadActivity(activity.id))
            assertEquals(listOf(item), repository.loadRecurringItems())

            val updatedActivity = activity.copy(amount = Money(8_500, eur))
            assertTrue(repository.updateActivity(updatedActivity))
            assertEquals(updatedActivity, repository.loadActivity(activity.id))
            assertEquals(false, repository.updateActivity(plannedExpense("missing", 1_000)))

            repository.deleteActivity(activity.id)
            repository.deleteRecurringItem(item.id)
            repository.clearCurrentFunds()

            assertNull(repository.loadBudgetSnapshot())
            assertEquals(emptyList<ActivityEntry>(), repository.loadActivities())
            assertEquals(emptyList<RecurringItem>(), repository.loadRecurringItems())
        }

    @Test
    fun `change callback follows successful mutations and ignores missing updates`() =
        runBlocking {
            var changes = 0
            repository = RoomBudgetRepository(database, onDataChanged = { changes++ })

            assertFalse(repository.updateActivity(plannedExpense("missing", 1_000)))
            repository.saveCurrentFunds(currentFunds(100_000, "2026-09-01T10:00:00Z"))
            repository.createActivity(plannedExpense("groceries", 8_000))
            assertTrue(repository.updateActivity(plannedExpense("groceries", 9_000)))

            assertEquals(3, changes)
        }

    @Test
    fun `custom category deletion keeps financial records and moves them to Other`() =
        runBlocking {
            val category =
                CustomCategory(
                    id = CategoryId("custom_household"),
                    name = "Household",
                    icon = CategoryIcon.HOME,
                )
            val activity = plannedExpense("groceries", 8_000).copy(categoryId = category.id)
            val recurring = recurringExpense("rent", 40_000).copy(categoryId = category.id)

            repository.createCustomCategory(category)
            repository.createActivity(activity)
            repository.createRecurringItem(recurring)

            assertEquals(listOf(category), repository.loadCustomCategories())
            assertEquals(category.id, repository.loadActivity(activity.id)?.categoryId)
            assertEquals(category.id, repository.loadRecurringItems().single().categoryId)
            assertFalse(repository.deleteCustomCategory(PredefinedCategory.HOUSING.id))
            assertTrue(repository.deleteCustomCategory(category.id))

            assertEquals(emptyList<CustomCategory>(), repository.loadCustomCategories())
            assertEquals(activity.copy(categoryId = null), repository.loadActivity(activity.id))
            assertEquals(recurring.copy(categoryId = null), repository.loadRecurringItems().single())
        }

    @Test
    fun `unknown category assignments are rejected before records are written`() {
        val unknown = CategoryId("custom_missing")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.createActivity(plannedExpense("unknown", 1_000).copy(categoryId = unknown))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.createRecurringItem(recurringExpense("unknown", 1_000).copy(categoryId = unknown))
            }
        }
        assertEquals(emptyList<ActivityEntry>(), runBlocking { repository.loadActivities() })
        assertEquals(emptyList<RecurringItem>(), runBlocking { repository.loadRecurringItems() })
    }

    @Test
    fun `confirmation and a later baseline never double count the same expense`() =
        runBlocking {
            val initialFunds = currentFunds(100_000, "2026-09-01T10:00:00Z")
            val expense = recurringExpenseActivity("rent-row", "rent", 40_000)
            repository.saveCurrentFunds(initialFunds)
            repository.saveActivity(expense)

            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))

            val confirmed =
                requireNotNull(
                    repository.confirmActivity(
                        expense.id,
                        Instant.parse("2026-09-02T08:00:00.000000001Z"),
                    ),
                )
            assertEquals(expense.id, confirmed.id)
            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))

            repository.saveCurrentFunds(currentFunds(60_000, "2026-09-03T10:00:00Z"))
            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))
            assertEquals(1, repository.loadActivities().size)
        }

    @Test
    fun `edited confirmation is one atomic update of the planned row`() =
        runBlocking {
            val initialFunds = currentFunds(100_000, "2026-09-01T10:00:00Z")
            val expense = recurringExpenseActivity("rent-row", "rent", 40_000)
            repository.saveCurrentFunds(initialFunds)
            repository.createActivity(expense)

            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))
            val confirmed =
                requireNotNull(
                    repository.updateAndConfirmActivity(
                        activityId = expense.id,
                        name = "Updated rent",
                        direction = Direction.EXPENSE,
                        amount = Money(45_000, eur),
                        tags = setOf(Tag("settled")),
                        bookedAt = Instant.parse("2026-09-02T08:00:00.000000001Z"),
                    ),
                )

            assertEquals(expense.id, confirmed.id)
            assertEquals(expense.source, confirmed.source)
            assertEquals(ActivityState.CONFIRMED, confirmed.state)
            assertEquals(setOf(Tag("settled")), confirmed.tags)
            assertEquals(1, repository.loadActivities().size)
            assertAvailableNow(55_000, requireNotNull(repository.loadBudgetSnapshot()))
        }

    @Test
    fun `detail editing preserves the current stored confirmation state`() =
        runBlocking {
            val planned = plannedExpense("expense", 4_000)
            repository.createActivity(planned)
            val bookingTime = Instant.parse("2026-09-02T08:00:00.000000001Z")
            repository.confirmActivity(planned.id, bookingTime)

            val updated =
                requireNotNull(
                    repository.updateActivityDetails(
                        activityId = planned.id,
                        name = "Refund",
                        direction = Direction.INCOME,
                        amount = Money(5_000, eur),
                        tags = setOf(Tag("adjusted")),
                    ),
                )

            assertEquals(ActivityState.CONFIRMED, updated.state)
            assertEquals(bookingTime, updated.bookedAt)
            assertEquals(planned.id, updated.id)
            assertEquals("Refund", updated.name)
            assertEquals(Direction.INCOME, updated.direction)
            assertEquals(Money(5_000, eur), updated.amount)
            assertEquals(setOf(Tag("adjusted")), updated.tags)
            assertEquals(listOf(updated), repository.loadActivities())
        }

    @Test
    fun `concurrent confirmation preserves one identity and one booking instant`() =
        runBlocking {
            val expense = recurringExpenseActivity("rent-row", "rent", 40_000)
            val firstTime = Instant.parse("2026-09-02T08:00:00Z")
            val secondTime = Instant.parse("2026-09-02T09:00:00Z")
            repository.saveActivity(expense)

            val confirmations =
                coroutineScope {
                    listOf(
                        async { repository.confirmActivity(expense.id, firstTime) },
                        async { repository.confirmActivity(expense.id, secondTime) },
                    ).awaitAll()
                }

            assertNotNull(confirmations[0])
            assertEquals(confirmations[0], confirmations[1])
            assertTrue(confirmations[0]!!.bookedAt in setOf(firstTime, secondTime))
            assertEquals(listOf(confirmations[0]), repository.loadActivities())
        }

    @Test
    fun `confirming unknown activity leaves storage unchanged`() =
        runBlocking {
            assertNull(
                repository.confirmActivity(
                    ActivityId("missing"),
                    Instant.parse("2026-09-02T08:00:00Z"),
                ),
            )
            assertEquals(emptyList<ActivityEntry>(), repository.loadActivities())
        }

    @Test
    fun `database uniqueness failures cross the repository boundary without data loss`() {
        val first = recurringExpenseActivity("rent-a", "rent", 40_000)
        val duplicate = recurringExpenseActivity("rent-b", "rent", 41_000)
        runBlocking { repository.saveActivity(first) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.saveActivity(duplicate) }
        }

        assertEquals(listOf(first), runBlocking { repository.loadActivities() })
    }

    @Test
    fun `ensuring the same recurring occurrence is idempotent and preserves its stored state`() =
        runBlocking {
            val planned = recurringExpenseActivity("rent-a", "rent", 40_000)
            assertEquals(listOf(planned), repository.ensureRecurringOccurrences(listOf(planned)))

            val confirmed =
                requireNotNull(
                    repository.confirmActivity(
                        planned.id,
                        Instant.parse("2026-09-02T08:00:00Z"),
                    ),
                )
            val regenerated = planned.copy(id = ActivityId("rent-b"), amount = Money(45_000, eur))

            assertEquals(listOf(confirmed), repository.ensureRecurringOccurrences(listOf(regenerated)))
            assertEquals(listOf(confirmed), repository.loadActivities())
        }

    @Test
    fun `due recurring occurrence confirms the existing planned row without replacing its details`() =
        runBlocking {
            val planned = recurringExpenseActivity("rent-a", "rent", 40_000).copy(tags = setOf(Tag("edited")))
            repository.ensureRecurringOccurrences(listOf(planned))
            val automaticBooking = Instant.parse("2026-09-05T00:00:00Z")
            val due =
                planned
                    .copy(
                        id = ActivityId("regenerated"),
                        amount = Money(45_000, eur),
                        tags = setOf(Tag("definition")),
                    ).confirm(automaticBooking)

            val confirmed = repository.ensureRecurringOccurrences(listOf(due)).single()

            assertEquals(planned.id, confirmed.id)
            assertEquals(planned.amount, confirmed.amount)
            assertEquals(planned.tags, confirmed.tags)
            assertEquals(ActivityState.CONFIRMED, confirmed.state)
            assertEquals(automaticBooking, confirmed.bookedAt)
            assertEquals(1, repository.loadActivities().size)
        }

    @Test
    fun `ensuring a batch rolls back every new occurrence when one identity conflicts`() {
        val existing = plannedExpense("existing", 2_000)
        val first = recurringExpenseActivity("new", "rent", 40_000)
        val conflicting = recurringExpenseActivity("existing", "salary", 100_000)
        runBlocking { repository.createActivity(existing) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.ensureRecurringOccurrences(listOf(first, conflicting)) }
        }

        assertEquals(listOf(existing), runBlocking { repository.loadActivities() })
    }

    @Test
    fun `monthly refresh after a new baseline never replays a confirmed occurrence`() =
        runBlocking {
            repository.saveCurrentFunds(currentFunds(100_000, "2026-09-01T10:00:00Z"))
            repository.saveRecurringItem(recurringExpense("rent", 40_000))
            var nextId = 0
            val coordinator =
                RecurringOccurrenceCoordinator(
                    repository = repository,
                    idFactory = RecurringOccurrenceActivityIdFactory { ActivityId("generated-${nextId++}") },
                    clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC),
                )

            val generated = coordinator.refresh(month, eur).single()
            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))
            repository.confirmActivity(generated.id, Instant.parse("2026-09-02T08:00:00Z"))
            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))

            repository.saveCurrentFunds(currentFunds(60_000, "2026-09-03T10:00:00Z"))
            val refreshed = coordinator.refresh(month, eur).single()

            assertEquals(generated.id, refreshed.id)
            assertEquals(ActivityState.CONFIRMED, refreshed.state)
            assertEquals(1, repository.loadActivities().size)
            assertAvailableNow(60_000, requireNotNull(repository.loadBudgetSnapshot()))
        }

    @Test
    fun `activity creation never overwrites an existing identity`() {
        val first = plannedExpense("one-off", 4_000)
        val collision = first.copy(amount = Money(9_000, eur))
        runBlocking { repository.createActivity(first) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.createActivity(collision) }
        }

        assertEquals(listOf(first), runBlocking { repository.loadActivities() })
    }

    @Test
    fun `recurring item creation never overwrites an existing identity`() {
        val first = recurringExpense("rent", 40_000)
        val collision = first.copy(amount = Money(45_000, eur))
        runBlocking { repository.createRecurringItem(first) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.createRecurringItem(collision) }
        }

        assertEquals(listOf(first), runBlocking { repository.loadRecurringItems() })
    }

    @Test
    fun `recurring item update cannot recreate a missing definition`() =
        runBlocking {
            assertFalse(repository.updateRecurringItem(recurringExpense("missing", 40_000)))
            assertEquals(emptyList<RecurringItem>(), repository.loadRecurringItems())
        }

    @Test
    fun `an existing activity cannot take another recurring occurrence identity`() {
        val first = recurringExpenseActivity("rent-a", "rent", 40_000)
        val second = recurringExpenseActivity("rent-b", "rent", 41_000, "2026-10")
        val conflictingUpdate = second.copy(source = first.source)
        runBlocking {
            repository.saveActivity(first)
            repository.saveActivity(second)
        }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.saveActivity(conflictingUpdate) }
        }

        assertEquals(listOf(first, second), runBlocking { repository.loadActivities() })
    }

    @Test
    fun `backup replacement swaps the complete dataset in one operation`() =
        runBlocking {
            repository.saveCurrentFunds(currentFunds(10_000, "2026-08-01T10:00:00Z"))
            repository.createActivity(plannedExpense("old", 1_000))
            repository.createRecurringItem(recurringExpense("old-item", 2_000))

            val customCategory =
                CustomCategory(
                    id = CategoryId("custom_replacement"),
                    name = "Replacement",
                    icon = CategoryIcon.WORK,
                )
            val replacementActivity =
                plannedExpense("new", 3_000).copy(
                    categoryId = customCategory.id,
                    tags = setOf(Tag("replacement")),
                )
            val replacementItem =
                recurringExpense("new-item", 4_000).copy(
                    categoryId = PredefinedCategory.INSURANCE.id,
                    tags = setOf(Tag("replacement")),
                )
            val replacement =
                BackupData(
                    currentFunds = currentFunds(20_000, "2026-09-01T10:00:00.123456789Z"),
                    activities = listOf(replacementActivity),
                    recurringItems = listOf(replacementItem),
                    customCategories = listOf(customCategory),
                )

            repository.replaceBackupData(replacement)

            assertEquals(replacement, repository.loadBackupData())
            assertNull(repository.loadActivity(ActivityId("old")))
            assertEquals(listOf(replacementItem), repository.loadRecurringItems())
        }

    @Test
    fun `failed backup replacement rolls the whole database back`() {
        val initialFunds = currentFunds(25_000, "2026-09-01T10:00:00Z")
        val initialActivity = plannedExpense("existing", 2_000)
        val initialItem = recurringExpense("existing-item", 5_000)
        runBlocking {
            repository.saveCurrentFunds(initialFunds)
            repository.createActivity(initialActivity)
            repository.createRecurringItem(initialItem)
        }
        val first = recurringExpenseActivity("duplicate-a", "rent", 4_000)
        val second = recurringExpenseActivity("duplicate-b", "rent", 5_000)
        val invalidReplacement =
            BackupData(
                currentFunds = currentFunds(99_000, "2026-10-01T10:00:00Z"),
                activities = listOf(first, second),
                recurringItems = emptyList(),
            )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.replaceBackupData(invalidReplacement) }
        }

        assertEquals(
            BackupData(initialFunds, listOf(initialActivity), listOf(initialItem)),
            runBlocking { repository.loadBackupData() },
        )
    }

    @Test
    fun `application owns one lazy instance of each repository`() {
        val application = RuntimeEnvironment.getApplication() as VectorintApplication

        assertSame(application.budgetRepository, application.budgetRepository)
        assertSame(application.settingsRepository, application.settingsRepository)
    }

    private fun assertAvailableNow(
        expectedMinorUnits: Long,
        snapshot: BudgetSnapshot,
    ) {
        val result =
            AvailableFundsCalculator.calculate(
                currentFunds = snapshot.currentFunds,
                month = month,
                activity = snapshot.activities,
            )
        assertTrue(result is AvailableFundsResult.Available)
        assertEquals(expectedMinorUnits, (result as AvailableFundsResult.Available).availableNow.minorUnits)
    }

    private fun currentFunds(
        minorUnits: Long,
        capturedAt: String,
    ): CurrentFunds = CurrentFunds(Money(minorUnits, eur), Instant.parse(capturedAt))

    private fun plannedExpense(
        id: String,
        minorUnits: Long,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = Direction.EXPENSE,
            amount = Money(minorUnits, eur),
            state = ActivityState.PLANNED,
            budgetMonth = month,
            expectedOn = LocalDate.of(2026, 9, 5),
        )

    private fun recurringExpenseActivity(
        id: String,
        recurringItemId: String,
        minorUnits: Long,
        occurrenceKey: String = "2026-09",
    ): ActivityEntry =
        plannedExpense(id, minorUnits).copy(
            source =
                ActivitySource.Recurring(
                    itemId = RecurringItemId(recurringItemId),
                    occurrenceKey = occurrenceKey,
                ),
        )

    private fun recurringExpense(
        id: String,
        minorUnits: Long,
    ): RecurringItem =
        RecurringItem.monthly(
            id = RecurringItemId(id),
            name = "Recurring expense",
            direction = Direction.EXPENSE,
            amount = Money(minorUnits, eur),
            firstOccurrence = LocalDate.of(2026, 1, 1),
        )
}
