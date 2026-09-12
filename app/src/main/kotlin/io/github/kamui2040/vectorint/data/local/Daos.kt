package io.github.kamui2040.vectorint.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import java.time.Instant

@Dao
internal abstract class AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertEntity(entity: AccountEntity)

    @Update
    abstract fun updateEntity(entity: AccountEntity): Int

    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE, name, id")
    abstract fun loadEntities(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    abstract fun loadEntity(accountId: String): AccountEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE id = :accountId)")
    abstract fun entityExists(accountId: String): Boolean

    @Query("SELECT COUNT(*) FROM activities WHERE account_id = :accountId")
    abstract fun activityReferenceCount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM recurring_items WHERE account_id = :accountId")
    abstract fun recurringReferenceCount(accountId: String): Int

    @Query("DELETE FROM accounts WHERE id = :accountId")
    abstract fun deleteEntity(accountId: String): Int

    @Query("DELETE FROM accounts")
    abstract fun clear()

    fun create(account: Account) = insertEntity(account.toEntity())

    fun loadAll(): List<Account> = loadEntities().map(AccountEntity::toDomain)

    fun load(accountId: AccountId): Account? = loadEntity(accountId.value)?.toDomain()

    fun update(account: Account): Boolean = updateEntity(account.toEntity()) == 1

    @Transaction
    open fun delete(accountId: AccountId): Boolean {
        if (activityReferenceCount(accountId.value) != 0 || recurringReferenceCount(accountId.value) != 0) {
            return false
        }
        return deleteEntity(accountId.value) == 1
    }
}

@Dao
internal abstract class ActivityDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertEntity(entity: ActivityEntity)

    @Update
    abstract fun updateEntity(entity: ActivityEntity): Int

    @Query("SELECT EXISTS(SELECT 1 FROM activities WHERE id = :activityId)")
    abstract fun entityExists(activityId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertTagCrossRefs(crossRefs: List<ActivityTagCrossRef>)

    @Query("DELETE FROM activity_tags WHERE activity_id = :activityId")
    abstract fun deleteTagCrossRefs(activityId: String)

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :activityId")
    abstract fun loadRecord(activityId: String): ActivityRecord?

    @Transaction
    @Query(
        """
        SELECT * FROM activities
        WHERE recurring_item_id = :recurringItemId
          AND recurring_occurrence_key = :occurrenceKey
        """,
    )
    abstract fun loadRecurringRecord(
        recurringItemId: String,
        occurrenceKey: String,
    ): ActivityRecord?

    @Transaction
    @Query("SELECT * FROM activities ORDER BY id")
    abstract fun loadRecords(): List<ActivityRecord>

    @Query("DELETE FROM activities WHERE id = :activityId")
    abstract fun deleteEntity(activityId: String)

    @Query("DELETE FROM activities")
    abstract fun clear()

    @Query("SELECT COUNT(*) FROM activities")
    abstract fun countEntities(): Int

    @Query("SELECT COUNT(*) FROM activity_tags WHERE activity_id = :activityId")
    abstract fun countTagCrossRefs(activityId: String): Int

    @Transaction
    open fun save(activity: ActivityEntry) {
        val record = activity.toRecord()
        if (entityExists(record.activity.id)) {
            check(updateEntity(record.activity) == 1) { "Stored activity disappeared during update" }
        } else {
            insertEntity(record.activity)
        }
        replaceTags(record)
    }

    @Transaction
    open fun create(activity: ActivityEntry) {
        val record = activity.toRecord()
        insertEntity(record.activity)
        replaceTags(record)
    }

    @Transaction
    open fun ensureRecurringOccurrences(activities: List<ActivityEntry>): List<ActivityEntry> =
        activities.map { activity ->
            val source =
                activity.source as? ActivitySource.Recurring
                    ?: error("Only recurring activity can be ensured as an occurrence")
            val stored = loadRecurringRecord(source.itemId.value, source.occurrenceKey)?.toDomain()
            when {
                stored == null -> activity.also(::create)
                stored.state == ActivityState.PLANNED && activity.state == ActivityState.CONFIRMED -> {
                    stored.confirm(requireNotNull(activity.bookedAt)).also(::save)
                }

                else -> stored
            }
        }

    @Transaction
    open fun update(activity: ActivityEntry): Boolean {
        val record = activity.toRecord()
        if (updateEntity(record.activity) != 1) return false
        replaceTags(record)
        return true
    }

    @Transaction
    open fun updateDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        categoryId: CategoryId?,
        tags: Set<Tag>,
    ): ActivityEntry? {
        val activity = loadRecord(activityId.value)?.toDomain() ?: return null
        val updated =
            activity.copy(
                name = name,
                direction = direction,
                amount = amount,
                categoryId = categoryId,
                tags = tags,
            )
        check(update(updated)) { "Stored activity disappeared during update" }
        return updated
    }

    private fun replaceTags(record: ActivityRecord) {
        deleteTagCrossRefs(record.activity.id)
        if (record.tags.isNotEmpty()) {
            insertTags(record.tags)
            insertTagCrossRefs(
                record.tags.map { tag ->
                    ActivityTagCrossRef(
                        activityId = record.activity.id,
                        tagName = tag.name,
                    )
                },
            )
        }
    }

    fun load(activityId: ActivityId): ActivityEntry? = loadRecord(activityId.value)?.toDomain()

    fun loadAll(): List<ActivityEntry> = loadRecords().map(ActivityRecord::toDomain)

    @Transaction
    open fun confirm(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry? {
        val activity = loadRecord(activityId.value)?.toDomain() ?: return null
        return activity.confirm(bookedAt).also(::save)
    }

    @Transaction
    open fun updateAndConfirm(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        categoryId: CategoryId?,
        tags: Set<Tag>,
        bookedAt: Instant,
    ): ActivityEntry? {
        val activity = loadRecord(activityId.value)?.toDomain() ?: return null
        val confirmed =
            activity
                .copy(
                    name = name,
                    direction = direction,
                    amount = amount,
                    categoryId = categoryId,
                    tags = tags,
                ).confirm(bookedAt)
        check(update(confirmed)) { "Stored activity disappeared during confirmation" }
        return confirmed
    }

    fun delete(activityId: ActivityId) = deleteEntity(activityId.value)
}

@Dao
internal abstract class RecurringItemDao {
    @Upsert
    abstract fun upsertEntity(entity: RecurringItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertEntity(entity: RecurringItemEntity)

    @Update
    abstract fun updateEntity(entity: RecurringItemEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertTagCrossRefs(crossRefs: List<RecurringItemTagCrossRef>)

    @Query("DELETE FROM recurring_item_tags WHERE recurring_item_id = :recurringItemId")
    abstract fun deleteTagCrossRefs(recurringItemId: String)

    @Transaction
    @Query("SELECT * FROM recurring_items WHERE id = :recurringItemId")
    abstract fun loadRecord(recurringItemId: String): RecurringItemRecord?

    @Transaction
    @Query("SELECT * FROM recurring_items ORDER BY id")
    abstract fun loadRecords(): List<RecurringItemRecord>

    @Query("DELETE FROM recurring_items WHERE id = :recurringItemId")
    abstract fun deleteEntity(recurringItemId: String)

    @Query("DELETE FROM recurring_items")
    abstract fun clear()

    @Query("SELECT COUNT(*) FROM recurring_items")
    abstract fun countEntities(): Int

    @Query("SELECT COUNT(*) FROM recurring_item_tags WHERE recurring_item_id = :recurringItemId")
    abstract fun countTagCrossRefs(recurringItemId: String): Int

    @Transaction
    open fun save(item: RecurringItem) {
        val record = item.toRecord()
        upsertEntity(record.item)
        replaceTags(record)
    }

    @Transaction
    open fun create(item: RecurringItem) {
        val record = item.toRecord()
        insertEntity(record.item)
        replaceTags(record)
    }

    @Transaction
    open fun update(item: RecurringItem): Boolean {
        val record = item.toRecord()
        if (updateEntity(record.item) != 1) return false
        replaceTags(record)
        return true
    }

    private fun replaceTags(record: RecurringItemRecord) {
        deleteTagCrossRefs(record.item.id)
        if (record.tags.isNotEmpty()) {
            insertTags(record.tags)
            insertTagCrossRefs(
                record.tags.map { tag ->
                    RecurringItemTagCrossRef(
                        recurringItemId = record.item.id,
                        tagName = tag.name,
                    )
                },
            )
        }
    }

    fun load(recurringItemId: RecurringItemId): RecurringItem? = loadRecord(recurringItemId.value)?.toDomain()

    fun loadAll(): List<RecurringItem> = loadRecords().map(RecurringItemRecord::toDomain)

    fun delete(recurringItemId: RecurringItemId) = deleteEntity(recurringItemId.value)
}

@Dao
internal abstract class TagDao {
    @Query("DELETE FROM tags")
    abstract fun clear()
}

@Dao
internal abstract class CustomCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertEntity(entity: CustomCategoryEntity)

    @Query("SELECT * FROM custom_categories ORDER BY name COLLATE NOCASE, name, id")
    abstract fun loadEntities(): List<CustomCategoryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM custom_categories WHERE id = :categoryId)")
    abstract fun entityExists(categoryId: String): Boolean

    @Query("DELETE FROM custom_categories WHERE id = :categoryId")
    abstract fun deleteEntity(categoryId: String): Int

    @Query("UPDATE activities SET category_id = NULL WHERE category_id = :categoryId")
    abstract fun clearActivityAssignments(categoryId: String)

    @Query("UPDATE recurring_items SET category_id = NULL WHERE category_id = :categoryId")
    abstract fun clearRecurringAssignments(categoryId: String)

    @Query("DELETE FROM custom_categories")
    abstract fun clear()

    fun create(category: CustomCategory) = insertEntity(category.toEntity())

    fun loadAll(): List<CustomCategory> = loadEntities().map(CustomCategoryEntity::toDomain)

    @Transaction
    open fun delete(categoryId: CategoryId): Boolean {
        if (deleteEntity(categoryId.value) != 1) return false
        clearActivityAssignments(categoryId.value)
        clearRecurringAssignments(categoryId.value)
        return true
    }
}

@Dao
internal abstract class BudgetSnapshotDao {
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE, name, id")
    abstract fun loadAccountEntities(): List<AccountEntity>

    @Transaction
    @Query("SELECT * FROM activities ORDER BY id")
    abstract fun loadActivityRecords(): List<ActivityRecord>

    @Transaction
    open fun load(): BudgetSnapshot? {
        val accounts = loadAccountEntities().map(AccountEntity::toDomain)
        val activities = loadActivityRecords().map(ActivityRecord::toDomain)
        if (accounts.isEmpty() && activities.isEmpty()) return null
        return BudgetSnapshot(
            accounts = accounts,
            activities = activities,
        )
    }
}
