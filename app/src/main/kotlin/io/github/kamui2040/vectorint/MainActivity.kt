package io.github.kamui2040.vectorint

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.kamui2040.vectorint.backup.AndroidBackupDocumentGateway
import io.github.kamui2040.vectorint.backup.BackupCoordinator
import io.github.kamui2040.vectorint.backup.BackupDocumentService
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.data.RecurringOccurrenceCoordinator
import io.github.kamui2040.vectorint.data.UserSettings
import io.github.kamui2040.vectorint.presentation.about.AboutDialog
import io.github.kamui2040.vectorint.presentation.activity.ActivityEditRoute
import io.github.kamui2040.vectorint.presentation.activity.ActivityEditor
import io.github.kamui2040.vectorint.presentation.activity.ActivityHistoryLoader
import io.github.kamui2040.vectorint.presentation.activity.ActivityHistoryRoute
import io.github.kamui2040.vectorint.presentation.activity.RegionalActivityDisplayFormatter
import io.github.kamui2040.vectorint.presentation.category.CategoryManager
import io.github.kamui2040.vectorint.presentation.entry.CurrentFundsEditor
import io.github.kamui2040.vectorint.presentation.entry.CurrentFundsRoute
import io.github.kamui2040.vectorint.presentation.entry.OneOffActivityEditor
import io.github.kamui2040.vectorint.presentation.entry.OneOffActivityRoute
import io.github.kamui2040.vectorint.presentation.entry.RegionalEntryMoneyAdapter
import io.github.kamui2040.vectorint.presentation.home.HomeScreen
import io.github.kamui2040.vectorint.presentation.home.HomeStateLoader
import io.github.kamui2040.vectorint.presentation.home.HomeUiState
import io.github.kamui2040.vectorint.presentation.home.RegionalHomeValueFormatter
import io.github.kamui2040.vectorint.presentation.navigation.MainView
import io.github.kamui2040.vectorint.presentation.navigation.MainViewScaffold
import io.github.kamui2040.vectorint.presentation.overview.OverviewScreen
import io.github.kamui2040.vectorint.presentation.overview.OverviewStateLoader
import io.github.kamui2040.vectorint.presentation.overview.OverviewUiState
import io.github.kamui2040.vectorint.presentation.overview.RegionalOverviewValueFormatter
import io.github.kamui2040.vectorint.presentation.recurring.RecurringItemEditor
import io.github.kamui2040.vectorint.presentation.recurring.RecurringItemRoute
import io.github.kamui2040.vectorint.presentation.recurring.RecurringListLoader
import io.github.kamui2040.vectorint.presentation.recurring.RecurringListRoute
import io.github.kamui2040.vectorint.presentation.recurring.RegionalRecurringMoneyFormatter
import io.github.kamui2040.vectorint.presentation.recurring.RegionalRecurringScheduleInputAdapter
import io.github.kamui2040.vectorint.presentation.settings.SettingsEditor
import io.github.kamui2040.vectorint.presentation.settings.SettingsRoute
import io.github.kamui2040.vectorint.presentation.settings.applyAppLanguage
import io.github.kamui2040.vectorint.presentation.settings.currentAppLanguage
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import io.github.kamui2040.vectorint.reminder.REMINDER_INTENT_ACTION
import io.github.kamui2040.vectorint.reminder.REMINDER_ITEM_ID_EXTRA

class MainActivity : AppCompatActivity() {
    private var notificationsAvailable by mutableStateOf(false)
    private var pendingReminderItemId by mutableStateOf<String?>(null)
    private var resumeGeneration by mutableIntStateOf(0)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            val vectorintApplication = application as VectorintApplication
            notificationsAvailable = vectorintApplication.notificationsAllowed()
            vectorintApplication.refreshReminders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vectorintApplication = application as VectorintApplication
        val categoryManager = CategoryManager(vectorintApplication.budgetRepository)
        notificationsAvailable = vectorintApplication.notificationsAllowed()
        pendingReminderItemId = reminderItemIdFrom(intent)
        val recurringOccurrenceCoordinator =
            RecurringOccurrenceCoordinator(vectorintApplication.budgetRepository)
        val homeStateLoader =
            HomeStateLoader(
                budgetRepository = vectorintApplication.budgetRepository,
                settingsRepository = vectorintApplication.settingsRepository,
                formatter = RegionalHomeValueFormatter(),
                occurrenceUpdater = recurringOccurrenceCoordinator,
            )
        val overviewStateLoader =
            OverviewStateLoader(
                budgetRepository = vectorintApplication.budgetRepository,
                formatter = RegionalOverviewValueFormatter(),
                occurrenceUpdater = recurringOccurrenceCoordinator,
            )
        val entryMoneyAdapter = RegionalEntryMoneyAdapter()
        val currentFundsEditor =
            CurrentFundsEditor(
                budgetRepository = vectorintApplication.budgetRepository,
                moneyAdapter = entryMoneyAdapter,
            )
        val oneOffActivityEditor =
            OneOffActivityEditor(
                budgetRepository = vectorintApplication.budgetRepository,
                moneyAdapter = entryMoneyAdapter,
            )
        val activityDisplayFormatter = RegionalActivityDisplayFormatter()
        val activityHistoryLoader =
            ActivityHistoryLoader(
                budgetRepository = vectorintApplication.budgetRepository,
                formatter = activityDisplayFormatter,
                occurrenceUpdater = recurringOccurrenceCoordinator,
            )
        val activityEditor =
            ActivityEditor(
                budgetRepository = vectorintApplication.budgetRepository,
                moneyAdapter = entryMoneyAdapter,
                formatter = activityDisplayFormatter,
            )
        val recurringListLoader =
            RecurringListLoader(
                budgetRepository = vectorintApplication.budgetRepository,
                formatter = RegionalRecurringMoneyFormatter(),
            )
        val recurringScheduleAdapter = RegionalRecurringScheduleInputAdapter()
        val recurringItemEditor =
            RecurringItemEditor(
                budgetRepository = vectorintApplication.budgetRepository,
                moneyAdapter = entryMoneyAdapter,
                scheduleAdapter = recurringScheduleAdapter,
            )
        val settingsEditor = SettingsEditor(vectorintApplication.settingsRepository)
        val backupDocumentService =
            BackupDocumentService(
                coordinator =
                    BackupCoordinator(
                        dataRepository = vectorintApplication.budgetRepository,
                        settingsRepository = vectorintApplication.settingsRepository,
                    ),
                documents = AndroidBackupDocumentGateway(contentResolver),
            )
        setContent {
            val userSettings by
                vectorintApplication.settingsRepository.settings.collectAsState(
                    initial = UserSettings(),
                )
            var reloadKey by remember { mutableIntStateOf(0) }
            var selectedHomeMonthOffset by rememberSaveable { mutableIntStateOf(0) }
            var selectedOverviewMonthOffset by rememberSaveable { mutableIntStateOf(0) }
            var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
            var activityReturnDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
            var selectedActivityId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedRecurringItemId by rememberSaveable { mutableStateOf<String?>(null) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showAbout by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(pendingReminderItemId) {
                pendingReminderItemId?.let { reminderItemId ->
                    selectedRecurringItemId = reminderItemId
                    destination = AppDestination.EDIT_RECURRING_ITEM
                    pendingReminderItemId = null
                }
            }

            VectorintTheme(
                themeMode = userSettings.themeMode,
                colorPalette = userSettings.colorPalette,
            ) {
                val destinationContent: @Composable () -> Unit = {
                    when (destination) {
                        AppDestination.HOME -> {
                            val selectedMonth =
                                BudgetMonth(
                                    homeStateLoader.currentMonth().value.plusMonths(selectedHomeMonthOffset.toLong()),
                                )
                            val selectedMonthLabel = homeStateLoader.formatMonth(selectedMonth)
                            val homeState by
                                produceState<HomeUiState>(
                                    initialValue = HomeUiState.Loading(selectedMonthLabel),
                                    key1 = reloadKey,
                                    key2 = resumeGeneration,
                                    key3 = selectedHomeMonthOffset,
                                ) {
                                    value = HomeUiState.Loading(selectedMonthLabel)
                                    value = homeStateLoader.load(selectedMonth)
                                }
                            HomeScreen(
                                state = homeState,
                                onRetry = { reloadKey++ },
                                onSetCurrentFunds = { destination = AppDestination.CURRENT_FUNDS },
                                onEditCurrentFunds = { destination = AppDestination.CURRENT_FUNDS },
                                onAddActivity = {
                                    activityReturnDestination = AppDestination.HOME
                                    destination = AppDestination.ADD_ACTIVITY
                                },
                                onViewRecurringItems = { destination = AppDestination.RECURRING_ITEMS },
                                selectedMonthIsCurrent = selectedHomeMonthOffset == 0,
                                onPreviousMonth = { selectedHomeMonthOffset-- },
                                onNextMonth = { selectedHomeMonthOffset++ },
                                onCurrentMonth = { selectedHomeMonthOffset = 0 },
                            )
                        }

                        AppDestination.OVERVIEW -> {
                            val selectedMonth =
                                BudgetMonth(
                                    overviewStateLoader
                                        .currentMonth()
                                        .value
                                        .plusMonths(selectedOverviewMonthOffset.toLong()),
                                )
                            val selectedMonthLabel = overviewStateLoader.formatMonth(selectedMonth)
                            val overviewState by
                                produceState<OverviewUiState>(
                                    initialValue = OverviewUiState.Loading(selectedMonthLabel),
                                    key1 = reloadKey,
                                    key2 = resumeGeneration,
                                    key3 = selectedOverviewMonthOffset,
                                ) {
                                    value = OverviewUiState.Loading(selectedMonthLabel)
                                    value = overviewStateLoader.load(selectedMonth)
                                }
                            OverviewScreen(
                                state = overviewState,
                                onRetry = { reloadKey++ },
                                onSetCurrentFunds = { destination = AppDestination.CURRENT_FUNDS },
                                selectedMonthIsCurrent = selectedOverviewMonthOffset == 0,
                                onPreviousMonth = { selectedOverviewMonthOffset-- },
                                onNextMonth = { selectedOverviewMonthOffset++ },
                                onCurrentMonth = { selectedOverviewMonthOffset = 0 },
                            )
                        }

                        AppDestination.CURRENT_FUNDS ->
                            CurrentFundsRoute(
                                editor = currentFundsEditor,
                                onSaved = {
                                    vectorintApplication.refreshWidgets()
                                    reloadKey++
                                    destination = AppDestination.HOME
                                },
                                onBack = { destination = AppDestination.HOME },
                            )

                        AppDestination.ADD_ACTIVITY ->
                            OneOffActivityRoute(
                                editor = oneOffActivityEditor,
                                categoryManager = categoryManager,
                                onSaved = {
                                    vectorintApplication.refreshWidgets()
                                    reloadKey++
                                    destination = activityReturnDestination
                                },
                                onBack = { destination = activityReturnDestination },
                            )

                        AppDestination.ACTIVITY_HISTORY ->
                            ActivityHistoryRoute(
                                loader = activityHistoryLoader,
                                onActivitySelected = { activityId ->
                                    selectedActivityId = activityId.value
                                    destination = AppDestination.EDIT_ACTIVITY
                                },
                                onAddActivity = {
                                    activityReturnDestination = AppDestination.ACTIVITY_HISTORY
                                    destination = AppDestination.ADD_ACTIVITY
                                },
                                onBack = { destination = AppDestination.HOME },
                            )

                        AppDestination.EDIT_ACTIVITY ->
                            ActivityEditRoute(
                                editor = activityEditor,
                                categoryManager = categoryManager,
                                activityId = ActivityId(checkNotNull(selectedActivityId)),
                                onChanged = {
                                    vectorintApplication.refreshWidgets()
                                    reloadKey++
                                    destination = AppDestination.ACTIVITY_HISTORY
                                },
                                onBack = { destination = AppDestination.ACTIVITY_HISTORY },
                            )

                        AppDestination.RECURRING_ITEMS ->
                            RecurringListRoute(
                                loader = recurringListLoader,
                                onItemSelected = { recurringItemId ->
                                    selectedRecurringItemId = recurringItemId.value
                                    destination = AppDestination.EDIT_RECURRING_ITEM
                                },
                                onAdd = {
                                    selectedRecurringItemId = null
                                    destination = AppDestination.ADD_RECURRING_ITEM
                                },
                                onBack = { destination = AppDestination.HOME },
                            )

                        AppDestination.ADD_RECURRING_ITEM,
                        AppDestination.EDIT_RECURRING_ITEM,
                        ->
                            RecurringItemRoute(
                                editor = recurringItemEditor,
                                categoryManager = categoryManager,
                                scheduleAdapter = recurringScheduleAdapter,
                                recurringItemId = selectedRecurringItemId?.let(::RecurringItemId),
                                notificationsAvailable = notificationsAvailable,
                                onEnableNotifications = ::enableNotifications,
                                onOpenNotificationSettings = ::openNotificationSettings,
                                onChanged = {
                                    vectorintApplication.refreshReminders()
                                    vectorintApplication.refreshWidgets()
                                    reloadKey++
                                    destination = AppDestination.RECURRING_ITEMS
                                },
                                onBack = { destination = AppDestination.RECURRING_ITEMS },
                            )
                    }
                }
                MainViewScaffold(
                    selectedView = destination.mainView(activityReturnDestination),
                    onViewSelected = { selectedView ->
                        if (selectedView == MainView.HOME) selectedHomeMonthOffset = 0
                        destination = selectedView.toAppDestination()
                    },
                    onOpenAbout = { showAbout = true },
                    onOpenSettings = { showSettings = true },
                    content = destinationContent,
                )
                if (showSettings) {
                    SettingsRoute(
                        editor = settingsEditor,
                        backupDocumentService = backupDocumentService,
                        language = currentAppLanguage(),
                        onLanguageChange = { language ->
                            applyAppLanguage(language)
                            vectorintApplication.refreshWidgets()
                        },
                        onSaved = {
                            vectorintApplication.refreshWidgets()
                            reloadKey++
                        },
                        onRestored = {
                            vectorintApplication.refreshReminders()
                            vectorintApplication.refreshWidgets()
                            reloadKey++
                            selectedHomeMonthOffset = 0
                            selectedOverviewMonthOffset = 0
                            destination = AppDestination.HOME
                            showSettings = false
                        },
                        onOpenAbout = {
                            showSettings = false
                            showAbout = true
                        },
                        onBack = { showSettings = false },
                    )
                }
                if (showAbout) {
                    AboutDialog(onDismiss = { showAbout = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingReminderItemId = reminderItemIdFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeGeneration++
        val vectorintApplication = application as VectorintApplication
        notificationsAvailable = vectorintApplication.notificationsAllowed()
        vectorintApplication.refreshReminders()
        vectorintApplication.refreshWidgets()
    }

    private fun enableNotifications() {
        val vectorintApplication = application as VectorintApplication
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            vectorintApplication.notificationPermissionGateway.runtimePermissionRequired()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsAvailable = vectorintApplication.notificationsAllowed()
            vectorintApplication.refreshReminders()
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            },
        )
    }
}

internal fun reminderItemIdFrom(intent: Intent?): String? =
    intent
        ?.takeIf { it.action == REMINDER_INTENT_ACTION }
        ?.getStringExtra(REMINDER_ITEM_ID_EXTRA)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

private enum class AppDestination {
    HOME,
    OVERVIEW,
    CURRENT_FUNDS,
    ADD_ACTIVITY,
    ACTIVITY_HISTORY,
    EDIT_ACTIVITY,
    RECURRING_ITEMS,
    ADD_RECURRING_ITEM,
    EDIT_RECURRING_ITEM,
}

private fun AppDestination.mainView(activityReturnDestination: AppDestination): MainView =
    when (this) {
        AppDestination.HOME,
        AppDestination.CURRENT_FUNDS,
        AppDestination.RECURRING_ITEMS,
        AppDestination.ADD_RECURRING_ITEM,
        AppDestination.EDIT_RECURRING_ITEM,
        -> MainView.HOME

        AppDestination.ACTIVITY_HISTORY,
        AppDestination.EDIT_ACTIVITY,
        -> MainView.HISTORY

        AppDestination.OVERVIEW -> MainView.OVERVIEW

        AppDestination.ADD_ACTIVITY ->
            if (activityReturnDestination == AppDestination.ACTIVITY_HISTORY) MainView.HISTORY else MainView.HOME
    }

private fun MainView.toAppDestination(): AppDestination =
    when (this) {
        MainView.HOME -> AppDestination.HOME
        MainView.HISTORY -> AppDestination.ACTIVITY_HISTORY
        MainView.OVERVIEW -> AppDestination.OVERVIEW
    }
