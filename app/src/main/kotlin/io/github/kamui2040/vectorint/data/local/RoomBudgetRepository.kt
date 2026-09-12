package io.github.kamui2040.vectorint.data.local

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.BackupDataRepository
import io.github.kamui2040.vectorint.data.BudgetRepository
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import io.github.kamui2040.vectorint.data.RecurringOccurrenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

internal class RoomBudgetRepository(
    private val database: VectorintDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onDataChanged: suspend () -> Unit = {},
) : BudgetRepository,
    RecurringOccurrenceRepository,
    BackupDataRepository {
    override suspend fun loadBudgetSnapshot(): BudgetSnapshot? = onDatabaseThread { database.budgetSnapshotDao().load() }

    override suspend fun saveCurrentFunds(currentFunds: CurrentFunds) = mutate { database.currentFundsDao().save(currentFunds) }

    override suspend fun clearCurrentFunds() = mutate { database.currentFundsDao().clear() }

    override suspend fun loadActivities(): List<ActivityEntry> = onDatabaseThread { database.activityDao().loadAll() }

    override suspend fun loadActivity(activityId: ActivityId): ActivityEntry? = onDatabaseThread { database.activityDao().load(activityId) }

    override suspend fun createActivity(activity: ActivityEntry) =
        mutate {
            requireKnownCategory(activity.categoryId)
            database.activityDao().create(activity)
        }

    override suspend fun saveActivity(activity: ActivityEntry) =
        mutate {
            requireKnownCategory(activity.categoryId)
            database.activityDao().save(activity)
        }

    override suspend fun updateActivity(activity: ActivityEntry): Boolean =
        mutateIfChanged({ it }) {
            requireKnownCategory(activity.categoryId)
            database.activityDao().update(activity)
        }

    override suspend fun updateActivityDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        categoryId: CategoryId?,
    ): ActivityEntry? =
        mutateIfChanged({ it != null }) {
            requireKnownCategory(categoryId)
            database.activityDao().updateDetails(activityId, name, direction, amount, categoryId, tags)
        }

    override suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? = mutateIfChanged({ it != null }) { database.activityDao().confirm(activityId, bookedAt) }

    override suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId?,
    ): ActivityEntry? =
        mutateIfChanged({ it != null }) {
            requireKnownCategory(categoryId)
            database.activityDao().updateAndConfirm(activityId, name, direction, amount, categoryId, tags, bookedAt)
        }

    override suspend fun deleteActivity(activityId: ActivityId) = mutate { database.activityDao().delete(activityId) }

    override suspend fun loadRecurringItems(): List<RecurringItem> = onDatabaseThread { database.recurringItemDao().loadAll() }

    override suspend fun saveRecurringItem(item: RecurringItem) =
        mutate {
            requireKnownCategory(item.categoryId)
            database.recurringItemDao().save(item)
        }

    override suspend fun createRecurringItem(item: RecurringItem) =
        mutate {
            requireKnownCategory(item.categoryId)
            database.recurringItemDao().create(item)
        }

    override suspend fun updateRecurringItem(item: RecurringItem): Boolean =
        mutateIfChanged({ it }) {
            requireKnownCategory(item.categoryId)
            database.recurringItemDao().update(item)
        }

    override suspend fun deleteRecurringItem(recurringItemId: RecurringItemId) =
        mutate { database.recurringItemDao().delete(recurringItemId) }

    override suspend fun ensureRecurringOccurrences(activities: List<ActivityEntry>): List<ActivityEntry> =
        mutateIfChanged({ it.isNotEmpty() }) {
            activities.forEach { requireKnownCategory(it.categoryId) }
            database.activityDao().ensureRecurringOccurrences(activities)
        }

    override suspend fun loadCustomCategories(): List<CustomCategory> = onDatabaseThread { database.customCategoryDao().loadAll() }

    override suspend fun createCustomCategory(category: CustomCategory) = mutate { database.customCategoryDao().create(category) }

    override suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean =
        mutateIfChanged({ it }) {
            if (PredefinedCategory.fromId(categoryId) != null) {
                false
            } else {
                database.customCategoryDao().delete(categoryId)
            }
        }

    override suspend fun loadBackupData(): BackupData =
        onDatabaseThread {
            database.runInTransaction<BackupData> {
                BackupData(
                    currentFunds = database.currentFundsDao().load(),
                    activities = database.activityDao().loadAll(),
                    recurringItems = database.recurringItemDao().loadAll(),
                    customCategories = database.customCategoryDao().loadAll(),
                )
            }
        }

    override suspend fun replaceBackupData(data: BackupData) {
        mutate {
            requireKnownCategoryAssignments(data)
            database.runInTransaction {
                database.activityDao().clear()
                database.recurringItemDao().clear()
                database.tagDao().clear()
                database.customCategoryDao().clear()
                database.currentFundsDao().clear()

                data.currentFunds?.let(database.currentFundsDao()::save)
                data.customCategories.forEach(database.customCategoryDao()::create)
                data.recurringItems.forEach(database.recurringItemDao()::create)
                data.activities.forEach(database.activityDao()::create)
            }
        }
    }

    private fun requireKnownCategory(categoryId: CategoryId?) {
        if (categoryId == null || PredefinedCategory.fromId(categoryId) != null) return
        require(database.customCategoryDao().entityExists(categoryId.value)) {
            "Category assignment is unknown"
        }
    }

    private fun requireKnownCategoryAssignments(data: BackupData) {
        val customIds = data.customCategories.map(CustomCategory::id).toSet()
        require(customIds.size == data.customCategories.size) { "Custom category IDs must be unique" }
        (data.activities.map(ActivityEntry::categoryId) + data.recurringItems.map(RecurringItem::categoryId))
            .filterNotNull()
            .forEach { categoryId ->
                require(PredefinedCategory.fromId(categoryId) != null || categoryId in customIds) {
                    "Category assignment is unknown"
                }
            }
    }

    private suspend fun <T> onDatabaseThread(block: () -> T): T = withContext(ioDispatcher) { block() }

    private suspend fun <T> mutate(block: () -> T): T = onDatabaseThread(block).also { onDataChanged() }

    private suspend fun <T> mutateIfChanged(
        changed: (T) -> Boolean,
        block: () -> T,
    ): T = onDatabaseThread(block).also { result -> if (changed(result)) onDataChanged() }
}
