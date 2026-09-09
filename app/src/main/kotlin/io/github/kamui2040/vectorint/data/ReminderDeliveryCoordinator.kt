package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.PlannedReminder
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.ReminderPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal fun interface ReminderItemsRepository {
    suspend fun loadRecurringItems(): List<RecurringItem>
}

internal interface ReminderAlarmGateway {
    fun schedule(at: Instant)

    fun cancel()
}

internal interface ReminderNotificationGateway {
    fun notificationsAllowed(): Boolean

    fun isActive(key: String): Boolean

    fun post(reminder: PlannedReminder): Boolean

    fun cancelNotificationsNotIn(relevantKeys: Set<String>)
}

internal enum class ReminderRefreshOutcome {
    NO_REMINDERS,
    SCHEDULED,
    DELIVERED,
    DELIVERY_PENDING,
    NOTIFICATIONS_UNAVAILABLE,
    FAILED,
}

internal class ReminderDeliveryCoordinator(
    private val itemsRepository: ReminderItemsRepository,
    private val stateRepository: ReminderDeliveryStateRepository,
    private val alarmGateway: ReminderAlarmGateway,
    private val notificationGateway: ReminderNotificationGateway,
    private val planner: ReminderPlanner = ReminderPlanner(),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    private val refreshMutex = Mutex()

    suspend fun refresh(): ReminderRefreshOutcome =
        refreshMutex.withLock {
            try {
                refreshLocked()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ReminderRefreshOutcome.FAILED
            }
        }

    private suspend fun refreshLocked(): ReminderRefreshOutcome {
        val items = itemsRepository.loadRecurringItems()
        val acknowledged = stateRepository.loadAcknowledgedKeys()
        val now = clock.instant()
        val zoneId = zoneProvider()
        val plan = planner.plan(items, acknowledged, now, zoneId)

        notificationGateway.cancelNotificationsNotIn(plan.relevantKeys)
        var currentAcknowledged = acknowledged.intersect(plan.relevantKeys)
        if (currentAcknowledged != acknowledged) {
            stateRepository.replaceAcknowledgedKeys(currentAcknowledged)
        }

        if (!notificationGateway.notificationsAllowed()) {
            alarmGateway.cancel()
            return ReminderRefreshOutcome.NOTIFICATIONS_UNAVAILABLE
        }

        var delivered = false
        plan.due.forEach { reminder ->
            val encodedKey = reminder.key.encoded
            val confirmedActive =
                notificationGateway.isActive(encodedKey) || notificationGateway.post(reminder)
            if (confirmedActive) {
                currentAcknowledged = currentAcknowledged + encodedKey
                delivered = true
            }
        }
        if (currentAcknowledged != acknowledged.intersect(plan.relevantKeys)) {
            stateRepository.replaceAcknowledgedKeys(currentAcknowledged)
        }

        val updatedPlan = planner.plan(items, currentAcknowledged, now, zoneId)
        updatedPlan.nextEvaluationAt?.let(alarmGateway::schedule) ?: alarmGateway.cancel()
        return when {
            delivered -> ReminderRefreshOutcome.DELIVERED
            updatedPlan.nextEvaluationAt != null -> ReminderRefreshOutcome.SCHEDULED
            updatedPlan.relevantKeys.isEmpty() -> ReminderRefreshOutcome.NO_REMINDERS
            else -> ReminderRefreshOutcome.DELIVERY_PENDING
        }
    }
}
