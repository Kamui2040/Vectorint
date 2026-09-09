package io.github.kamui2040.vectorint.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.OccurrenceTiming
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderSettings
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BudgetSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VectorintDatabaseTest {
    private val eur = CurrencyCode.of("EUR")
    private lateinit var database: VectorintDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    VectorintDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `Current funds is replaced as one exact baseline row`() {
        val first =
            CurrentFunds(
                amount = Money(100_000, eur),
                capturedAt = Instant.parse("2026-09-01T10:00:00.000000001Z"),
            )
        val replacement =
            CurrentFunds(
                amount = Money(92_500, eur),
                capturedAt = Instant.parse("2026-09-05T12:30:00.999999999Z"),
            )

        database.currentFundsDao().save(first)
        database.currentFundsDao().save(replacement)

        assertEquals(replacement, database.currentFundsDao().load())
    }

    @Test
    fun `budget snapshot stays unavailable until a Current funds baseline exists`() {
        database.activityDao().save(oneOffActivity("purchase"))

        assertNull(database.budgetSnapshotDao().load())
    }

    @Test
    fun `budget snapshot reads baseline and activity as domain values`() {
        val funds =
            CurrentFunds(
                amount = Money(100_000, eur),
                capturedAt = Instant.parse("2026-09-01T10:00:00.000000001Z"),
            )
        val activity = oneOffActivity("purchase")
        database.currentFundsDao().save(funds)
        database.activityDao().save(activity)

        assertEquals(
            BudgetSnapshot(funds, listOf(activity)),
            database.budgetSnapshotDao().load(),
        )
    }

    @Test
    fun `confirmation updates the same recurring occurrence row`() {
        val planned = recurringActivity("rent-row", "rent", "2026-09")
        val confirmed =
            planned
                .confirm(Instant.parse("2026-09-02T08:00:00.000000001Z"))
                .copy(tags = setOf(Tag("settled")))

        database.activityDao().save(planned)
        database.activityDao().save(confirmed)

        assertEquals(1, database.activityDao().countEntities())
        assertEquals(confirmed, database.activityDao().load(planned.id))
        assertEquals(1, database.activityDao().countTagCrossRefs(planned.id.value))
    }

    @Test
    fun `duplicate recurring economic occurrence is rejected atomically`() {
        val first = recurringActivity("rent-a", "rent", "2026-09").copy(tags = emptySet())
        val duplicate = recurringActivity("rent-b", "rent", "2026-09").copy(tags = emptySet())

        database.activityDao().save(first)

        assertThrows(SQLiteConstraintException::class.java) {
            database.activityDao().save(duplicate)
        }
        assertEquals(listOf(first), database.activityDao().loadAll())
    }

    @Test
    fun `different recurring items can use the same occurrence key`() {
        val first = recurringActivity("activity-a", "rent", "2026-09")
        val second = recurringActivity("activity-b", "salary", "2026-09")

        database.activityDao().save(first)
        database.activityDao().save(second)

        assertEquals(listOf(first, second), database.activityDao().loadAll())
    }

    @Test
    fun `independent one-off activity rows can coexist`() {
        val first = oneOffActivity("groceries")
        val second = oneOffActivity("transport")

        database.activityDao().save(first)
        database.activityDao().save(second)

        assertEquals(listOf(first, second), database.activityDao().loadAll())
    }

    @Test
    fun `recurring item persists schedule reminders and tags`() {
        val item =
            RecurringItem(
                id = RecurringItemId("salary"),
                name = "Salary",
                direction = Direction.INCOME,
                amount = Money(250_000, eur),
                schedule =
                    RecurringSchedule(
                        firstOccurrence = LocalDate.of(2026, 1, 31),
                        timing = OccurrenceTiming.DateRange(2),
                        interval = RecurrenceInterval(3, RecurrenceUnit.MONTHS),
                        countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                        requireManualConfirmation = true,
                        endsOn = LocalDate.of(2027, 12, 31),
                        remindOn = LocalDate.of(2026, 11, 1),
                    ),
                reminders =
                    ReminderSettings(
                        occurrence = ReminderLead(1),
                        remind = ReminderLead(14),
                        end = ReminderLead(30),
                    ),
                tags = setOf(Tag("income"), Tag("work")),
            )

        database.recurringItemDao().save(item)

        assertEquals(item, database.recurringItemDao().load(item.id))
        assertEquals(2, database.recurringItemDao().countTagCrossRefs(item.id.value))
    }

    @Test
    fun `saving an aggregate replaces its tag links`() {
        val original = oneOffActivity("purchase").copy(tags = setOf(Tag("home"), Tag("shared")))
        val updated = original.copy(tags = setOf(Tag("reviewed")))

        database.activityDao().save(original)
        database.activityDao().save(updated)

        assertEquals(updated, database.activityDao().load(updated.id))
        assertEquals(1, database.activityDao().countTagCrossRefs(updated.id.value))
    }

    @Test
    fun `deleting an aggregate cascades its tag links`() {
        val activity = oneOffActivity("purchase").copy(tags = setOf(Tag("home"), Tag("shared")))

        database.activityDao().save(activity)
        database.activityDao().delete(activity.id)

        assertNull(database.activityDao().load(activity.id))
        assertEquals(0, database.activityDao().countTagCrossRefs(activity.id.value))
    }

    @Test
    fun `deleting a recurring item cascades its tag links`() {
        val item =
            RecurringItem
                .monthly(
                    id = RecurringItemId("subscription"),
                    name = "Subscription",
                    direction = Direction.EXPENSE,
                    amount = Money(1_500, eur),
                    firstOccurrence = LocalDate.of(2026, 1, 1),
                ).copy(tags = setOf(Tag("service"), Tag("shared")))

        database.recurringItemDao().save(item)
        database.recurringItemDao().delete(item.id)

        assertNull(database.recurringItemDao().load(item.id))
        assertEquals(0, database.recurringItemDao().countTagCrossRefs(item.id.value))
    }

    @Test
    fun `deleting a recurring definition preserves its activity history`() {
        val item =
            RecurringItem.monthly(
                id = RecurringItemId("rent"),
                name = "Rent",
                direction = Direction.EXPENSE,
                amount = Money(40_000, eur),
                firstOccurrence = LocalDate.of(2026, 1, 1),
            )
        val activity = recurringActivity("rent-2026-09", item.id.value, "2026-09")

        database.recurringItemDao().save(item)
        database.activityDao().save(activity)
        database.recurringItemDao().delete(item.id)

        assertNull(database.recurringItemDao().load(item.id))
        assertEquals(activity, database.activityDao().load(activity.id))
    }

    @Test
    fun `version one recurring schedule migrates through version three without data loss`() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(RuntimeEnvironment.getApplication())
                    .name(null)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL(
                                    """
                                    CREATE TABLE recurring_items (
                                        id TEXT NOT NULL PRIMARY KEY,
                                        name TEXT NOT NULL,
                                        direction TEXT NOT NULL,
                                        minor_units INTEGER NOT NULL,
                                        currency_code TEXT NOT NULL,
                                        timing_kind TEXT NOT NULL,
                                        timing_first_day INTEGER,
                                        timing_last_day INTEGER,
                                        starts_on_epoch_day INTEGER NOT NULL,
                                        ends_at_epoch_day INTEGER,
                                        review_on_epoch_day INTEGER,
                                        occurrence_reminder_days INTEGER,
                                        review_reminder_days INTEGER,
                                        end_reminder_days INTEGER
                                    )
                                    """.trimIndent(),
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.use {
            val db = helper.writableDatabase
            val startsOn = LocalDate.of(2026, 2, 1)
            db.execSQL(
                """
                INSERT INTO recurring_items (
                    id, name, direction, minor_units, currency_code,
                    timing_kind, timing_first_day, timing_last_day,
                    starts_on_epoch_day, ends_at_epoch_day, review_on_epoch_day,
                    occurrence_reminder_days, review_reminder_days, end_reminder_days
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy",
                    "Legacy",
                    "expense",
                    1_000,
                    "EUR",
                    "exact_day",
                    31,
                    null,
                    startsOn.toEpochDay(),
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
            )

            VectorintDatabase.MIGRATION_1_2.migrate(db)
            VectorintDatabase.MIGRATION_2_3.migrate(db)

            db
                .query(
                    """
                    SELECT first_occurrence_epoch_day, repeat_every, repeat_unit, counts_toward,
                           schedule_timing_kind, period_end_offset_days, require_manual_confirmation
                    FROM recurring_items
                    """.trimIndent(),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), cursor.getLong(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals("months", cursor.getString(2))
                    assertEquals("occurrence_month", cursor.getString(3))
                    assertEquals("specific_date", cursor.getString(4))
                    assertNull(cursor.getString(5))
                    assertEquals(0, cursor.getInt(6))
                }
        }
    }

    @Test
    fun `version three gains categories without changing existing records`() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(RuntimeEnvironment.getApplication())
                    .name(null)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(3) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE activities (id TEXT NOT NULL PRIMARY KEY)")
                                db.execSQL("CREATE TABLE recurring_items (id TEXT NOT NULL PRIMARY KEY)")
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.use {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO activities (id) VALUES ('activity')")
            db.execSQL("INSERT INTO recurring_items (id) VALUES ('recurring')")

            VectorintDatabase.MIGRATION_3_4.migrate(db)

            db.query("SELECT id, category_id FROM activities").use { cursor ->
                cursor.moveToFirst()
                assertEquals("activity", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
            }
            db.query("SELECT id, category_id FROM recurring_items").use { cursor ->
                cursor.moveToFirst()
                assertEquals("recurring", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
            }
            db.execSQL(
                "INSERT INTO custom_categories (id, name, icon) VALUES ('custom_trips', 'Trips', 'travel')",
            )
            assertThrows(SQLiteConstraintException::class.java) {
                db.execSQL(
                    "INSERT INTO custom_categories (id, name, icon) VALUES ('custom_other', 'trips', 'other')",
                )
            }
        }
    }

    @Test
    fun `version four adds activity names and preserves recurring names`() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(RuntimeEnvironment.getApplication())
                    .name(null)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(4) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE recurring_items (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)",
                                )
                                db.execSQL(
                                    "CREATE TABLE activities (id TEXT NOT NULL PRIMARY KEY, recurring_item_id TEXT)",
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.use {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO recurring_items (id, name) VALUES ('rent', 'Rent')")
            db.execSQL("INSERT INTO activities (id, recurring_item_id) VALUES ('recurring', 'rent')")
            db.execSQL("INSERT INTO activities (id, recurring_item_id) VALUES ('one-off', NULL)")

            VectorintDatabase.MIGRATION_4_5.migrate(db)

            db.query("SELECT id, name FROM activities ORDER BY id").use { cursor ->
                cursor.moveToFirst()
                assertEquals("one-off", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                cursor.moveToNext()
                assertEquals("recurring", cursor.getString(0))
                assertEquals("Rent", cursor.getString(1))
            }
        }
    }

    private fun recurringActivity(
        id: String,
        recurringItemId: String,
        occurrenceKey: String,
    ): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = Direction.EXPENSE,
            amount = Money(40_000, eur),
            state = ActivityState.PLANNED,
            budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
            expectedOn = LocalDate.of(2026, 9, 1),
            source =
                ActivitySource.Recurring(
                    itemId = RecurringItemId(recurringItemId),
                    occurrenceKey = occurrenceKey,
                ),
            tags = setOf(Tag("housing")),
        )

    private fun oneOffActivity(id: String): ActivityEntry =
        ActivityEntry(
            id = ActivityId(id),
            direction = Direction.EXPENSE,
            amount = Money(2_500, eur),
            state = ActivityState.PLANNED,
            budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
            expectedOn = LocalDate.of(2026, 9, 5),
        )
}
