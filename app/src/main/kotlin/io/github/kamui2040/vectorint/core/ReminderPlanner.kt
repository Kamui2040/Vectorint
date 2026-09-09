package io.github.kamui2040.vectorint.core

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind {
    OCCURRENCE,
    REMIND,
    END,
}

data class ReminderDeliveryKey(
    val itemId: RecurringItemId,
    val kind: ReminderKind,
    val anchorDate: LocalDate,
    val daysBefore: Int,
) {
    init {
        require(daysBefore in 0..ReminderLead.MAX_DAYS_BEFORE) {
            "Reminder lead must be between 0 and ${ReminderLead.MAX_DAYS_BEFORE} days"
        }
    }

    val encoded: String
        get() = "v1|${kind.name}|${itemId.value.length}:${itemId.value}|${anchorDate.toEpochDay()}|$daysBefore"
}

data class PlannedReminder(
    val key: ReminderDeliveryKey,
    val itemName: String,
    val direction: Direction,
    val notifyAt: Instant,
    val relevantUntil: LocalDate,
)

data class ReminderPlan(
    val due: List<PlannedReminder>,
    val nextEvaluationAt: Instant?,
    val relevantKeys: Set<String>,
)

class ReminderPlanner(
    private val reminderTime: LocalTime = LocalTime.of(9, 0),
    private val retirementTime: LocalTime = LocalTime.of(0, 5),
) {
    fun plan(
        items: List<RecurringItem>,
        acknowledgedKeys: Set<String>,
        now: Instant,
        zoneId: ZoneId,
    ): ReminderPlan {
        val today = now.atZone(zoneId).toLocalDate()
        val candidates =
            items
                .flatMap { item -> item.candidates(today, zoneId) }
                .sortedWith(
                    compareBy<PlannedReminder>(
                        { it.notifyAt },
                        { it.key.anchorDate },
                        { it.key.kind.ordinal },
                        { it.key.itemId.value },
                    ),
                )
        val relevantKeys = candidates.mapTo(linkedSetOf()) { it.key.encoded }
        val due =
            candidates.filter { candidate ->
                candidate.key.encoded !in acknowledgedKeys && !candidate.notifyAt.isAfter(now)
            }
        val nextNotification =
            candidates
                .asSequence()
                .filter { it.key.encoded !in acknowledgedKeys && it.notifyAt.isAfter(now) }
                .map { it.notifyAt }
                .minOrNull()
        val nextRetirement =
            candidates
                .asSequence()
                .filter { it.key.encoded in acknowledgedKeys }
                .mapNotNull { it.relevantUntil.dayAfterAt(retirementTime, zoneId) }
                .filter { it.isAfter(now) }
                .minOrNull()

        return ReminderPlan(
            due = due,
            nextEvaluationAt = listOfNotNull(nextNotification, nextRetirement).minOrNull(),
            relevantKeys = relevantKeys,
        )
    }

    private fun RecurringItem.candidates(
        today: LocalDate,
        zoneId: ZoneId,
    ): List<PlannedReminder> =
        buildList {
            reminders.occurrence?.let { lead ->
                schedule.nextOccurrenceOnOrAfter(today)?.let { occurrence ->
                    toCandidate(
                        anchorDate = occurrence,
                        relevantUntil = occurrence,
                        kind = ReminderKind.OCCURRENCE,
                        lead = lead,
                        zoneId = zoneId,
                    )?.let(::add)
                }
            }
            reminders.remind?.let { lead ->
                schedule.remindOn
                    ?.takeUnless { it.isBefore(today) }
                    ?.let { anchor -> toCandidate(anchor, anchor, ReminderKind.REMIND, lead, zoneId) }
                    ?.let(::add)
            }
            reminders.end?.let { lead ->
                schedule.endsOn
                    ?.takeUnless { it.isBefore(today) }
                    ?.let { anchor -> toCandidate(anchor, anchor, ReminderKind.END, lead, zoneId) }
                    ?.let(::add)
            }
        }

    private fun RecurringItem.toCandidate(
        anchorDate: LocalDate,
        relevantUntil: LocalDate,
        kind: ReminderKind,
        lead: ReminderLead,
        zoneId: ZoneId,
    ): PlannedReminder? =
        try {
            val notificationDate = anchorDate.minusDays(lead.daysBefore.toLong())
            PlannedReminder(
                key =
                    ReminderDeliveryKey(
                        itemId = id,
                        kind = kind,
                        anchorDate = anchorDate,
                        daysBefore = lead.daysBefore,
                    ),
                itemName = name,
                direction = direction,
                notifyAt = notificationDate.atTime(reminderTime).atZone(zoneId).toInstant(),
                relevantUntil = relevantUntil,
            )
        } catch (_: DateTimeException) {
            null
        }

    private fun LocalDate.dayAfterAt(
        time: LocalTime,
        zoneId: ZoneId,
    ): Instant? =
        try {
            plusDays(1).atTime(time).atZone(zoneId).toInstant()
        } catch (_: DateTimeException) {
            null
        }
}
