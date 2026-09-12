package io.github.kamui2040.vectorint.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["name"], unique = true, name = "index_accounts_name")],
)
internal data class AccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "minor_units") val minorUnits: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "captured_at_epoch_second") val capturedAtEpochSecond: Long,
    @ColumnInfo(name = "captured_at_nano") val capturedAtNano: Int,
    @ColumnInfo(name = "include_in_available_now", defaultValue = "1")
    val includeInAvailableNow: Boolean = true,
)

@Entity(
    tableName = "activities",
    indices = [
        Index(value = ["account_id"], name = "index_activities_account_id"),
        Index(
            value = ["recurring_item_id", "recurring_occurrence_key"],
            unique = true,
            name = "index_activities_recurring_occurrence",
        ),
    ],
)
internal data class ActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "''") val name: String = "",
    @ColumnInfo(name = "account_id", defaultValue = "'legacy-main'") val accountId: String = "legacy-main",
    val direction: String,
    @ColumnInfo(name = "minor_units") val minorUnits: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val state: String,
    @ColumnInfo(name = "budget_month") val budgetMonth: String,
    @ColumnInfo(name = "expected_on_epoch_day") val expectedOnEpochDay: Long?,
    @ColumnInfo(name = "booked_at_epoch_second") val bookedAtEpochSecond: Long?,
    @ColumnInfo(name = "booked_at_nano") val bookedAtNano: Int?,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "recurring_item_id") val recurringItemId: String?,
    @ColumnInfo(name = "recurring_occurrence_key") val recurringOccurrenceKey: String?,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
)

@Entity(
    tableName = "recurring_items",
    indices = [Index(value = ["account_id"], name = "index_recurring_items_account_id")],
)
internal data class RecurringItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "account_id", defaultValue = "'legacy-main'") val accountId: String = "legacy-main",
    val direction: String,
    @ColumnInfo(name = "minor_units") val minorUnits: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "timing_kind") val legacyTimingKind: String,
    @ColumnInfo(name = "timing_first_day") val legacyTimingFirstDay: Int?,
    @ColumnInfo(name = "timing_last_day") val legacyTimingLastDay: Int?,
    @ColumnInfo(name = "starts_on_epoch_day") val legacyStartsOnEpochDay: Long,
    @ColumnInfo(name = "first_occurrence_epoch_day", defaultValue = "0") val firstOccurrenceEpochDay: Long,
    @ColumnInfo(name = "repeat_every", defaultValue = "1") val repeatEvery: Int,
    @ColumnInfo(name = "repeat_unit", defaultValue = "'months'") val repeatUnit: String,
    @ColumnInfo(name = "counts_toward", defaultValue = "'occurrence_month'") val countsToward: String,
    @ColumnInfo(name = "schedule_timing_kind", defaultValue = "'specific_date'")
    val scheduleTimingKind: String = "specific_date",
    @ColumnInfo(name = "period_end_offset_days") val periodEndOffsetDays: Int? = null,
    @ColumnInfo(name = "require_manual_confirmation", defaultValue = "0")
    val requireManualConfirmation: Boolean = false,
    @ColumnInfo(name = "ends_at_epoch_day") val endsOnEpochDay: Long?,
    @ColumnInfo(name = "review_on_epoch_day") val remindOnEpochDay: Long?,
    @ColumnInfo(name = "occurrence_reminder_days") val occurrenceReminderDays: Int?,
    @ColumnInfo(name = "review_reminder_days") val remindReminderDays: Int?,
    @ColumnInfo(name = "end_reminder_days") val endReminderDays: Int?,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
)

@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["name"], unique = true, name = "index_custom_categories_name")],
)
internal data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val icon: String,
)

@Entity(tableName = "tags")
internal data class TagEntity(
    @PrimaryKey val name: String,
)

@Entity(
    tableName = "activity_tags",
    primaryKeys = ["activity_id", "tag_name"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tag_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_name"], name = "index_activity_tags_tag_name")],
)
internal data class ActivityTagCrossRef(
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "tag_name") val tagName: String,
)

@Entity(
    tableName = "recurring_item_tags",
    primaryKeys = ["recurring_item_id", "tag_name"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tag_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_name"], name = "index_recurring_item_tags_tag_name")],
)
internal data class RecurringItemTagCrossRef(
    @ColumnInfo(name = "recurring_item_id") val recurringItemId: String,
    @ColumnInfo(name = "tag_name") val tagName: String,
)
