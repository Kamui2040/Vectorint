package io.github.kamui2040.vectorint.presentation.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.presentation.category.CategoryManager
import io.github.kamui2040.vectorint.presentation.category.CategorySelector
import io.github.kamui2040.vectorint.presentation.category.CategorySummary
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import io.github.kamui2040.vectorint.presentation.tag.TagEditor
import io.github.kamui2040.vectorint.presentation.tag.TagSummary
import io.github.kamui2040.vectorint.presentation.tag.rememberTagState
import io.github.kamui2040.vectorint.presentation.theme.directionColors
import kotlinx.coroutines.launch

@Composable
internal fun ActivityHistoryRoute(
    loader: ActivityHistoryLoader,
    onActivitySelected: (ActivityId) -> Unit,
    onAddActivity: () -> Unit,
    onBack: () -> Unit,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val state by
        produceState<ActivityHistoryUiState>(
            initialValue = ActivityHistoryUiState.Loading,
            key1 = loadKey,
        ) {
            value = ActivityHistoryUiState.Loading
            value = loader.load()
        }

    BackHandler(onBack = onBack)
    ActivityHistoryScreen(
        state = state,
        onActivitySelected = onActivitySelected,
        onAddActivity = onAddActivity,
        onRetry = { loadKey++ },
    )
}

@Composable
internal fun ActivityHistoryScreen(
    state: ActivityHistoryUiState,
    onActivitySelected: (ActivityId) -> Unit,
    onAddActivity: () -> Unit,
    onRetry: () -> Unit,
) {
    val screenTitle = stringResource(R.string.activity_history_title)
    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { paneTitle = screenTitle },
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = screenTitle,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            when (state) {
                ActivityHistoryUiState.Loading ->
                    item {
                        ActivityLoadingContent()
                    }

                ActivityHistoryUiState.Empty -> {
                    item {
                        ActivityMessageCard(
                            title = stringResource(R.string.activity_history_empty),
                            body = stringResource(R.string.activity_history_empty_body),
                        )
                    }
                    item {
                        Button(
                            onClick = onAddActivity,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.home_add_activity))
                        }
                    }
                }

                ActivityHistoryUiState.LoadFailed -> {
                    item {
                        ActivityMessageCard(
                            title = stringResource(R.string.activity_history_load_failed),
                            body = stringResource(R.string.entry_load_failed_body),
                        )
                    }
                    item {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.home_try_again))
                        }
                    }
                }

                is ActivityHistoryUiState.Ready -> {
                    item {
                        Button(
                            onClick = onAddActivity,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.home_add_activity))
                        }
                    }
                    items(
                        items = state.items,
                        key = { item -> item.id.value },
                    ) { item ->
                        ActivityHistoryCard(
                            item = item,
                            customCategories = state.customCategories,
                            onClick = { onActivitySelected(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHistoryCard(
    item: ActivityHistoryItemUi,
    customCategories: List<CustomCategory>,
    onClick: () -> Unit,
) {
    val directionColors = directionColors(item.direction)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = directionColors.container,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name.ifBlank { stringResource(R.string.activity_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = directionColors.onContainer,
                )
                Text(
                    text = directionLabel(item.direction),
                    style = MaterialTheme.typography.labelLarge,
                    color = directionColors.onContainer.copy(alpha = 0.9f),
                )
                Text(
                    text = timingLabel(item.timing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = directionColors.onContainer.copy(alpha = 0.78f),
                )
                CategorySummary(
                    categoryId = item.categoryId,
                    customCategories = customCategories,
                    color = directionColors.onContainer.copy(alpha = 0.9f),
                )
                TagSummary(item.tags)
            }
            Text(
                text = item.amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = directionColors.onContainer,
            )
        }
    }
}

@Composable
internal fun ActivityEditRoute(
    editor: ActivityEditor,
    categoryManager: CategoryManager,
    activityId: ActivityId,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val result by
        produceState<ActivityEditLoadResult?>(initialValue = null, key1 = activityId, key2 = loadKey) {
            value = editor.load(activityId)
        }

    when (val current = result) {
        null -> ActivityEditMessageScreen(title = stringResource(R.string.entry_loading), onBack = onBack)
        ActivityEditLoadResult.Missing ->
            ActivityEditMessageScreen(
                title = stringResource(R.string.activity_missing),
                body = stringResource(R.string.activity_missing_body),
                onBack = onBack,
            )

        ActivityEditLoadResult.Failed ->
            ActivityEditMessageScreen(
                title = stringResource(R.string.activity_edit_load_failed),
                body = stringResource(R.string.entry_load_failed_body),
                onRetry = { loadKey++ },
                onBack = onBack,
            )

        is ActivityEditLoadResult.Ready ->
            key(current.seed) {
                ActivityEditReadyRoute(
                    editor = editor,
                    categoryManager = categoryManager,
                    seed = current.seed,
                    onChanged = onChanged,
                    onBack = onBack,
                )
            }
    }
}

@Composable
private fun ActivityEditReadyRoute(
    editor: ActivityEditor,
    categoryManager: CategoryManager,
    seed: ActivityEditSeed,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var nameInput by rememberSaveable { mutableStateOf(seed.activity.name) }
    var amountInput by rememberSaveable { mutableStateOf(seed.amountInput) }
    var direction by rememberSaveable { mutableStateOf(seed.activity.direction) }
    var categoryIdValue by rememberSaveable { mutableStateOf(seed.activity.categoryId?.value) }
    var tags by rememberTagState(seed.activity.tags)
    var issue by remember { mutableStateOf<ActivityEditIssue?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runMutation(block: suspend () -> ActivityMutationResult) {
        busy = true
        issue = null
        scope.launch {
            when (val result = block()) {
                ActivityMutationResult.Saved,
                ActivityMutationResult.Confirmed,
                ActivityMutationResult.Deleted,
                -> onChanged()

                ActivityMutationResult.InvalidName -> issue = ActivityEditIssue.InvalidName
                ActivityMutationResult.InvalidAmount -> issue = ActivityEditIssue.InvalidAmount
                ActivityMutationResult.AmountMustBePositive -> issue = ActivityEditIssue.AmountMustBePositive
                ActivityMutationResult.Missing -> issue = ActivityEditIssue.Missing
                ActivityMutationResult.StorageFailed -> issue = ActivityEditIssue.StorageFailed
            }
            busy = false
        }
    }

    BackHandler(enabled = !busy && !showDeleteConfirmation, onBack = onBack)
    ActivityEditScreen(
        seed = seed,
        nameInput = nameInput,
        amountInput = amountInput,
        direction = direction,
        busy = busy,
        issue = issue,
        categoryId = categoryIdValue?.let(::CategoryId),
        categoryManager = categoryManager,
        tags = tags,
        showDeleteConfirmation = showDeleteConfirmation,
        onNameChange = {
            nameInput = it
            issue = null
        },
        onAmountChange = {
            amountInput = it
            issue = null
        },
        onDirectionChange = {
            direction = it
            issue = null
        },
        onCategoryChange = {
            categoryIdValue = it?.value
            issue = null
        },
        onTagsChange = {
            tags = it
            issue = null
        },
        onSave = {
            runMutation {
                editor.save(
                    seed = seed,
                    nameInput = nameInput,
                    amountInput = amountInput,
                    direction = direction,
                    tags = tags,
                    categoryId = categoryIdValue?.let(::CategoryId),
                )
            }
        },
        onConfirm = {
            runMutation {
                editor.confirm(
                    seed = seed,
                    nameInput = nameInput,
                    amountInput = amountInput,
                    direction = direction,
                    tags = tags,
                    categoryId = categoryIdValue?.let(::CategoryId),
                )
            }
        },
        onDeleteRequest = { showDeleteConfirmation = true },
        onDeleteDismiss = { showDeleteConfirmation = false },
        onDeleteConfirm = {
            showDeleteConfirmation = false
            runMutation { editor.delete(seed.activity.id) }
        },
        onBack = onBack,
    )
}

@Composable
internal fun ActivityEditScreen(
    seed: ActivityEditSeed,
    nameInput: String,
    amountInput: String,
    direction: Direction,
    busy: Boolean,
    issue: ActivityEditIssue?,
    categoryId: CategoryId? = seed.activity.categoryId,
    categoryManager: CategoryManager? = null,
    tags: Set<Tag> = seed.activity.tags,
    showDeleteConfirmation: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (Direction) -> Unit,
    onCategoryChange: (CategoryId?) -> Unit = {},
    onTagsChange: (Set<Tag>) -> Unit = {},
    onSave: () -> Unit,
    onConfirm: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    ActivityEditScaffold(
        title = stringResource(R.string.activity_edit_title),
        onBack = onBack,
        backEnabled = !busy,
    ) {
        ActivityStatusCard(seed.timing)
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text(stringResource(R.string.entry_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
            isError = issue == ActivityEditIssue.InvalidName,
        )
        OutlinedTextField(
            value = amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text(stringResource(R.string.entry_amount)) },
            supportingText = {
                Text(stringResource(R.string.activity_currency, seed.activity.amount.currency.value))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError =
                issue == ActivityEditIssue.InvalidAmount ||
                    issue == ActivityEditIssue.AmountMustBePositive,
        )
        Text(
            text = stringResource(R.string.activity_direction),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
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
                enabled = !busy,
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
                enabled = !busy,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = incomeColors.container,
                        selectedLabelColor = incomeColors.onContainer,
                    ),
            )
        }
        categoryManager?.let { manager ->
            CategorySelector(
                selectedCategoryId = categoryId,
                enabled = !busy,
                manager = manager,
                onCategoryChange = onCategoryChange,
            )
        }
        TagEditor(
            tags = tags,
            enabled = !busy,
            onTagsChange = onTagsChange,
        )
        ActivityEditIssueMessage(issue)
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text(stringResource(R.string.activity_save_changes))
        }
        if (seed.activity.state == ActivityState.PLANNED) {
            InfoHeading(
                title = stringResource(R.string.activity_confirmation),
                help = stringResource(R.string.activity_confirm_explanation),
            )
            OutlinedButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(stringResource(R.string.activity_confirm_now))
            }
        }
        TextButton(
            onClick = onDeleteRequest,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.activity_delete))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.activity_delete_title)) },
            text = { Text(stringResource(R.string.activity_delete_body)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(R.string.activity_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.activity_delete_cancel))
                }
            },
        )
    }
}

internal enum class ActivityEditIssue {
    InvalidName,
    InvalidAmount,
    AmountMustBePositive,
    Missing,
    StorageFailed,
}

@Composable
private fun ActivityStatusCard(timing: ActivityTimingUi) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = statusLabel(timing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = timingLabel(timing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityEditIssueMessage(issue: ActivityEditIssue?) {
    val message =
        when (issue) {
            null -> null
            ActivityEditIssue.InvalidName -> stringResource(R.string.entry_invalid_name)
            ActivityEditIssue.InvalidAmount -> stringResource(R.string.entry_invalid_amount)
            ActivityEditIssue.AmountMustBePositive -> stringResource(R.string.entry_amount_positive)
            ActivityEditIssue.Missing -> stringResource(R.string.activity_missing_body)
            ActivityEditIssue.StorageFailed -> stringResource(R.string.activity_change_failed)
        }
    if (message != null) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ActivityEditMessageScreen(
    title: String,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ActivityEditScaffold(title = title, onBack = onBack) {
        if (body == null) {
            ActivityLoadingContent()
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
    }
}

@Composable
private fun ActivityEditScaffold(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
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
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun ActivityLoadingContent() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ActivityMessageCard(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier =
                    Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun directionLabel(direction: Direction): String =
    stringResource(
        when (direction) {
            Direction.EXPENSE -> R.string.activity_expense
            Direction.INCOME -> R.string.activity_income
        },
    )

@Composable
private fun statusLabel(timing: ActivityTimingUi): String =
    stringResource(
        when (timing) {
            is ActivityTimingUi.ConfirmedDate -> R.string.activity_status_confirmed
            is ActivityTimingUi.PlannedDate,
            is ActivityTimingUi.PlannedMonth,
            -> R.string.activity_status_planned
        },
    )

@Composable
private fun timingLabel(timing: ActivityTimingUi): String =
    when (timing) {
        is ActivityTimingUi.ConfirmedDate ->
            stringResource(R.string.activity_timing_confirmed, timing.date)

        is ActivityTimingUi.PlannedDate ->
            stringResource(R.string.activity_timing_planned_date, timing.date)

        is ActivityTimingUi.PlannedMonth ->
            stringResource(R.string.activity_timing_planned_month, timing.month)
    }
