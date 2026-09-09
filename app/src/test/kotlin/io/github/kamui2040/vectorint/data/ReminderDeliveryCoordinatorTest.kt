package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.PlannedReminder
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderPlanner
import io.github.kamui2040.vectorint.core.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReminderDeliveryCoordinatorTest {
    private val now = Instant.parse("2026-09-06T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val planner = ReminderPlanner()

    @Test
    fun `confirmed active notification is acknowledged and not posted twice`() =
        runBlocking {
            val item = reminderItem()
            val state = FakeReminderStateRepository()
            val alarm = FakeReminderAlarmGateway()
            val notifications = FakeReminderNotificationGateway()
            val coordinator = coordinator(listOf(item), state, alarm, notifications)

            assertEquals(ReminderRefreshOutcome.DELIVERED, coordinator.refresh())
            assertEquals(1, notifications.posted.size)
            assertEquals(
                setOf(
                    notifications.posted
                        .single()
                        .key.encoded,
                ),
                state.keys,
            )
            assertEquals(Instant.parse("2026-09-07T00:05:00Z"), alarm.scheduledAt)

            assertEquals(ReminderRefreshOutcome.SCHEDULED, coordinator.refresh())
            assertEquals(1, notifications.posted.size)
        }

    @Test
    fun `permission denial preserves eligibility and cancels wakeups`() =
        runBlocking {
            val state = FakeReminderStateRepository()
            val alarm = FakeReminderAlarmGateway(scheduledAt = Instant.parse("2026-09-07T09:00:00Z"))
            val notifications = FakeReminderNotificationGateway(allowed = false)
            val coordinator = coordinator(listOf(reminderItem()), state, alarm, notifications)

            assertEquals(ReminderRefreshOutcome.NOTIFICATIONS_UNAVAILABLE, coordinator.refresh())

            assertTrue(state.keys.isEmpty())
            assertTrue(notifications.posted.isEmpty())
            assertEquals(null, alarm.scheduledAt)
            assertEquals(1, alarm.cancelCount)
        }

    @Test
    fun `active notification repairs a missing acknowledgement without reposting`() =
        runBlocking {
            val item = reminderItem()
            val due = planner.plan(listOf(item), emptySet(), now, ZoneOffset.UTC).due.single()
            val state = FakeReminderStateRepository()
            val notifications = FakeReminderNotificationGateway(activeKeys = mutableSetOf(due.key.encoded))
            val coordinator = coordinator(listOf(item), state, FakeReminderAlarmGateway(), notifications)

            assertEquals(ReminderRefreshOutcome.DELIVERED, coordinator.refresh())

            assertTrue(notifications.posted.isEmpty())
            assertEquals(setOf(due.key.encoded), state.keys)
        }

    @Test
    fun `failed post is not acknowledged`() =
        runBlocking {
            val state = FakeReminderStateRepository()
            val alarm = FakeReminderAlarmGateway()
            val notifications = FakeReminderNotificationGateway(postSucceeds = false)
            val coordinator = coordinator(listOf(reminderItem()), state, alarm, notifications)

            assertEquals(ReminderRefreshOutcome.DELIVERY_PENDING, coordinator.refresh())

            assertTrue(state.keys.isEmpty())
            assertEquals(1, notifications.posted.size)
            assertEquals(null, alarm.scheduledAt)
        }

    @Test
    fun `changed configuration cancels and prunes stale delivery state`() =
        runBlocking {
            val oldItem = reminderItem(lead = 1)
            val newItem = reminderItem(lead = 2)
            val oldKey =
                planner
                    .plan(listOf(oldItem), emptySet(), now, ZoneOffset.UTC)
                    .due
                    .single()
                    .key.encoded
            val state = FakeReminderStateRepository(keys = setOf(oldKey))
            val notifications = FakeReminderNotificationGateway(activeKeys = mutableSetOf(oldKey))
            val coordinator = coordinator(listOf(newItem), state, FakeReminderAlarmGateway(), notifications)

            assertEquals(ReminderRefreshOutcome.DELIVERED, coordinator.refresh())

            assertEquals(setOf(oldKey), notifications.cancelledKeys)
            assertTrue(oldKey !in state.keys)
            assertEquals(1, notifications.posted.size)
            assertEquals(
                setOf(
                    notifications.posted
                        .single()
                        .key.encoded,
                ),
                state.keys,
            )
        }

    @Test
    fun `removed reminder clears stale state and notification`() =
        runBlocking {
            val oldKey = "v1|synthetic-old-key"
            val state = FakeReminderStateRepository(keys = setOf(oldKey))
            val notifications = FakeReminderNotificationGateway(activeKeys = mutableSetOf(oldKey))
            val alarm = FakeReminderAlarmGateway(scheduledAt = Instant.parse("2026-09-07T09:00:00Z"))
            val coordinator = coordinator(emptyList(), state, alarm, notifications)

            assertEquals(ReminderRefreshOutcome.NO_REMINDERS, coordinator.refresh())

            assertTrue(state.keys.isEmpty())
            assertEquals(setOf(oldKey), notifications.cancelledKeys)
            assertEquals(null, alarm.scheduledAt)
        }

    @Test
    fun `repository failure does not replace an existing alarm`() =
        runBlocking {
            val expectedAlarm = Instant.parse("2026-09-07T09:00:00Z")
            val alarm = FakeReminderAlarmGateway(scheduledAt = expectedAlarm)
            val coordinator =
                ReminderDeliveryCoordinator(
                    itemsRepository = ReminderItemsRepository { throw IOException("synthetic read failure") },
                    stateRepository = FakeReminderStateRepository(),
                    alarmGateway = alarm,
                    notificationGateway = FakeReminderNotificationGateway(),
                    clock = clock,
                    zoneProvider = { ZoneOffset.UTC },
                )

            assertEquals(ReminderRefreshOutcome.FAILED, coordinator.refresh())

            assertEquals(expectedAlarm, alarm.scheduledAt)
            assertEquals(0, alarm.cancelCount)
        }

    private fun coordinator(
        items: List<RecurringItem>,
        state: FakeReminderStateRepository,
        alarm: FakeReminderAlarmGateway,
        notifications: FakeReminderNotificationGateway,
    ) = ReminderDeliveryCoordinator(
        itemsRepository = ReminderItemsRepository { items },
        stateRepository = state,
        alarmGateway = alarm,
        notificationGateway = notifications,
        clock = clock,
        zoneProvider = { ZoneOffset.UTC },
    )

    private fun reminderItem(lead: Int = 0): RecurringItem =
        RecurringItem(
            id = RecurringItemId("synthetic-item"),
            name = "Synthetic item",
            direction = Direction.EXPENSE,
            amount = Money(1_000, CurrencyCode.of("EUR")),
            schedule = RecurringSchedule(LocalDate.of(2026, 9, 6)),
            reminders = ReminderSettings(occurrence = ReminderLead(lead)),
        )
}

private class FakeReminderStateRepository(
    keys: Set<String> = emptySet(),
) : ReminderDeliveryStateRepository {
    var keys: Set<String> = keys

    override suspend fun loadAcknowledgedKeys(): Set<String> = keys

    override suspend fun replaceAcknowledgedKeys(keys: Set<String>) {
        this.keys = keys
    }
}

private class FakeReminderAlarmGateway(
    var scheduledAt: Instant? = null,
) : ReminderAlarmGateway {
    var cancelCount = 0

    override fun schedule(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
        cancelCount++
    }
}

private class FakeReminderNotificationGateway(
    private val allowed: Boolean = true,
    private val postSucceeds: Boolean = true,
    private val activeKeys: MutableSet<String> = mutableSetOf(),
) : ReminderNotificationGateway {
    val posted = mutableListOf<PlannedReminder>()
    val cancelledKeys = mutableSetOf<String>()

    override fun notificationsAllowed(): Boolean = allowed

    override fun isActive(key: String): Boolean = key in activeKeys

    override fun post(reminder: PlannedReminder): Boolean {
        posted += reminder
        if (postSucceeds) activeKeys += reminder.key.encoded
        return postSucceeds
    }

    override fun cancelNotificationsNotIn(relevantKeys: Set<String>) {
        val stale = activeKeys - relevantKeys
        cancelledKeys += stale
        activeKeys.removeAll(stale)
    }
}
