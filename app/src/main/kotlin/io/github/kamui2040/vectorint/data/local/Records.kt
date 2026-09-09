package io.github.kamui2040.vectorint.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private const val INCOME = "income"
private const val EXPENSE = "expense"
private const val PLANNED = "planned"
private const val CONFIRMED = "confirmed"
private const val ONE_OFF = "one_off"
private const val RECURRING = "recurring"
private const val EXACT_RECURRENCE = "exact_recurrence"
private const val LEGACY_EXACT_DAY = "exact_day"
private const val LEGACY_DAY_RANGE = "day_range"
private const val LEGACY_ANY_TIME_IN_MONTH = "any_time_in_month"
private const val OCCURRENCE_MONTH = "occurrence_month"
private const val FOLLOWING_MONTH = "following_month"
private const val DAYS = "days"
private const val WEEKS = "weeks"
private const val MONTHS = "months"
private const val YEARS = "years"
private const val SPECIFIC_DATE = "specific_date"
private const val DATE_RANGE = "date_range"
private const val ANY_TIME_IN_MONTH = "any_time_in_month"

internal data class ActivityRecord(
    @Embedded val activity: ActivityEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy =
            Junction(
                value = ActivityTagCrossRef::class,
                parentColumn = "activity_id",
                entityColumn = "tag_name",
            ),
    )
    val tags: List<TagEntity>,
)

internal data class RecurringItemRecord(
    @Embedded val item: RecurringItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy =
            Junction(
                value = RecurringItemTagCrossRef::class,
                parentColumn = "recurring_item_id",
                entityColumn = "tag_name",
            ),
    )
    val tags: List<TagEntity>,
)

internal fun CurrentFunds.toEntity(): CurrentFundsEntity =
    CurrentFundsEntity(
        minorUnits = amount.minorUnits,
        currencyCode = amount.currency.value,
        capturedAtEpochSecond = capturedAt.epochSecond,
        capturedAtNano = capturedAt.nano,
    )

internal fun CurrentFundsEntity.toDomain(): CurrentFunds {
    require(singletonId == CURRENT_FUNDS_SINGLETON_ID) { "Unknown Current funds row" }
    require(capturedAtNano in 0..999_999_999) { "Stored nanoseconds are out of range" }
    return CurrentFunds(
        amount = Money(minorUnits, CurrencyCode.of(currencyCode)),
        capturedAt = Instant.ofEpochSecond(capturedAtEpochSecond, capturedAtNano.toLong()),
    )
}

internal fun ActivityEntry.toRecord(): ActivityRecord {
    val recurringSource = source as? ActivitySource.Recurring
    return ActivityRecord(
        activity =
            ActivityEntity(
                id = id.value,
                name = name,
                direction = direction.toStoredValue(),
                minorUnits = amount.minorUnits,
                currencyCode = amount.currency.value,
                state = state.toStoredValue(),
                budgetMonth = budgetMonth.value.toString(),
                expectedOnEpochDay = expectedOn?.toEpochDay(),
                bookedAtEpochSecond = bookedAt?.epochSecond,
                bookedAtNano = bookedAt?.nano,
                sourceKind = if (recurringSource == null) ONE_OFF else RECURRING,
                recurringItemId = recurringSource?.itemId?.value,
                recurringOccurrenceKey = recurringSource?.occurrenceKey,
                categoryId = categoryId?.value,
            ),
        tags = tags.map { TagEntity(it.value) }.sortedBy(TagEntity::name),
    )
}

internal fun ActivityRecord.toDomain(): ActivityEntry {
    val bookedAt = storedInstant(activity.bookedAtEpochSecond, activity.bookedAtNano)
    val source =
        when (activity.sourceKind) {
            ONE_OFF -> {
                require(activity.recurringItemId == null && activity.recurringOccurrenceKey == null) {
                    "One-off activity cannot carry recurring identity"
                }
                ActivitySource.OneOff
            }

            RECURRING ->
                ActivitySource.Recurring(
                    itemId = RecurringItemId(requireNotNull(activity.recurringItemId)),
                    occurrenceKey = requireNotNull(activity.recurringOccurrenceKey),
                )

            else -> error("Unknown stored activity source: ${activity.sourceKind}")
        }

    return ActivityEntry(
        id = ActivityId(activity.id),
        name = activity.name,
        direction = activity.direction.toDirection(),
        amount = Money(activity.minorUnits, CurrencyCode.of(activity.currencyCode)),
        state = activity.state.toActivityState(),
        budgetMonth = BudgetMonth(YearMonth.parse(activity.budgetMonth)),
        expectedOn = activity.expectedOnEpochDay?.let(LocalDate::ofEpochDay),
        bookedAt = bookedAt,
        source = source,
        categoryId = activity.categoryId?.let(::CategoryId),
        tags = tags.map { Tag(it.name) }.toSet(),
    )
}

internal fun RecurringItem.toRecord(): RecurringItemRecord =
    RecurringItemRecord(
        item =
            RecurringItemEntity(
                id = id.value,
                name = name,
                direction = direction.toStoredValue(),
                minorUnits = amount.minorUnits,
                currencyCode = amount.currency.value,
                legacyTimingKind = EXACT_RECURRENCE,
                legacyTimingFirstDay = null,
                legacyTimingLastDay = null,
                legacyStartsOnEpochDay = schedule.firstOccurrence.toEpochDay(),
                firstOccurrenceEpochDay = schedule.firstOccurrence.toEpochDay(),
                repeatEvery = schedule.interval.every,
                repeatUnit = schedule.interval.unit.toStoredValue(),
                countsToward = schedule.countsToward.toStoredValue(),
                scheduleTimingKind = schedule.timing.toStoredValue(),
                periodEndOffsetDays = (schedule.timing as? OccurrenceTiming.DateRange)?.endOffsetDays,
                requireManualConfirmation = schedule.requireManualConfirmation,
                endsOnEpochDay = schedule.endsOn?.toEpochDay(),
                remindOnEpochDay = schedule.remindOn?.toEpochDay(),
                occurrenceReminderDays = reminders.occurrence?.daysBefore,
                remindReminderDays = reminders.remind?.daysBefore,
                endReminderDays = reminders.end?.daysBefore,
                categoryId = categoryId?.value,
            ),
        tags = tags.map { TagEntity(it.value) }.sortedBy(TagEntity::name),
    )

internal fun RecurringItemRecord.toDomain(): RecurringItem {
    item.validateLegacyTimingColumns()
    return RecurringItem(
        id = RecurringItemId(item.id),
        name = item.name,
        direction = item.direction.toDirection(),
        amount = Money(item.minorUnits, CurrencyCode.of(item.currencyCode)),
        schedule =
            RecurringSchedule(
                firstOccurrence = LocalDate.ofEpochDay(item.firstOccurrenceEpochDay),
                timing = item.toOccurrenceTiming(),
                interval =
                    RecurrenceInterval(
                        every = item.repeatEvery,
                        unit = item.repeatUnit.toRecurrenceUnit(),
                    ),
                countsToward = item.countsToward.toBudgetMonthAssignment(),
                requireManualConfirmation = item.requireManualConfirmation,
                endsOn = item.endsOnEpochDay?.let(LocalDate::ofEpochDay),
                remindOn = item.remindOnEpochDay?.let(LocalDate::ofEpochDay),
            ),
        reminders =
            ReminderSettings(
                occurrence = item.occurrenceReminderDays?.let(::ReminderLead),
                remind = item.remindReminderDays?.let(::ReminderLead),
                end = item.endReminderDays?.let(::ReminderLead),
            ),
        categoryId = item.categoryId?.let(::CategoryId),
        tags = tags.map { Tag(it.name) }.toSet(),
    )
}

internal fun CustomCategory.toEntity(): CustomCategoryEntity =
    CustomCategoryEntity(
        id = id.value,
        name = name,
        icon = icon.name.lowercase(),
    )

internal fun CustomCategoryEntity.toDomain(): CustomCategory =
    CustomCategory(
        id = CategoryId(id),
        name = name,
        icon =
            CategoryIcon.entries.singleOrNull { it.name.equals(icon, ignoreCase = true) }
                ?: error("Unknown stored category icon: $icon"),
    )

private fun OccurrenceTiming.toStoredValue(): String =
    when (this) {
        OccurrenceTiming.SpecificDate -> SPECIFIC_DATE
        is OccurrenceTiming.DateRange -> DATE_RANGE
        OccurrenceTiming.AnyTimeInMonth -> ANY_TIME_IN_MONTH
    }

private fun RecurringItemEntity.toOccurrenceTiming(): OccurrenceTiming =
    when (scheduleTimingKind) {
        SPECIFIC_DATE -> {
            require(periodEndOffsetDays == null) { "A specific date cannot carry a period end" }
            OccurrenceTiming.SpecificDate
        }

        DATE_RANGE -> OccurrenceTiming.DateRange(requireNotNull(periodEndOffsetDays))
        ANY_TIME_IN_MONTH -> {
            require(periodEndOffsetDays == null) { "An unspecified month cannot carry a period end" }
            OccurrenceTiming.AnyTimeInMonth
        }

        else -> error("Unknown stored recurring timing: $scheduleTimingKind")
    }

private fun RecurringItemEntity.validateLegacyTimingColumns() {
    when (legacyTimingKind) {
        EXACT_RECURRENCE -> {
            require(legacyTimingFirstDay == null && legacyTimingLastDay == null) {
                "Exact recurrence cannot carry legacy day values"
            }
            require(legacyStartsOnEpochDay == firstOccurrenceEpochDay) {
                "Exact recurrence anchors must match"
            }
        }

        LEGACY_EXACT_DAY ->
            require(legacyTimingFirstDay != null && legacyTimingLastDay == null) {
                "Legacy exact-day timing is malformed"
            }

        LEGACY_DAY_RANGE ->
            require(legacyTimingFirstDay != null && legacyTimingLastDay != null) {
                "Legacy day-range timing is malformed"
            }

        LEGACY_ANY_TIME_IN_MONTH ->
            require(legacyTimingFirstDay == null && legacyTimingLastDay == null) {
                "Legacy whole-month timing is malformed"
            }

        else -> error("Unknown stored recurring timing compatibility value: $legacyTimingKind")
    }
}

private fun RecurrenceUnit.toStoredValue(): String =
    when (this) {
        RecurrenceUnit.DAYS -> DAYS
        RecurrenceUnit.WEEKS -> WEEKS
        RecurrenceUnit.MONTHS -> MONTHS
        RecurrenceUnit.YEARS -> YEARS
    }

private fun String.toRecurrenceUnit(): RecurrenceUnit =
    when (this) {
        DAYS -> RecurrenceUnit.DAYS
        WEEKS -> RecurrenceUnit.WEEKS
        MONTHS -> RecurrenceUnit.MONTHS
        YEARS -> RecurrenceUnit.YEARS
        else -> error("Unknown stored recurrence unit: $this")
    }

private fun BudgetMonthAssignment.toStoredValue(): String =
    when (this) {
        BudgetMonthAssignment.OCCURRENCE_MONTH -> OCCURRENCE_MONTH
        BudgetMonthAssignment.FOLLOWING_MONTH -> FOLLOWING_MONTH
    }

private fun String.toBudgetMonthAssignment(): BudgetMonthAssignment =
    when (this) {
        OCCURRENCE_MONTH -> BudgetMonthAssignment.OCCURRENCE_MONTH
        FOLLOWING_MONTH -> BudgetMonthAssignment.FOLLOWING_MONTH
        else -> error("Unknown stored budget-month assignment: $this")
    }

private fun storedInstant(
    epochSecond: Long?,
    nano: Int?,
): Instant? {
    require((epochSecond == null) == (nano == null)) { "Stored instant must contain both fields" }
    require(nano == null || nano in 0..999_999_999) { "Stored nanoseconds are out of range" }
    return if (epochSecond == null) null else Instant.ofEpochSecond(epochSecond, nano!!.toLong())
}

private fun Direction.toStoredValue(): String =
    when (this) {
        Direction.INCOME -> INCOME
        Direction.EXPENSE -> EXPENSE
    }

private fun String.toDirection(): Direction =
    when (this) {
        INCOME -> Direction.INCOME
        EXPENSE -> Direction.EXPENSE
        else -> error("Unknown stored direction: $this")
    }

private fun ActivityState.toStoredValue(): String =
    when (this) {
        ActivityState.PLANNED -> PLANNED
        ActivityState.CONFIRMED -> CONFIRMED
    }

private fun String.toActivityState(): ActivityState =
    when (this) {
        PLANNED -> ActivityState.PLANNED
        CONFIRMED -> ActivityState.CONFIRMED
        else -> error("Unknown stored activity state: $this")
    }
