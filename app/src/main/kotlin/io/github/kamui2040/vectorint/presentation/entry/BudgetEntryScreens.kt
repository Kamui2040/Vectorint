package io.github.kamui2040.vectorint.presentation.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.presentation.account.AccountSelector
import io.github.kamui2040.vectorint.presentation.category.CategoryManager
import io.github.kamui2040.vectorint.presentation.category.CategorySelector
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import io.github.kamui2040.vectorint.presentation.tag.TagEditor
import io.github.kamui2040.vectorint.presentation.tag.rememberTagState
import io.github.kamui2040.vectorint.presentation.theme.directionColors
import io.github.kamui2040.vectorint.receipt.ReceiptTotalExtractor
import io.github.kamui2040.vectorint.receipt.ReceiptVendorMatcher
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun OneOffActivityRoute(
    editor: OneOffActivityEditor,
    categoryManager: CategoryManager,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    receiptText: String? = null,
    onReceiptConsumed: () -> Unit = {},
    onScanReceipt: (() -> Unit)? = null,
    receiptScanInProgress: Boolean = false,
    receiptScanMessage: String? = null,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val result by
        produceState<OneOffActivityLoadResult?>(initialValue = null, key1 = loadKey) {
            value = editor.load()
        }

    when (val current = result) {
        null -> EntryLoadingScreen(onBack)
        OneOffActivityLoadResult.NeedsCurrentFunds ->
            EntryMessageScreen(
                title = stringResource(R.string.activity_current_funds_needed),
                body = stringResource(R.string.activity_current_funds_needed_body),
                onRetry = null,
                onBack = onBack,
            )

        OneOffActivityLoadResult.Failed ->
            EntryMessageScreen(
                title = stringResource(R.string.activity_load_failed),
                body = stringResource(R.string.entry_load_failed_body),
                onRetry = { loadKey++ },
                onBack = onBack,
            )

        is OneOffActivityLoadResult.Ready ->
            key(current.seed) {
                OneOffActivityReadyRoute(
                    editor = editor,
                    categoryManager = categoryManager,
                    seed = current.seed,
                    onSaved = onSaved,
                    onBack = onBack,
                    receiptText = receiptText,
                    onReceiptConsumed = onReceiptConsumed,
                    onScanReceipt = onScanReceipt,
                    receiptScanInProgress = receiptScanInProgress,
                    receiptScanMessage = receiptScanMessage,
                )
            }
    }
}

@Composable
private fun OneOffActivityReadyRoute(
    editor: OneOffActivityEditor,
    categoryManager: CategoryManager,
    seed: OneOffActivityFormSeed,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    receiptText: String?,
    onReceiptConsumed: () -> Unit,
    onScanReceipt: (() -> Unit)?,
    receiptScanInProgress: Boolean,
    receiptScanMessage: String?,
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var accountIdValue by rememberSaveable { mutableStateOf(seed.selectedAccountId.value) }
    var direction by rememberSaveable { mutableStateOf(Direction.EXPENSE) }
    var state by rememberSaveable { mutableStateOf(ActivityState.CONFIRMED) }
    var plannedDateEpochDay by rememberSaveable { mutableStateOf(seed.today.toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryIdValue by rememberSaveable { mutableStateOf<String?>(null) }
    var tags by rememberTagState(emptySet())
    var receiptSuggestion by remember { mutableStateOf<ReceiptSuggestion?>(null) }
    var issue by remember { mutableStateOf<EntrySaveResult?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val plannedDate = LocalDate.ofEpochDay(plannedDateEpochDay)
    val plannedDateLabel = remember(plannedDate) { RegionalFormatter().formatDate(plannedDate) }

    LaunchedEffect(receiptText) {
        val recognizedText = receiptText ?: return@LaunchedEffect
        val receiptDraft = receiptDraft(recognizedText, seed.currencyCode, editor::formatInput)
        nameInput = receiptDraft.nameInput.orEmpty()
        amountInput = receiptDraft.amountInput.orEmpty()
        direction = Direction.EXPENSE
        receiptSuggestion = receiptDraft.suggestion
        issue = null
        onReceiptConsumed()
    }

    if (showDatePicker) {
        OneOffDatePickerDialog(
            initialDate = plannedDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                plannedDateEpochDay = it.toEpochDay()
                issue = null
                showDatePicker = false
            },
        )
    }

    BackHandler(enabled = !saving, onBack = onBack)
    OneOffActivityScreen(
        nameInput = nameInput,
        amountInput = amountInput,
        accounts = seed.accounts,
        accountId = AccountId(accountIdValue),
        currencyCode = seed.currencyCode.value,
        direction = direction,
        state = state,
        plannedDateLabel = plannedDateLabel,
        saving = saving,
        issue = issue,
        categoryId = categoryIdValue?.let(::CategoryId),
        categoryManager = categoryManager,
        tags = tags,
        receiptSuggestion = receiptSuggestion,
        onScanReceipt = onScanReceipt,
        receiptScanInProgress = receiptScanInProgress,
        receiptScanMessage = receiptScanMessage,
        onNameChange = {
            nameInput = it
            issue = null
        },
        onAmountChange = {
            amountInput = it
            issue = null
        },
        onAccountChange = {
            accountIdValue = it.value
            issue = null
        },
        onDirectionChange = {
            direction = it
            issue = null
        },
        onStateChange = {
            state = it
            issue = null
        },
        onSelectPlannedDate = { showDatePicker = true },
        onCategoryChange = {
            categoryIdValue = it?.value
            issue = null
        },
        onTagsChange = {
            tags = it
            issue = null
        },
        onSave = {
            saving = true
            issue = null
            scope.launch {
                when (
                    val result =
                        editor.save(
                            nameInput = nameInput,
                            amountInput = amountInput,
                            currencyCode = seed.currencyCode,
                            accountId = AccountId(accountIdValue),
                            direction = direction,
                            state = state,
                            plannedOn = plannedDate,
                            categoryId = categoryIdValue?.let(::CategoryId),
                            tags = tags,
                        )
                ) {
                    EntrySaveResult.Saved -> onSaved()
                    else -> {
                        issue = result
                        saving = false
                    }
                }
            }
        },
        onBack = onBack,
    )
}

@Composable
internal fun OneOffActivityScreen(
    nameInput: String,
    amountInput: String,
    currencyCode: String,
    accounts: List<Account> = emptyList(),
    accountId: AccountId = io.github.kamui2040.vectorint.core.LEGACY_DEFAULT_ACCOUNT_ID,
    direction: Direction,
    state: ActivityState,
    plannedDateLabel: String,
    saving: Boolean,
    issue: EntrySaveResult?,
    categoryId: CategoryId? = null,
    categoryManager: CategoryManager? = null,
    tags: Set<Tag> = emptySet(),
    receiptSuggestion: ReceiptSuggestion? = null,
    onScanReceipt: (() -> Unit)? = null,
    receiptScanInProgress: Boolean = false,
    receiptScanMessage: String? = null,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (AccountId) -> Unit = {},
    onDirectionChange: (Direction) -> Unit,
    onStateChange: (ActivityState) -> Unit,
    onSelectPlannedDate: () -> Unit,
    onCategoryChange: (CategoryId?) -> Unit = {},
    onTagsChange: (Set<Tag>) -> Unit = {},
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    EntryScaffold(
        title = stringResource(R.string.activity_add_title),
        onBack = onBack,
        backEnabled = !saving,
        titleAction =
            onScanReceipt?.let { scanReceipt ->
                {
                    ReceiptScanButton(
                        onClick = scanReceipt,
                        inProgress = receiptScanInProgress,
                        enabled = !saving,
                    )
                }
            },
    ) {
        if (receiptScanMessage != null) {
            Text(
                text = receiptScanMessage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (receiptSuggestion != null) {
            Text(
                text =
                    stringResource(
                        when (receiptSuggestion) {
                            ReceiptSuggestion.TOTAL_ADDED -> R.string.activity_receipt_total_added
                            ReceiptSuggestion.TOTAL_NOT_FOUND -> R.string.activity_receipt_total_not_found
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            label = { Text(stringResource(R.string.entry_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
            isError = issue == EntrySaveResult.InvalidName,
        )
        OutlinedTextField(
            value = amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            label = { Text(stringResource(R.string.entry_amount)) },
            supportingText = { Text(stringResource(R.string.activity_currency, currencyCode)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = issue == EntrySaveResult.InvalidAmount || issue == EntrySaveResult.AmountMustBePositive,
        )

        AccountSelector(
            accounts = accounts,
            selectedAccountId = accountId,
            enabled = !saving,
            onAccountChange = onAccountChange,
        )

        SelectionHeading(stringResource(R.string.activity_direction))
        val expenseColors = directionColors(Direction.EXPENSE)
        val incomeColors = directionColors(Direction.INCOME)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = direction == Direction.EXPENSE,
                onClick = { onDirectionChange(Direction.EXPENSE) },
                label = { Text(stringResource(R.string.activity_expense)) },
                modifier = Modifier.weight(1f),
                enabled = !saving,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = expenseColors.container,
                        selectedLabelColor = expenseColors.onContainer,
                    ),
            )
            FilterChip(
                selected = direction == Direction.INCOME,
                onClick = { onDirectionChange(Direction.INCOME) },
                label = { Text(stringResource(R.string.activity_income)) },
                modifier = Modifier.weight(1f),
                enabled = !saving,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = incomeColors.container,
                        selectedLabelColor = incomeColors.onContainer,
                    ),
            )
        }

        InfoHeading(
            title = stringResource(R.string.activity_timing),
            help = activityEffectText(direction, state),
        )
        FilterChip(
            selected = state == ActivityState.CONFIRMED,
            onClick = { onStateChange(ActivityState.CONFIRMED) },
            label = { Text(stringResource(R.string.activity_confirmed_now)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        )
        FilterChip(
            selected = state == ActivityState.PLANNED,
            onClick = { onStateChange(ActivityState.PLANNED) },
            label = { Text(stringResource(R.string.activity_planned_month)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        )
        if (state == ActivityState.PLANNED) {
            OutlinedButton(
                onClick = onSelectPlannedDate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            ) {
                Text(stringResource(R.string.activity_timing_planned_date, plannedDateLabel))
            }
        }
        categoryManager?.let { manager ->
            CategorySelector(
                selectedCategoryId = categoryId,
                enabled = !saving,
                manager = manager,
                onCategoryChange = onCategoryChange,
            )
        }
        TagEditor(
            tags = tags,
            enabled = !saving,
            onTagsChange = onTagsChange,
        )
        EntryIssue(issue)
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.activity_save))
            }
        }
    }
}

internal enum class ReceiptSuggestion {
    TOTAL_ADDED,
    TOTAL_NOT_FOUND,
}

private data class ReceiptDraft(
    val nameInput: String?,
    val amountInput: String?,
    val suggestion: ReceiptSuggestion,
)

private fun receiptDraft(
    recognizedText: String,
    currencyCode: io.github.kamui2040.vectorint.core.CurrencyCode,
    formatInput: (io.github.kamui2040.vectorint.core.Money) -> String,
): ReceiptDraft {
    val amountInput = ReceiptTotalExtractor.extract(recognizedText, currencyCode)?.let(formatInput)
    return ReceiptDraft(
        nameInput = ReceiptVendorMatcher.match(recognizedText)?.displayName,
        amountInput = amountInput,
        suggestion =
            if (amountInput == null) {
                ReceiptSuggestion.TOTAL_NOT_FOUND
            } else {
                ReceiptSuggestion.TOTAL_ADDED
            },
    )
}

@Composable
private fun ReceiptScanButton(
    onClick: () -> Unit,
    inProgress: Boolean,
    enabled: Boolean,
) {
    val label = stringResource(R.string.home_scan_receipt)
    FilledIconButton(
        onClick = onClick,
        enabled = enabled && !inProgress,
        modifier =
            Modifier
                .size(56.dp)
                .semantics { contentDescription = label },
    ) {
        if (inProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun SelectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun activityEffectText(
    direction: Direction,
    state: ActivityState,
): String =
    stringResource(
        when (direction to state) {
            Direction.EXPENSE to ActivityState.CONFIRMED -> R.string.activity_effect_confirmed_expense
            Direction.INCOME to ActivityState.CONFIRMED -> R.string.activity_effect_confirmed_income
            Direction.EXPENSE to ActivityState.PLANNED -> R.string.activity_effect_planned_expense
            Direction.INCOME to ActivityState.PLANNED -> R.string.activity_effect_planned_income
            else -> error("Unknown activity selection")
        },
    )

@Composable
private fun EntryIssue(issue: EntrySaveResult?) {
    val message =
        when (issue) {
            null,
            EntrySaveResult.Saved,
            -> null

            EntrySaveResult.InvalidName -> stringResource(R.string.entry_invalid_name)
            EntrySaveResult.InvalidCurrency -> stringResource(R.string.entry_invalid_currency)
            EntrySaveResult.InvalidAmount -> stringResource(R.string.entry_invalid_amount)
            EntrySaveResult.AmountMustBePositive -> stringResource(R.string.entry_amount_positive)
            EntrySaveResult.DateMustNotBePast -> stringResource(R.string.activity_date_must_not_be_past)
            EntrySaveResult.StorageFailed -> stringResource(R.string.entry_save_failed)
        }
    if (message != null) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneOffDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    key(initialDate) {
        val pickerState =
            androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = Math.multiplyExact(initialDate.toEpochDay(), MILLIS_PER_DAY),
            )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = pickerState.selectedDateMillis ?: return@TextButton
                        onDateSelected(LocalDate.ofEpochDay(Math.floorDiv(selected, MILLIS_PER_DAY)))
                    },
                ) {
                    Text(stringResource(R.string.recurring_set_date))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.activity_delete_cancel))
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
            )
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
private fun EntryLoadingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    EntryScaffold(
        title = stringResource(R.string.entry_loading),
        onBack = onBack,
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun EntryMessageScreen(
    title: String,
    body: String,
    onRetry: (() -> Unit)?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    EntryScaffold(title = title, onBack = onBack) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.home_try_again))
            }
        }
    }
}

@Composable
private fun EntryScaffold(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(
                onClick = onBack,
                enabled = backEnabled,
            ) {
                Text(stringResource(R.string.entry_back))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                titleAction?.invoke()
            }
            content()
        }
    }
}
