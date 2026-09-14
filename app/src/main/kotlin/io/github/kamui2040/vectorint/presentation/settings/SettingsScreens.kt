package io.github.kamui2040.vectorint.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.backup.AutoBackupConfiguration
import io.github.kamui2040.vectorint.backup.AutoBackupInterval
import io.github.kamui2040.vectorint.backup.AutoBackupLastResult
import io.github.kamui2040.vectorint.backup.BackupDocumentService
import io.github.kamui2040.vectorint.backup.BackupExportResult
import io.github.kamui2040.vectorint.backup.BackupRestoreResult
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import kotlinx.coroutines.launch

internal enum class SettingsPage {
    ROOT,
    APPEARANCE,
    LANGUAGE,
    CALCULATION,
    DATA,
}

@Composable
internal fun SettingsRoute(
    editor: SettingsEditor,
    autoBackupEditor: AutoBackupSettingsEditor,
    backupDocumentService: BackupDocumentService,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onSaved: () -> Unit,
    onRestored: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val result by
        produceState<SettingsLoadResult?>(initialValue = null, key1 = loadKey) {
            value = editor.load()
        }

    when (val current = result) {
        null -> SettingsMessageScreen(onBack = onBack)
        SettingsLoadResult.Failed ->
            SettingsMessageScreen(
                title = stringResource(R.string.settings_load_failed),
                body = stringResource(R.string.settings_load_failed_body),
                onRetry = { loadKey++ },
                onBack = onBack,
            )

        is SettingsLoadResult.Ready ->
            key(current.settings) {
                SettingsReadyRoute(
                    editor = editor,
                    autoBackupEditor = autoBackupEditor,
                    backupDocumentService = backupDocumentService,
                    settings = current.settings,
                    language = language,
                    onLanguageChange = onLanguageChange,
                    onSaved = onSaved,
                    onRestored = onRestored,
                    onOpenAbout = onOpenAbout,
                    onBack = onBack,
                )
            }
    }
}

@Composable
private fun SettingsReadyRoute(
    editor: SettingsEditor,
    autoBackupEditor: AutoBackupSettingsEditor,
    backupDocumentService: BackupDocumentService,
    settings: UserSettings,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onSaved: () -> Unit,
    onRestored: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    var includeExpectedIncome by rememberSaveable { mutableStateOf(settings.includeExpectedIncome) }
    var themeMode by rememberSaveable { mutableStateOf(settings.themeMode) }
    var colorPalette by rememberSaveable { mutableStateOf(settings.colorPalette) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var backupState by rememberSaveable { mutableStateOf(BackupUiState.IDLE) }
    var autoBackupLoadKey by remember { mutableIntStateOf(0) }
    val autoBackupLoadResult by
        produceState<AutoBackupSettingsLoadResult?>(initialValue = null, key1 = autoBackupLoadKey) {
            value = autoBackupEditor.load()
        }
    var autoBackupUiState by remember { mutableStateOf(AutoBackupUiState.IDLE) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backupBusy = backupState == BackupUiState.WORKING
    val autoBackupState =
        (autoBackupLoadResult as? AutoBackupSettingsLoadResult.Ready)?.state
    val autoBackupConfiguration = autoBackupState?.configuration ?: AutoBackupConfiguration()
    val persistableUriFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                backupState = BackupUiState.WORKING
                scope.launch {
                    backupState =
                        when (backupDocumentService.export(uri)) {
                            BackupExportResult.Exported -> BackupUiState.EXPORTED
                            BackupExportResult.Failed -> BackupUiState.EXPORT_FAILED
                        }
                }
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                backupState = BackupUiState.WORKING
                scope.launch {
                    when (backupDocumentService.restore(uri)) {
                        BackupRestoreResult.Restored -> {
                            backupState = BackupUiState.RESTORED
                            onRestored()
                        }

                        BackupRestoreResult.InvalidBackup -> backupState = BackupUiState.INVALID_BACKUP
                        BackupRestoreResult.StorageFailed -> backupState = BackupUiState.RESTORE_FAILED
                        BackupRestoreResult.RecoveryFailed -> backupState = BackupUiState.RECOVERY_FAILED
                    }
                }
            }
        }
    val autoBackupFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val previousDestination = autoBackupConfiguration.destinationTreeUri
                autoBackupUiState = AutoBackupUiState.WORKING
                try {
                    context.contentResolver.takePersistableUriPermission(uri, persistableUriFlags)
                    scope.launch {
                        when (
                            autoBackupEditor.selectDestination(
                                current = autoBackupConfiguration,
                                treeUri = uri.toString(),
                            )
                        ) {
                            AutoBackupSettingsSaveResult.Saved -> {
                                if (previousDestination != null && previousDestination != uri.toString()) {
                                    releasePersistedFolderPermission(
                                        context.contentResolver,
                                        previousDestination,
                                        persistableUriFlags,
                                    )
                                }
                                autoBackupUiState = AutoBackupUiState.FOLDER_SELECTED
                                autoBackupLoadKey++
                            }

                            AutoBackupSettingsSaveResult.SavedSchedulingFailed -> {
                                if (previousDestination != null && previousDestination != uri.toString()) {
                                    releasePersistedFolderPermission(
                                        context.contentResolver,
                                        previousDestination,
                                        persistableUriFlags,
                                    )
                                }
                                autoBackupUiState = AutoBackupUiState.SCHEDULE_FAILED
                                autoBackupLoadKey++
                            }

                            AutoBackupSettingsSaveResult.Failed -> {
                                if (previousDestination != uri.toString()) {
                                    releasePersistedFolderPermission(
                                        context.contentResolver,
                                        uri.toString(),
                                        persistableUriFlags,
                                    )
                                }
                                autoBackupUiState = AutoBackupUiState.FAILED
                            }
                        }
                    }
                } catch (_: Exception) {
                    autoBackupUiState = AutoBackupUiState.FAILED
                }
            }
        }

    fun saveAutoBackupConfiguration(configuration: AutoBackupConfiguration) {
        autoBackupUiState = AutoBackupUiState.WORKING
        scope.launch {
            autoBackupUiState =
                when (autoBackupEditor.save(configuration)) {
                    AutoBackupSettingsSaveResult.Saved -> {
                        autoBackupLoadKey++
                        AutoBackupUiState.SAVED
                    }

                    AutoBackupSettingsSaveResult.SavedSchedulingFailed -> {
                        autoBackupLoadKey++
                        AutoBackupUiState.SCHEDULE_FAILED
                    }

                    AutoBackupSettingsSaveResult.Failed -> AutoBackupUiState.FAILED
                }
        }
    }

    SettingsScreen(
        includeExpectedIncome = includeExpectedIncome,
        themeMode = themeMode,
        colorPalette = colorPalette,
        language = language,
        page = page,
        saving = saving,
        saveFailed = saveFailed,
        backupState = backupState,
        autoBackupConfiguration = autoBackupConfiguration,
        autoBackupLastResult = autoBackupState?.lastResult ?: AutoBackupLastResult.NONE,
        autoBackupLoading = autoBackupLoadResult == null,
        autoBackupLoadFailed = autoBackupLoadResult == AutoBackupSettingsLoadResult.Failed,
        autoBackupUiState = autoBackupUiState,
        onPageChange = {
            page = it
            saveFailed = false
        },
        onIncludeExpectedIncomeChange = {
            includeExpectedIncome = it
            saveFailed = false
        },
        onThemeModeChange = {
            themeMode = it
            saveFailed = false
        },
        onColorPaletteChange = {
            colorPalette = it
            saveFailed = false
        },
        onLanguageChange = onLanguageChange,
        onSave = {
            saving = true
            saveFailed = false
            scope.launch {
                when (
                    editor.save(
                        UserSettings(
                            includeExpectedIncome = includeExpectedIncome,
                            themeMode = themeMode,
                            colorPalette = colorPalette,
                        ),
                    )
                ) {
                    SettingsSaveResult.Saved -> {
                        saving = false
                        page = SettingsPage.ROOT
                        onSaved()
                    }

                    SettingsSaveResult.StorageFailed -> {
                        saveFailed = true
                        saving = false
                    }
                }
            }
        },
        onExportBackup = {
            backupState = BackupUiState.IDLE
            exportLauncher.launch(BACKUP_FILE_NAME)
        },
        onRestoreBackup = {
            backupState = BackupUiState.IDLE
            confirmRestore = true
        },
        onChooseAutoBackupFolder = {
            autoBackupUiState = AutoBackupUiState.IDLE
            autoBackupFolderLauncher.launch(null)
        },
        onForgetAutoBackupFolder = {
            val previousDestination = autoBackupConfiguration.destinationTreeUri
            autoBackupUiState = AutoBackupUiState.WORKING
            scope.launch {
                when (autoBackupEditor.clearDestination(autoBackupConfiguration)) {
                    AutoBackupSettingsSaveResult.Saved -> {
                        if (previousDestination != null) {
                            releasePersistedFolderPermission(
                                context.contentResolver,
                                previousDestination,
                                persistableUriFlags,
                            )
                        }
                        autoBackupUiState = AutoBackupUiState.SAVED
                        autoBackupLoadKey++
                    }

                    AutoBackupSettingsSaveResult.SavedSchedulingFailed -> {
                        if (previousDestination != null) {
                            releasePersistedFolderPermission(
                                context.contentResolver,
                                previousDestination,
                                persistableUriFlags,
                            )
                        }
                        autoBackupUiState = AutoBackupUiState.SCHEDULE_FAILED
                        autoBackupLoadKey++
                    }

                    AutoBackupSettingsSaveResult.Failed -> autoBackupUiState = AutoBackupUiState.FAILED
                }
            }
        },
        onAutoBackupAfterChangesChange = {
            saveAutoBackupConfiguration(autoBackupConfiguration.copy(afterChanges = it))
        },
        onAutoBackupAppStartChange = {
            saveAutoBackupConfiguration(autoBackupConfiguration.copy(onAppStart = it))
        },
        onAutoBackupAppBackgroundChange = {
            saveAutoBackupConfiguration(autoBackupConfiguration.copy(onAppBackground = it))
        },
        onAutoBackupIntervalChange = {
            saveAutoBackupConfiguration(autoBackupConfiguration.copy(interval = it))
        },
        onOpenAbout = onOpenAbout,
        onDismiss = onBack,
    )

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRestore = false
                        restoreLauncher.launch(BACKUP_MIME_TYPES)
                    },
                ) {
                    Text(stringResource(R.string.settings_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.activity_delete_cancel))
                }
            },
        )
    }
}

@Composable
internal fun SettingsScreen(
    includeExpectedIncome: Boolean,
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    colorPalette: ColorPalette = ColorPalette.ORBIT,
    language: AppLanguage = AppLanguage.FOLLOW_DEVICE,
    page: SettingsPage = SettingsPage.ROOT,
    saving: Boolean,
    saveFailed: Boolean,
    backupState: BackupUiState = BackupUiState.IDLE,
    autoBackupConfiguration: AutoBackupConfiguration = AutoBackupConfiguration(),
    autoBackupLastResult: AutoBackupLastResult = AutoBackupLastResult.NONE,
    autoBackupLoading: Boolean = false,
    autoBackupLoadFailed: Boolean = false,
    autoBackupUiState: AutoBackupUiState = AutoBackupUiState.IDLE,
    onPageChange: (SettingsPage) -> Unit = {},
    onIncludeExpectedIncomeChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onColorPaletteChange: (ColorPalette) -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onSave: () -> Unit,
    onExportBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onChooseAutoBackupFolder: () -> Unit = {},
    onForgetAutoBackupFolder: () -> Unit = {},
    onAutoBackupAfterChangesChange: (Boolean) -> Unit = {},
    onAutoBackupAppStartChange: (Boolean) -> Unit = {},
    onAutoBackupAppBackgroundChange: (Boolean) -> Unit = {},
    onAutoBackupIntervalChange: (AutoBackupInterval) -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val backupBusy = backupState == BackupUiState.WORKING
    val autoBackupBusy = autoBackupUiState == AutoBackupUiState.WORKING
    val busy = saving || backupBusy || autoBackupBusy
    val screenTitle = stringResource(R.string.settings_title)
    val pageTitle =
        when (page) {
            SettingsPage.ROOT -> screenTitle
            SettingsPage.APPEARANCE -> stringResource(R.string.settings_appearance)
            SettingsPage.LANGUAGE -> stringResource(R.string.settings_language)
            SettingsPage.CALCULATION -> stringResource(R.string.settings_calculation)
            SettingsPage.DATA -> stringResource(R.string.settings_data_backup)
        }
    val pageHelp =
        when (page) {
            SettingsPage.LANGUAGE -> stringResource(R.string.settings_language_body)
            SettingsPage.CALCULATION -> stringResource(R.string.settings_include_expected_income_body)
            SettingsPage.DATA -> stringResource(R.string.settings_backup_body)
            SettingsPage.ROOT,
            SettingsPage.APPEARANCE,
            -> null
        }

    BackHandler(enabled = !busy) {
        if (page == SettingsPage.ROOT) {
            onDismiss()
        } else {
            onPageChange(SettingsPage.ROOT)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { paneTitle = screenTitle }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page != SettingsPage.ROOT) {
                TextButton(
                    onClick = { onPageChange(SettingsPage.ROOT) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.entry_back))
                }
            }

            if (pageHelp == null) {
                Text(
                    text = pageTitle,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                InfoHeading(
                    title = pageTitle,
                    help = pageHelp,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (page == SettingsPage.ROOT) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        }

        when (page) {
            SettingsPage.ROOT ->
                SettingsRootPage(
                    enabled = !busy,
                    onPageChange = onPageChange,
                    onOpenAbout = onOpenAbout,
                )

            SettingsPage.APPEARANCE ->
                SettingsAppearancePage(
                    themeMode = themeMode,
                    colorPalette = colorPalette,
                    enabled = !busy,
                    saving = saving,
                    onThemeModeChange = onThemeModeChange,
                    onColorPaletteChange = onColorPaletteChange,
                    onSave = onSave,
                )

            SettingsPage.LANGUAGE ->
                SettingsLanguagePage(
                    language = language,
                    enabled = !busy,
                    onLanguageChange = onLanguageChange,
                )

            SettingsPage.CALCULATION ->
                SettingsCalculationPage(
                    includeExpectedIncome = includeExpectedIncome,
                    enabled = !busy,
                    saving = saving,
                    onIncludeExpectedIncomeChange = onIncludeExpectedIncomeChange,
                    onSave = onSave,
                )

            SettingsPage.DATA ->
                SettingsDataPage(
                    enabled = !busy,
                    backupState = backupState,
                    autoBackupConfiguration = autoBackupConfiguration,
                    autoBackupLastResult = autoBackupLastResult,
                    autoBackupLoading = autoBackupLoading,
                    autoBackupLoadFailed = autoBackupLoadFailed,
                    autoBackupUiState = autoBackupUiState,
                    onExportBackup = onExportBackup,
                    onRestoreBackup = onRestoreBackup,
                    onChooseAutoBackupFolder = onChooseAutoBackupFolder,
                    onForgetAutoBackupFolder = onForgetAutoBackupFolder,
                    onAutoBackupAfterChangesChange = onAutoBackupAfterChangesChange,
                    onAutoBackupAppStartChange = onAutoBackupAppStartChange,
                    onAutoBackupAppBackgroundChange = onAutoBackupAppBackgroundChange,
                    onAutoBackupIntervalChange = onAutoBackupIntervalChange,
                )
        }

        if (saveFailed) {
            Text(
                text = stringResource(R.string.settings_save_failed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SettingsRootPage(
    enabled: Boolean,
    onPageChange: (SettingsPage) -> Unit,
    onOpenAbout: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            SettingsMenuRow(
                title = stringResource(R.string.settings_appearance),
                enabled = enabled,
                onClick = { onPageChange(SettingsPage.APPEARANCE) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsMenuRow(
                title = stringResource(R.string.settings_language),
                enabled = enabled,
                onClick = { onPageChange(SettingsPage.LANGUAGE) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsMenuRow(
                title = stringResource(R.string.settings_calculation),
                enabled = enabled,
                onClick = { onPageChange(SettingsPage.CALCULATION) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsMenuRow(
                title = stringResource(R.string.settings_data_backup),
                enabled = enabled,
                onClick = { onPageChange(SettingsPage.DATA) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsMenuRow(
                title = stringResource(R.string.about_title),
                enabled = enabled,
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun SettingsAppearancePage(
    themeMode: ThemeMode,
    colorPalette: ColorPalette,
    enabled: Boolean,
    saving: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorPaletteChange: (ColorPalette) -> Unit,
    onSave: () -> Unit,
) {
    SettingsChoiceCard {
        SettingsSectionTitle(stringResource(R.string.settings_theme))
        ThemeMode.entries.forEach { mode ->
            SettingsChoiceRow(
                label = stringResource(mode.labelResource()),
                selected = themeMode == mode,
                enabled = enabled,
                onClick = { onThemeModeChange(mode) },
            )
        }
    }
    SettingsChoiceCard {
        SettingsSectionTitle(stringResource(R.string.settings_palette))
        ColorPalette.entries.forEach { palette ->
            SettingsChoiceRow(
                label = stringResource(palette.labelResource()),
                selected = colorPalette == palette,
                enabled = enabled,
                onClick = { onColorPaletteChange(palette) },
            )
        }
    }
    SettingsSaveButton(saving = saving, enabled = enabled, onSave = onSave)
}

@Composable
private fun SettingsLanguagePage(
    language: AppLanguage,
    enabled: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    SettingsChoiceCard {
        AppLanguage.entries.forEach { choice ->
            SettingsChoiceRow(
                label = stringResource(choice.labelResource),
                selected = language == choice,
                enabled = enabled,
                onClick = { onLanguageChange(choice) },
            )
        }
    }
}

@Composable
private fun SettingsCalculationPage(
    includeExpectedIncome: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onIncludeExpectedIncomeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .toggleable(
                        value = includeExpectedIncome,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onIncludeExpectedIncomeChange,
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsSectionTitle(stringResource(R.string.settings_include_expected_income))
            }
            Switch(
                checked = includeExpectedIncome,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
    SettingsSaveButton(saving = saving, enabled = enabled, onSave = onSave)
}

@Composable
private fun SettingsDataPage(
    enabled: Boolean,
    backupState: BackupUiState,
    autoBackupConfiguration: AutoBackupConfiguration,
    autoBackupLastResult: AutoBackupLastResult,
    autoBackupLoading: Boolean,
    autoBackupLoadFailed: Boolean,
    autoBackupUiState: AutoBackupUiState,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onChooseAutoBackupFolder: () -> Unit,
    onForgetAutoBackupFolder: () -> Unit,
    onAutoBackupAfterChangesChange: (Boolean) -> Unit,
    onAutoBackupAppStartChange: (Boolean) -> Unit,
    onAutoBackupAppBackgroundChange: (Boolean) -> Unit,
    onAutoBackupIntervalChange: (AutoBackupInterval) -> Unit,
) {
    SettingsSectionTitle(stringResource(R.string.settings_manual_backup))
    Button(
        onClick = onExportBackup,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(stringResource(R.string.settings_export_backup))
    }
    OutlinedButton(
        onClick = onRestoreBackup,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(stringResource(R.string.settings_restore_backup))
    }
    backupState.messageResource()?.let { messageResource ->
        Text(
            text = stringResource(messageResource),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color =
                if (backupState.isError()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    HorizontalDivider()
    InfoHeading(
        title = stringResource(R.string.settings_auto_backup),
        help = stringResource(R.string.settings_auto_backup_body),
    )
    when {
        autoBackupLoading -> {
            CircularProgressIndicator()
        }

        autoBackupLoadFailed -> {
            Text(
                text = stringResource(R.string.settings_auto_backup_load_failed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        else -> {
            Button(
                onClick = onChooseAutoBackupFolder,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Text(
                    stringResource(
                        if (autoBackupConfiguration.hasDestination) {
                            R.string.settings_auto_backup_change_folder
                        } else {
                            R.string.settings_auto_backup_choose_folder
                        },
                    ),
                )
            }
            if (autoBackupConfiguration.hasDestination) {
                Text(
                    text = stringResource(R.string.settings_auto_backup_folder_selected),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onForgetAutoBackupFolder,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.settings_auto_backup_forget_folder))
                }
                SettingsChoiceCard {
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_auto_backup_after_changes),
                        checked = autoBackupConfiguration.afterChanges,
                        enabled = enabled,
                        onCheckedChange = onAutoBackupAfterChangesChange,
                    )
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_auto_backup_on_start),
                        checked = autoBackupConfiguration.onAppStart,
                        enabled = enabled,
                        onCheckedChange = onAutoBackupAppStartChange,
                    )
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_auto_backup_on_background),
                        checked = autoBackupConfiguration.onAppBackground,
                        enabled = enabled,
                        onCheckedChange = onAutoBackupAppBackgroundChange,
                    )
                }
                InfoHeading(
                    title = stringResource(R.string.settings_auto_backup_timed),
                    help = stringResource(R.string.settings_auto_backup_timing_note),
                )
                SettingsChoiceCard {
                    AutoBackupInterval.entries.forEach { interval ->
                        SettingsChoiceRow(
                            label = stringResource(interval.labelResource()),
                            selected = autoBackupConfiguration.interval == interval,
                            enabled = enabled,
                            onClick = { onAutoBackupIntervalChange(interval) },
                        )
                    }
                }
                autoBackupLastResult.messageResource()?.let { messageResource ->
                    Text(
                        text = stringResource(messageResource),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color =
                            if (autoBackupLastResult == AutoBackupLastResult.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            autoBackupUiState.messageResource()?.let { messageResource ->
                Text(
                    text = stringResource(messageResource),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color =
                        if (autoBackupUiState == AutoBackupUiState.FAILED ||
                            autoBackupUiState == AutoBackupUiState.SCHEDULE_FAILED
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsChoiceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsSaveButton(
    saving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        if (saving) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.settings_save))
        }
    }
}

private fun ThemeMode.labelResource(): Int =
    when (this) {
        ThemeMode.FOLLOW_SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private fun ColorPalette.labelResource(): Int =
    when (this) {
        ColorPalette.ORBIT -> R.string.settings_palette_orbit
        ColorPalette.NOVA -> R.string.settings_palette_nova
        ColorPalette.NEBULA -> R.string.settings_palette_nebula
    }

private fun AutoBackupInterval.labelResource(): Int =
    when (this) {
        AutoBackupInterval.NEVER -> R.string.settings_auto_backup_timed_off
        AutoBackupInterval.DAILY -> R.string.settings_auto_backup_timed_daily
        AutoBackupInterval.WEEKLY -> R.string.settings_auto_backup_timed_weekly
    }

internal enum class BackupUiState {
    IDLE,
    WORKING,
    EXPORTED,
    RESTORED,
    EXPORT_FAILED,
    INVALID_BACKUP,
    RESTORE_FAILED,
    RECOVERY_FAILED,
}

internal enum class AutoBackupUiState {
    IDLE,
    WORKING,
    SAVED,
    FOLDER_SELECTED,
    SCHEDULE_FAILED,
    FAILED,
}

private fun AutoBackupUiState.messageResource(): Int? =
    when (this) {
        AutoBackupUiState.IDLE,
        AutoBackupUiState.WORKING,
        -> null

        AutoBackupUiState.SAVED -> R.string.settings_auto_backup_settings_saved
        AutoBackupUiState.FOLDER_SELECTED -> R.string.settings_auto_backup_folder_ready
        AutoBackupUiState.SCHEDULE_FAILED -> R.string.settings_auto_backup_schedule_failed
        AutoBackupUiState.FAILED -> R.string.settings_auto_backup_save_failed
    }

private fun AutoBackupLastResult.messageResource(): Int? =
    when (this) {
        AutoBackupLastResult.NONE -> null
        AutoBackupLastResult.SUCCEEDED -> R.string.settings_auto_backup_last_success
        AutoBackupLastResult.FAILED -> R.string.settings_auto_backup_last_failed
    }

private fun BackupUiState.messageResource(): Int? =
    when (this) {
        BackupUiState.IDLE -> null
        BackupUiState.WORKING -> R.string.settings_backup_working
        BackupUiState.EXPORTED -> R.string.settings_backup_exported
        BackupUiState.RESTORED -> R.string.settings_backup_restored
        BackupUiState.EXPORT_FAILED -> R.string.settings_backup_export_failed
        BackupUiState.INVALID_BACKUP -> R.string.settings_backup_invalid
        BackupUiState.RESTORE_FAILED -> R.string.settings_restore_failed
        BackupUiState.RECOVERY_FAILED -> R.string.settings_restore_recovery_failed
    }

private fun BackupUiState.isError(): Boolean =
    this in
        setOf(
            BackupUiState.EXPORT_FAILED,
            BackupUiState.INVALID_BACKUP,
            BackupUiState.RESTORE_FAILED,
            BackupUiState.RECOVERY_FAILED,
        )

private const val BACKUP_FILE_NAME = "vectorint-backup-v1.json"
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/json", "application/octet-stream")

private fun releasePersistedFolderPermission(
    contentResolver: android.content.ContentResolver,
    uri: String,
    flags: Int,
) {
    runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(uri), flags) }
}

@Composable
private fun SettingsMessageScreen(
    title: String = stringResource(R.string.entry_loading),
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.settings_close))
            }
        }

        if (body == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_try_again))
            }
        }
    }
}
