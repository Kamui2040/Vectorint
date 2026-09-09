package io.github.kamui2040.vectorint.presentation.settings

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kamui2040.vectorint.R
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
        null -> SettingsMessageDialog(onBack = onBack)
        SettingsLoadResult.Failed ->
            SettingsMessageDialog(
                title = stringResource(R.string.settings_load_failed),
                body = stringResource(R.string.settings_load_failed_body),
                onRetry = { loadKey++ },
                onBack = onBack,
            )

        is SettingsLoadResult.Ready ->
            key(current.settings) {
                SettingsReadyRoute(
                    editor = editor,
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
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backupBusy = backupState == BackupUiState.WORKING
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

    SettingsScreen(
        includeExpectedIncome = includeExpectedIncome,
        themeMode = themeMode,
        colorPalette = colorPalette,
        language = language,
        page = page,
        saving = saving,
        saveFailed = saveFailed,
        backupState = backupState,
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
    onPageChange: (SettingsPage) -> Unit = {},
    onIncludeExpectedIncomeChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onColorPaletteChange: (ColorPalette) -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onSave: () -> Unit,
    onExportBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val backupBusy = backupState == BackupUiState.WORKING
    val busy = saving || backupBusy
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

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties =
            DialogProperties(
                dismissOnBackPress = !busy,
                dismissOnClickOutside = !busy,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .heightIn(max = 780.dp)
                        .semantics { paneTitle = screenTitle },
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        InfoHeading(
                            title = pageTitle,
                            help = pageHelp,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
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
                                onExportBackup = onExportBackup,
                                onRestoreBackup = onRestoreBackup,
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
                    if (page == SettingsPage.ROOT) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End),
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.settings_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRootPage(
    enabled: Boolean,
    onPageChange: (SettingsPage) -> Unit,
    onOpenAbout: () -> Unit,
) {
    SettingsMenuCard(
        title = stringResource(R.string.settings_appearance),
        enabled = enabled,
        onClick = { onPageChange(SettingsPage.APPEARANCE) },
    )
    SettingsMenuCard(
        title = stringResource(R.string.settings_language),
        enabled = enabled,
        onClick = { onPageChange(SettingsPage.LANGUAGE) },
    )
    SettingsMenuCard(
        title = stringResource(R.string.settings_calculation),
        enabled = enabled,
        onClick = { onPageChange(SettingsPage.CALCULATION) },
    )
    SettingsMenuCard(
        title = stringResource(R.string.settings_data_backup),
        enabled = enabled,
        onClick = { onPageChange(SettingsPage.DATA) },
    )
    SettingsMenuCard(
        title = stringResource(R.string.about_title),
        enabled = enabled,
        onClick = onOpenAbout,
    )
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
                    ).padding(20.dp),
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
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
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
}

@Composable
private fun SettingsMenuCard(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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
            modifier = Modifier.padding(vertical = 12.dp),
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
                ).padding(horizontal = 16.dp, vertical = 10.dp),
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

@Composable
private fun SettingsMessageDialog(
    title: String = stringResource(R.string.entry_loading),
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .semantics { paneTitle = title },
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = title,
                        modifier =
                            Modifier.semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                            },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
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
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.home_try_again))
                        }
                    }
                    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.settings_close))
                    }
                }
            }
        }
    }
}
