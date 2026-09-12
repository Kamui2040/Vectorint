package io.github.kamui2040.vectorint

import android.app.Application
import io.github.kamui2040.vectorint.backup.AndroidAutoBackupDirectoryGateway
import io.github.kamui2040.vectorint.backup.AndroidAutoBackupWorkGateway
import io.github.kamui2040.vectorint.backup.AutoBackupRunResult
import io.github.kamui2040.vectorint.backup.AutoBackupRunner
import io.github.kamui2040.vectorint.backup.AutoBackupScheduler
import io.github.kamui2040.vectorint.backup.AutoBackupTrigger
import io.github.kamui2040.vectorint.backup.BackupCoordinator
import io.github.kamui2040.vectorint.core.ReminderPlanner
import io.github.kamui2040.vectorint.data.ReminderDeliveryCoordinator
import io.github.kamui2040.vectorint.data.ReminderItemsRepository
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.local.DataStoreAutoBackupSettingsRepository
import io.github.kamui2040.vectorint.data.local.DataStoreReminderDeliveryStateRepository
import io.github.kamui2040.vectorint.data.local.DataStoreSettingsRepository
import io.github.kamui2040.vectorint.data.local.RoomBudgetRepository
import io.github.kamui2040.vectorint.data.local.VectorintDatabase
import io.github.kamui2040.vectorint.data.local.vectorintAutoBackupDataStore
import io.github.kamui2040.vectorint.data.local.vectorintSettingsDataStore
import io.github.kamui2040.vectorint.reminder.AndroidNotificationPermissionGateway
import io.github.kamui2040.vectorint.reminder.AndroidReminderAlarmGateway
import io.github.kamui2040.vectorint.reminder.AndroidReminderNotificationGateway
import io.github.kamui2040.vectorint.widget.AvailableNowWidgetProvider
import io.github.kamui2040.vectorint.widget.QuickAddAvailableNowWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

class VectorintApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preferencesDataStore by lazy { vectorintSettingsDataStore }
    private val autoBackupDataStore by lazy { vectorintAutoBackupDataStore }

    internal val autoBackupSettingsRepository by lazy {
        DataStoreAutoBackupSettingsRepository(autoBackupDataStore)
    }

    internal val autoBackupScheduler by lazy {
        AutoBackupScheduler(
            settingsRepository = autoBackupSettingsRepository,
            workGateway = AndroidAutoBackupWorkGateway(this),
            scope = applicationScope,
        )
    }

    internal val budgetRepository: RoomBudgetRepository by lazy {
        RoomBudgetRepository(
            database = VectorintDatabase.create(this),
            onDataChanged = { autoBackupScheduler.request(AutoBackupTrigger.CHANGE) },
        )
    }

    internal val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(
            dataStore = preferencesDataStore,
            onSettingsChanged = { autoBackupScheduler.request(AutoBackupTrigger.CHANGE) },
        )
    }

    private val autoBackupRunner by lazy {
        AutoBackupRunner(
            settingsRepository = autoBackupSettingsRepository,
            backupCoordinator =
                BackupCoordinator(
                    dataRepository = budgetRepository,
                    settingsRepository = settingsRepository,
                ),
            directoryGateway = AndroidAutoBackupDirectoryGateway(contentResolver),
        )
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
        autoBackupScheduler.synchronize()
    }

    internal suspend fun runAutomaticBackup(): AutoBackupRunResult = autoBackupRunner.run()

    internal fun requestAutomaticBackup(trigger: AutoBackupTrigger) {
        autoBackupScheduler.requestAsync(trigger)
    }

    internal fun notificationsAllowed(): Boolean = notificationPermissionGateway.notificationsAllowed()

    internal fun refreshWidgets() {
        AvailableNowWidgetProvider.requestUpdate(this)
        QuickAddAvailableNowWidgetProvider.requestUpdate(this)
    }

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
