package io.github.kamui2040.vectorint.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CurrentFundsEntity::class,
        ActivityEntity::class,
        RecurringItemEntity::class,
        TagEntity::class,
        ActivityTagCrossRef::class,
        RecurringItemTagCrossRef::class,
        CustomCategoryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
internal abstract class VectorintDatabase : RoomDatabase() {
    abstract fun currentFundsDao(): CurrentFundsDao

    abstract fun activityDao(): ActivityDao

    abstract fun recurringItemDao(): RecurringItemDao

    abstract fun tagDao(): TagDao

    abstract fun customCategoryDao(): CustomCategoryDao

    abstract fun budgetSnapshotDao(): BudgetSnapshotDao

    companion object {
        private const val DATABASE_NAME = "vectorint.db"

        fun create(context: Context): VectorintDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    VectorintDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        internal val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN first_occurrence_epoch_day INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN repeat_every INTEGER NOT NULL DEFAULT 1",
                    )
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN repeat_unit TEXT NOT NULL DEFAULT 'months'",
                    )
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN counts_toward TEXT NOT NULL DEFAULT 'occurrence_month'",
                    )

                    db.execSQL(
                        "UPDATE recurring_items SET first_occurrence_epoch_day = starts_on_epoch_day",
                    )
                }
            }

        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN schedule_timing_kind TEXT NOT NULL DEFAULT 'specific_date'",
                    )
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN period_end_offset_days INTEGER",
                    )
                    db.execSQL(
                        "ALTER TABLE recurring_items ADD COLUMN require_manual_confirmation INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        """
                        UPDATE recurring_items
                        SET schedule_timing_kind = CASE timing_kind
                            WHEN 'day_range' THEN 'date_range'
                            WHEN 'any_time_in_month' THEN 'any_time_in_month'
                            ELSE 'specific_date'
                        END,
                        period_end_offset_days = CASE timing_kind
                            WHEN 'day_range' THEN timing_last_day - timing_first_day
                            ELSE NULL
                        END
                        """.trimIndent(),
                    )
                }
            }

        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS custom_categories (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT COLLATE NOCASE NOT NULL,
                            icon TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX index_custom_categories_name ON custom_categories (name)",
                    )
                    db.execSQL("ALTER TABLE activities ADD COLUMN category_id TEXT")
                    db.execSQL("ALTER TABLE recurring_items ADD COLUMN category_id TEXT")
                }
            }

        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE activities ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        """
                        UPDATE activities
                        SET name = COALESCE(
                            (SELECT recurring_items.name
                             FROM recurring_items
                             WHERE recurring_items.id = activities.recurring_item_id),
                            ''
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
