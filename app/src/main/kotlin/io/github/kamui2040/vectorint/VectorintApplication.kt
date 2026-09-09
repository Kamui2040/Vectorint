package io.github.kamui2040.vectorint

import android.app.Application
import io.github.kamui2040.vectorint.core.ReminderPlanner
import io.github.kamui2040.vectorint.data.ReminderDeliveryCoordinator
import io.github.kamui2040.vectorint.data.ReminderItemsRepository
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.local.DataStoreReminderDeliveryStateRepository
import io.github.kamui2040.vectorint.data.local.DataStoreSettingsRepository
import io.github.kamui2040.vectorint.data.local.RoomBudgetRepository
import io.github.kamui2040.vectorint.data.local.VectorintDatabase
import io.github.kamui2040.vectorint.data.local.vectorintSettingsDataStore
import io.github.kamui2040.vectorint.reminder.AndroidNotificationPermissionGateway
import io.github.kamui2040.vectorint.reminder.AndroidReminderAlarmGateway
import io.github.kamui2040.vectorint.reminder.AndroidReminderNotificationGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

class VectorintApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preferencesDataStore by lazy { vectorintSettingsDataStore }

    internal val budgetRepository: RoomBudgetRepository by lazy {
        RoomBudgetRepository(VectorintDatabase.create(this))
    }

    internal val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(preferencesDataStore)
    }

    internal val notificationPermissionGateway by lazy {
        AndroidNotificationPermissionGateway(this)
    }

    private val reminderNotificationGateway by lazy {
        AndroidReminderNotificationGateway(this, notificationPermissionGateway)
    }

    private val reminderDeliveryCoordinator by lazy {
        ReminderDeliveryCoordinator(
            itemsRepository = ReminderItemsRepository { budgetRepository.loadRecurringItems() },
            stateRepository = DataStoreReminderDeliveryStateRepository(preferencesDataStore),
            alarmGateway = AndroidReminderAlarmGateway(this),
            notificationGateway = reminderNotificationGateway,
            planner = ReminderPlanner(),
            clock = Clock.systemUTC(),
            zoneProvider = ZoneId::systemDefault,
        )
    }

    override fun onCreate() {
        super.onCreate()
        reminderNotificationGateway.createChannel()
    }

    internal fun notificationsAllowed(): Boolean = notificationPermissionGateway.notificationsAllowed()

    internal fun refreshReminders(onComplete: (() -> Unit)? = null) {
        applicationScope.launch {
            try {
                reminderDeliveryCoordinator.refresh()
            } finally {
                onComplete?.invoke()
            }
        }
    }
}
