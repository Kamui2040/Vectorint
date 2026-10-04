package io.github.kamui2040.vectorint.presentation.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.presentation.account.AccountSelector
import io.github.kamui2040.vectorint.presentation.category.CategoryManager
import io.github.kamui2040.vectorint.presentation.category.CategorySelector
import io.github.kamui2040.vectorint.presentation.category.CategorySummary
import io.github.kamui2040.vectorint.presentation.category.labelResource
import io.github.kamui2040.vectorint.presentation.component.ActiveFilterUi
import io.github.kamui2040.vectorint.presentation.component.CompactFilterBar
import io.github.kamui2040.vectorint.presentation.component.FilterOptionRow
import io.github.kamui2040.vectorint.presentation.component.FilterSheetActions
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import io.github.kamui2040.vectorint.presentation.tag.TagEditor
import io.github.kamui2040.vectorint.presentation.tag.TagSummary
import io.github.kamui2040.vectorint.presentation.tag.rememberTagState
import io.github.kamui2040.vectorint.presentation.theme.directionColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun RecurringListRoute(
    loader: RecurringListLoader,
    onItemSelected: (RecurringItemId) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val state by
        produceState<RecurringListUiState>(initialValue = RecurringListUiState.Loading, key1 = loadKey) {
            value = loader.load()
        }
    RecurringListScreen(
        state = state,
        onRetry = { loadKey++ },
        onItemSelected = onItemSelected,
        onAdd = onAdd,
        onBack = onBack,
    )
}

@Composable
internal fun RecurringListScreen(
    state: RecurringListUiState,
    onRetry: () -> Unit,
    onItemSelected: (RecurringItemId) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var directionFilter by rememberSaveable { mutableStateOf(RecurringDirectionFilter.ALL) }
    var assignmentFilter by rememberSaveable { mutableStateOf(RecurringAssignmentFilter.ALL) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("recurring_list")
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.entry_back))
                    }
                    Text(
                        text = stringResource(R.string.recurring_title),
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onAdd) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.recurring_add),
                        )
                    }
                }
            }
            when (state) {
                RecurringListUiState.Loading -> item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                RecurringListUiState.Empty ->
                    item {
                        RecurringMessageCard(
                            title = stringResource(R.string.recurring_empty),
                            body = stringResource(R.string.recurring_empty_body),
                        )
                    }

                RecurringListUiState.LoadFailed ->
                    item {
                        RecurringMessageCard(
                            title = stringResource(R.string.recurring_load_failed),
                            body = stringResource(R.string.entry_load_failed_body),
                            actionLabel = stringResource(R.string.home_try_again),
                            onAction = onRetry,
                        )
                    }

                is RecurringListUiState.Ready -> {
                    val filteredItems =
                        state.items.filter { item ->
                            val categoryName =
                                if (item.categoryId == null) {
                                    ""
                                } else {
                                    item.categoryId
                                        .let(PredefinedCategory::fromId)
                                        ?.let { resources.getString(it.labelResource()) }
                                        ?: state.customCategories.singleOrNull { it.id == item.categoryId }?.name
                                        ?: resources.getString(R.string.category_other)
                                }
                            item.matchesRecurringFilters(
                                query = searchQuery,
                                directionFilter = directionFilter,
                                assignmentFilter = assignmentFilter,
                                categoryName = categoryName,
                                directionName =
                                    resources.getString(
                                        if (item.direction == Direction.EXPENSE) {
                                            R.string.activity_expense
                                        } else {
                                            R.string.activity_income
                                        },
                                    ),
                                unassignedName = resources.getString(R.string.account_unassigned),
                            )
                        }
                    item {
                        RecurringSearchAndFilters(
                            searchQuery = searchQuery,
                            directionFilter = directionFilter,
                            assignmentFilter = assignmentFilter,
                            onSearchQueryChange = { searchQuery = it },
                            onDirectionFilterChange = { directionFilter = it },
                            onAssignmentFilterChange = { assignmentFilter = it },
                            onOpenFilters = { showFilters = true },
                        )
                    }
                    if (filteredItems.isEmpty()) {
                        item {
                            RecurringNoMatches(
                                onClear = {
                                    searchQuery = ""
                                    directionFilter = RecurringDirectionFilter.ALL
                                    assignmentFilter = RecurringAssignmentFilter.ALL
                                },
                            )
                        }
                    } else {
                        items(
                            items = filteredItems,
                            key = { it.id.value },
                        ) { item ->
                            RecurringItemCard(
                                item = item,
                                customCategories = state.customCategories,
                                onClick = { onItemSelected(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        RecurringFilterSheet(
            directionFilter = directionFilter,
            assignmentFilter = assignmentFilter,
            onDismiss = { showFilters = false },
            onApply = { direction, assignment ->
                directionFilter = direction
                assignmentFilter = assignment
                showFilters = false
            },
            onReset = {
                directionFilter = RecurringDirectionFilter.ALL
                assignmentFilter = RecurringAssignmentFilter.ALL
                showFilters = false
            },
        )
    }
}

private enum class RecurringDirectionFilter {
    ALL,
    EXPENSE,
    INCOME,
}

private enum class RecurringAssignmentFilter {
    ALL,
    ASSIGNED,
    UNASSIGNED,
}

@Composable
private fun RecurringSearchAndFilters(
    searchQuery: String,
    directionFilter: RecurringDirectionFilter,
    assignmentFilter: RecurringAssignmentFilter,
    onSearchQueryChange: (String) -> Unit,
    onDirectionFilterChange: (RecurringDirectionFilter) -> Unit,
    onAssignmentFilterChange: (RecurringAssignmentFilter) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val activeFilters =
        buildList {
            if (directionFilter != RecurringDirectionFilter.ALL) {
                add(
                    ActiveFilterUi(
                        key = "direction",
                        label =
                            stringResource(
                                if (directionFilter == RecurringDirectionFilter.EXPENSE) {
                                    R.string.activity_expense
                                } else {
                                    R.string.activity_income
                                },
                            ),
                        onRemove = { onDirectionFilterChange(RecurringDirectionFilter.ALL) },
                    ),
                )
            }
            if (assignmentFilter != RecurringAssignmentFilter.ALL) {
                add(
                    ActiveFilterUi(
                        key = "assignment",
                        label =
                            stringResource(
                                if (assignmentFilter == RecurringAssignmentFilter.ASSIGNED) {
                                    R.string.account_assigned
                                } else {
                                    R.string.account_unassigned
                                },
                            ),
                        onRemove = { onAssignmentFilterChange(RecurringAssignmentFilter.ALL) },
                    ),
                )
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.recurring_search)) },
            placeholder = { Text(stringResource(R.string.recurring_search_hint)) },
            leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
            trailingIcon =
                if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.recurring_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
            singleLine = true,
        )
        CompactFilterBar(
            activeFilters = activeFilters,
            onOpenFilters = onOpenFilters,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringFilterSheet(
    directionFilter: RecurringDirectionFilter,
    assignmentFilter: RecurringAssignmentFilter,
    onDismiss: () -> Unit,
    onApply: (RecurringDirectionFilter, RecurringAssignmentFilter) -> Unit,
    onReset: () -> Unit,
) {
    var draftDirection by rememberSaveable { mutableStateOf(directionFilter) }
    var draftAssignment by rememberSaveable { mutableStateOf(assignmentFilter) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.filters),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.activity_history_filter_type),
                style = MaterialTheme.typography.labelLarge,
            )
            FilterOptionRow(
                label = stringResource(R.string.activity_history_filter_type),
                tagPrefix = "recurring_direction",
                options =
                    listOf(
                        RecurringDirectionFilter.ALL to stringResource(R.string.activity_history_filter_all_types),
                        RecurringDirectionFilter.EXPENSE to stringResource(R.string.activity_expense),
                        RecurringDirectionFilter.INCOME to stringResource(R.string.activity_income),
                    ),
                selected = draftDirection,
                onSelected = { draftDirection = it },
            )
            Text(
                text = stringResource(R.string.account_assignment_filter),
                style = MaterialTheme.typography.labelLarge,
            )
            FilterOptionRow(
                label = stringResource(R.string.account_assignment_filter),
                tagPrefix = "recurring_assignment",
                options =
                    listOf(
                        RecurringAssignmentFilter.ALL to stringResource(R.string.account_assignment_all),
                        RecurringAssignmentFilter.ASSIGNED to stringResource(R.string.account_assigned),
                        RecurringAssignmentFilter.UNASSIGNED to stringResource(R.string.account_unassigned),
                    ),
                selected = draftAssignment,
                onSelected = { draftAssignment = it },
            )
            FilterSheetActions(
                onReset = {
                    draftDirection = RecurringDirectionFilter.ALL
                    draftAssignment = RecurringAssignmentFilter.ALL
                    onReset()
                },
                onApply = { onApply(draftDirection, draftAssignment) },
            )
        }
    }
}

@Composable
private fun RecurringNoMatches(onClear: () -> Unit) {
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecurringMessageCard(
            title = stringResource(R.string.recurring_no_matches),
            body = stringResource(R.string.recurring_no_matches_body),
        )
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.recurring_clear_all))
        }
    }
}

private fun RecurringListItemUi.matchesRecurringFilters(
    query: String,
    directionFilter: RecurringDirectionFilter,
    assignmentFilter: RecurringAssignmentFilter,
    categoryName: String,
    directionName: String,
    unassignedName: String,
): Boolean {
    val directionMatches =
        when (directionFilter) {
            RecurringDirectionFilter.ALL -> true
            RecurringDirectionFilter.EXPENSE -> direction == Direction.EXPENSE
            RecurringDirectionFilter.INCOME -> direction == Direction.INCOME
        }
    val assignmentMatches =
        when (assignmentFilter) {
            RecurringAssignmentFilter.ALL -> true
            RecurringAssignmentFilter.ASSIGNED -> accountName != null
            RecurringAssignmentFilter.UNASSIGNED -> accountName == null
        }
    val trimmedQuery = query.trim()
    val searchMatches =
        trimmedQuery.isEmpty() ||
            listOf(
                name,
                amount,
                firstOccurrenceLabel,
                accountName ?: unassignedName,
                categoryName,
                directionName,
                *tags.toTypedArray(),
            ).any { value -> value.contains(trimmedQuery, ignoreCase = true) }
    return directionMatches && assignmentMatches && searchMatches
}

@Composable
private fun RecurringItemCard(
    item: RecurringListItemUi,
    customCategories: List<CustomCategory>,
    onClick: () -> Unit,
) {
    val directionColors = directionColors(item.direction)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = directionColors.container,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = directionColors.onContainer,
            )
            Text(
                text =
                    stringResource(
                        if (item.direction == Direction.EXPENSE) {
                            R.string.recurring_expense_amount
                        } else {
                            R.string.recurring_income_amount
                        },
                        item.amount,
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = directionColors.onContainer,
            )
            Text(
                text = recurringScheduleLabel(item),
                style = MaterialTheme.typography.bodyMedium,
                color = directionColors.onContainer.copy(alpha = 0.82f),
            )
            Text(
                text =
                    stringResource(
                        R.string.account_assignment_label,
                        item.accountName ?: stringResource(R.string.account_unassigned),
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = directionColors.onContainer.copy(alpha = 0.9f),
            )
            if (item.schedule.countsToward == BudgetMonthAssignment.FOLLOWING_MONTH) {
                Text(
                    text = stringResource(R.string.recurring_counts_following_month_short),
                    style = MaterialTheme.typography.labelMedium,
                    color = directionColors.onContainer.copy(alpha = 0.82f),
                )
            }
            if (item.categoryId != null) {
                CategorySummary(
                    categoryId = item.categoryId,
                    customCategories = customCategories,
                    color = directionColors.onContainer.copy(alpha = 0.9f),
                )
            }
            TagSummary(item.tags)
        }
    }
}

@Composable
internal fun RecurringItemRoute(
    editor: RecurringItemEditor,
    categoryManager: CategoryManager,
    scheduleAdapter: RecurringScheduleInputAdapter,
    recurringItemId: RecurringItemId?,
    notificationsAvailable: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val result by
        produceState<RecurringItemLoadResult?>(initialValue = null, key1 = loadKey, key2 = recurringItemId) {
            value = editor.load(recurringItemId)
        }

    when (val current = result) {
        null -> RecurringEditorMessageScreen(stringResource(R.string.entry_loading), null, null, onBack)
        RecurringItemLoadResult.NeedsCurrentFunds ->
            RecurringEditorMessageScreen(
                title = stringResource(R.string.activity_current_funds_needed),
                body = stringResource(R.string.recurring_current_funds_needed_body),
                onRetry = null,
                onBack = onBack,
            )

        RecurringItemLoadResult.Missing ->
            RecurringEditorMessageScreen(
                title = stringResource(R.string.recurring_missing),
                body = stringResource(R.string.recurring_missing_body),
                onRetry = null,
                onBack = onBack,
            )

        RecurringItemLoadResult.Failed ->
            RecurringEditorMessageScreen(
                title = stringResource(R.string.recurring_load_failed),
                body = stringResource(R.string.entry_load_failed_body),
                onRetry = { loadKey++ },
                onBack = onBack,
            )

        is RecurringItemLoadResult.Ready ->
            key(current.seed) {
                RecurringItemReadyRoute(
                    editor = editor,
                    categoryManager = categoryManager,
                    scheduleAdapter = scheduleAdapter,
                    seed = current.seed,
                    notificationsAvailable = notificationsAvailable,
                    onEnableNotifications = onEnableNotifications,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onChanged = onChanged,
                    onBack = onBack,
                )
            }
    }
}

@Composable
private fun RecurringItemReadyRoute(
    editor: RecurringItemEditor,
    categoryManager: CategoryManager,
    scheduleAdapter: RecurringScheduleInputAdapter,
    seed: RecurringItemFormSeed,
    notificationsAvailable: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var nameInput by rememberSaveable { mutableStateOf(seed.nameInput) }
    var amountInput by rememberSaveable { mutableStateOf(seed.amountInput) }
    var accountIdValue by rememberSaveable { mutableStateOf(seed.accountId?.value) }
    var direction by rememberSaveable { mutableStateOf(seed.direction) }
    var includeExpectedIncome by rememberSaveable { mutableStateOf(seed.includeExpectedIncome) }
    var firstOccurrenceEpochDay by rememberSaveable { mutableLongStateOf(seed.firstOccurrence.toEpochDay()) }
    var timingChoice by rememberSaveable { mutableStateOf(seed.timingChoice) }
    var firstPeriodEndsOnEpochDay by rememberSaveable { mutableStateOf(seed.firstPeriodEndsOn?.toEpochDay()) }
    var repeatEveryInput by rememberSaveable { mutableStateOf(seed.repeatEveryInput) }
    var repeatUnit by rememberSaveable { mutableStateOf(seed.repeatUnit) }
    var countsToward by rememberSaveable { mutableStateOf(seed.countsToward) }
    var requireManualConfirmation by rememberSaveable { mutableStateOf(seed.requireManualConfirmation) }
    var endsOnEpochDay by rememberSaveable { mutableStateOf(seed.endsOn?.toEpochDay()) }
    var remindOnEpochDay by rememberSaveable { mutableStateOf(seed.remindOn?.toEpochDay()) }
    var occurrenceReminderEnabled by rememberSaveable { mutableStateOf(seed.occurrenceReminder.enabled) }
    var occurrenceReminderInput by rememberSaveable { mutableStateOf(seed.occurrenceReminder.daysBeforeInput) }
    var remindReminderEnabled by rememberSaveable { mutableStateOf(seed.remindReminder.enabled) }
    var remindReminderInput by rememberSaveable { mutableStateOf(seed.remindReminder.daysBeforeInput) }
    var endReminderEnabled by rememberSaveable { mutableStateOf(seed.endReminder.enabled) }
    var endReminderInput by rememberSaveable { mutableStateOf(seed.endReminder.daysBeforeInput) }
    var categoryIdValue by rememberSaveable { mutableStateOf(seed.categoryId?.value) }
    var tags by rememberTagState(seed.tags)
    var issue by remember { mutableStateOf<RecurringItemMutationResult?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var selectedDateField by remember { mutableStateOf<RecurringDateField?>(null) }
    val scope = rememberCoroutineScope()
    val clearIssue = { issue = null }
    val firstOccurrence = LocalDate.ofEpochDay(firstOccurrenceEpochDay)
    val firstPeriodEndsOn = firstPeriodEndsOnEpochDay?.let(LocalDate::ofEpochDay)
    val endsOn = endsOnEpochDay?.let(LocalDate::ofEpochDay)
    val remindOn = remindOnEpochDay?.let(LocalDate::ofEpochDay)

    BackHandler(enabled = !saving, onBack = onBack)
    RecurringItemEditorScreen(
        seed = seed,
        nameInput = nameInput,
        amountInput = amountInput,
        accountId = accountIdValue?.let(::AccountId),
        direction = direction,
        timingChoice = timingChoice,
        firstOccurrenceLabel =
            if (timingChoice == RecurringTimingChoice.ANY_TIME_IN_MONTH) {
                scheduleAdapter.formatMonth(java.time.YearMonth.from(firstOccurrence))
            } else {
                scheduleAdapter.formatDate(firstOccurrence)
            },
        firstPeriodEndsOnLabel = firstPeriodEndsOn?.let(scheduleAdapter::formatDate),
        repeatEveryInput = repeatEveryInput,
        repeatUnit = repeatUnit,
        countsToward = countsToward,
        requireManualConfirmation = requireManualConfirmation,
        includeExpectedIncome = includeExpectedIncome,
        endsOnLabel = endsOn?.let(scheduleAdapter::formatDate),
        remindOnLabel = remindOn?.let(scheduleAdapter::formatDate),
        occurrenceReminder = RecurringReminderInput(occurrenceReminderEnabled, occurrenceReminderInput),
        remindReminder = RecurringReminderInput(remindReminderEnabled, remindReminderInput),
        endReminder = RecurringReminderInput(endReminderEnabled, endReminderInput),
        notificationsAvailable = notificationsAvailable,
        saving = saving,
        issue = issue,
        categoryId = categoryIdValue?.let(::CategoryId),
        categoryManager = categoryManager,
        tags = tags,
        onNameChange = {
            nameInput = it
            clearIssue()
        },
        onAmountChange = {
            amountInput = it
            clearIssue()
        },
        onAccountChange = {
            accountIdValue = it.value
            clearIssue()
        },
        onDirectionChange = {
            direction = it
            clearIssue()
        },
        onTimingChoiceChange = { choice ->
            timingChoice = choice
            if (choice == RecurringTimingChoice.DATE_RANGE && firstPeriodEndsOnEpochDay == null) {
                firstPeriodEndsOnEpochDay = firstOccurrenceEpochDay
            }
            if (choice == RecurringTimingChoice.ANY_TIME_IN_MONTH) {
                val periodOffset = firstPeriodEndsOnEpochDay?.minus(firstOccurrenceEpochDay)
                firstOccurrenceEpochDay =
                    java.time.YearMonth
                        .from(firstOccurrence)
                        .atDay(1)
                        .toEpochDay()
                if (periodOffset != null) {
                    firstPeriodEndsOnEpochDay = firstOccurrenceEpochDay + periodOffset
                }
            }
            clearIssue()
        },
        onSelectFirstOccurrence = { selectedDateField = RecurringDateField.FIRST_OCCURRENCE },
        onSelectFirstPeriodEndsOn = { selectedDateField = RecurringDateField.FIRST_PERIOD_ENDS_ON },
        onRepeatEveryChange = {
            repeatEveryInput = it.filter(Char::isDigit).take(3)
            clearIssue()
        },
        onRepeatUnitChange = {
            repeatUnit = it
            clearIssue()
        },
        onCountsTowardChange = {
            countsToward = it
            clearIssue()
        },
        onRequireManualConfirmationChange = {
            requireManualConfirmation = it
            clearIssue()
        },
        onIncludeExpectedIncomeChange = {
            includeExpectedIncome = it
            clearIssue()
        },
        onSelectEndsOn = { selectedDateField = RecurringDateField.ENDS_ON },
        onClearEndsOn = {
            endsOnEpochDay = null
            endReminderEnabled = false
            clearIssue()
        },
        onSelectRemindOn = { selectedDateField = RecurringDateField.REMIND_ON },
        onClearRemindOn = {
            remindOnEpochDay = null
            remindReminderEnabled = false
            clearIssue()
        },
        onOccurrenceReminderEnabledChange = {
            occurrenceReminderEnabled = it
            if (it) onEnableNotifications()
            clearIssue()
        },
        onOccurrenceReminderDaysChange = {
            occurrenceReminderInput = it.filter(Char::isDigit).take(4)
            clearIssue()
        },
        onRemindReminderEnabledChange = {
            remindReminderEnabled = it
            if (it) onEnableNotifications()
            clearIssue()
        },
        onRemindReminderDaysChange = {
            remindReminderInput = it.filter(Char::isDigit).take(4)
            clearIssue()
        },
        onEndReminderEnabledChange = {
            endReminderEnabled = it
            if (it) onEnableNotifications()
            clearIssue()
        },
        onEndReminderDaysChange = {
            endReminderInput = it.filter(Char::isDigit).take(4)
            clearIssue()
        },
        onCategoryChange = {
            categoryIdValue = it?.value
            clearIssue()
        },
        onTagsChange = {
            tags = it
            clearIssue()
        },
        onOpenNotificationSettings = onOpenNotificationSettings,
        onSave = {
            saving = true
            issue = null
            scope.launch {
                val result =
                    editor.save(
                        seed = seed,
                        nameInput = nameInput,
                        amountInput = amountInput,
                        accountId = accountIdValue?.let(::AccountId),
                        direction = direction,
                        firstOccurrence = firstOccurrence,
                        timingChoice = timingChoice,
                        firstPeriodEndsOn = firstPeriodEndsOn,
                        repeatEveryInput = repeatEveryInput,
                        repeatUnit = repeatUnit,
                        countsToward = countsToward,
                        requireManualConfirmation = requireManualConfirmation,
                        includeExpectedIncome = includeExpectedIncome,
                        endsOn = endsOn,
                        remindOn = remindOn,
                        occurrenceReminder =
                            RecurringReminderInput(
                                enabled = occurrenceReminderEnabled,
                                daysBeforeInput = occurrenceReminderInput,
                            ),
                        remindReminder =
                            RecurringReminderInput(
                                enabled = remindReminderEnabled,
                                daysBeforeInput = remindReminderInput,
                            ),
                        endReminder =
                            RecurringReminderInput(
                                enabled = endReminderEnabled,
                                daysBeforeInput = endReminderInput,
                            ),
                        categoryId = categoryIdValue?.let(::CategoryId),
                        tags = tags,
                    )
                if (result == RecurringItemMutationResult.Saved) {
                    onChanged()
                } else {
                    issue = result
                    saving = false
                }
            }
        },
        onDelete = { showDeleteConfirmation = true },
        onBack = onBack,
    )

    selectedDateField?.let { field ->
        RecurringDatePickerDialog(
            initialDate =
                when (field) {
                    RecurringDateField.FIRST_OCCURRENCE -> firstOccurrence
                    RecurringDateField.FIRST_PERIOD_ENDS_ON -> firstPeriodEndsOn ?: firstOccurrence
                    RecurringDateField.ENDS_ON -> endsOn ?: firstOccurrence
                    RecurringDateField.REMIND_ON -> remindOn ?: firstOccurrence
                },
            onDismiss = { selectedDateField = null },
            onDateSelected = { date ->
                when (field) {
                    RecurringDateField.FIRST_OCCURRENCE -> {
                        val periodOffset = firstPeriodEndsOnEpochDay?.minus(firstOccurrenceEpochDay)
                        val selectedDate =
                            if (timingChoice == RecurringTimingChoice.ANY_TIME_IN_MONTH) {
                                java.time.YearMonth
                                    .from(date)
                                    .atDay(1)
                            } else {
                                date
                            }
                        firstOccurrenceEpochDay = selectedDate.toEpochDay()
                        if (periodOffset != null) {
                            firstPeriodEndsOnEpochDay = selectedDate.toEpochDay() + periodOffset
                        }
                    }

                    RecurringDateField.FIRST_PERIOD_ENDS_ON -> firstPeriodEndsOnEpochDay = date.toEpochDay()
                    RecurringDateField.ENDS_ON -> endsOnEpochDay = date.toEpochDay()
                    RecurringDateField.REMIND_ON -> remindOnEpochDay = date.toEpochDay()
                }
                selectedDateField = null
                clearIssue()
            },
        )
    }

    if (showDeleteConfirmation && seed.item != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        saving = true
                        scope.launch {
                            val result = editor.delete(seed.item.id)
                            if (result == RecurringItemMutationResult.Deleted) {
                                onChanged()
                            } else {
                                issue = result
                                saving = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.recurring_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.activity_delete_cancel))
                }
            },
        )
    }
}

@Suppress("LongParameterList")
@Composable
internal fun RecurringItemEditorScreen(
    seed: RecurringItemFormSeed,
    nameInput: String,
    amountInput: String,
    accountId: AccountId? = seed.accountId,
    direction: Direction,
    timingChoice: RecurringTimingChoice = RecurringTimingChoice.SPECIFIC_DATE,
    firstOccurrenceLabel: String,
    firstPeriodEndsOnLabel: String? = null,
    repeatEveryInput: String,
    repeatUnit: RecurrenceUnit,
    countsToward: BudgetMonthAssignment,
    requireManualConfirmation: Boolean = false,
    includeExpectedIncome: Boolean = false,
    endsOnLabel: String?,
    remindOnLabel: String?,
    occurrenceReminder: RecurringReminderInput,
    remindReminder: RecurringReminderInput,
    endReminder: RecurringReminderInput,
    notificationsAvailable: Boolean,
    saving: Boolean,
    issue: RecurringItemMutationResult?,
    categoryId: CategoryId? = seed.categoryId,
    categoryManager: CategoryManager? = null,
    tags: Set<Tag> = seed.tags,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (AccountId) -> Unit = {},
    onDirectionChange: (Direction) -> Unit,
    onTimingChoiceChange: (RecurringTimingChoice) -> Unit = {},
    onSelectFirstOccurrence: () -> Unit,
    onSelectFirstPeriodEndsOn: () -> Unit = {},
    onRepeatEveryChange: (String) -> Unit,
    onRepeatUnitChange: (RecurrenceUnit) -> Unit,
    onCountsTowardChange: (BudgetMonthAssignment) -> Unit,
    onRequireManualConfirmationChange: (Boolean) -> Unit = {},
    onIncludeExpectedIncomeChange: (Boolean) -> Unit = {},
    onSelectEndsOn: () -> Unit,
    onClearEndsOn: () -> Unit,
    onSelectRemindOn: () -> Unit,
    onClearRemindOn: () -> Unit,
    onOccurrenceReminderEnabledChange: (Boolean) -> Unit,
    onOccurrenceReminderDaysChange: (String) -> Unit,
    onRemindReminderEnabledChange: (Boolean) -> Unit,
    onRemindReminderDaysChange: (String) -> Unit,
    onEndReminderEnabledChange: (Boolean) -> Unit,
    onEndReminderDaysChange: (String) -> Unit,
    onCategoryChange: (CategoryId?) -> Unit = {},
    onTagsChange: (Set<Tag>) -> Unit = {},
    onOpenNotificationSettings: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
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
            TextButton(onClick = onBack, enabled = !saving) {
                Text(stringResource(R.string.entry_back))
            }
            Text(
                text = stringResource(if (seed.isEditing) R.string.recurring_edit_title else R.string.recurring_add_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                label = { Text(stringResource(R.string.recurring_name)) },
                singleLine = true,
                isError = issue == RecurringItemMutationResult.InvalidName,
            )
            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                label = { Text(stringResource(R.string.entry_amount)) },
                supportingText = { Text(stringResource(R.string.activity_currency, seed.currencyCode.value)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError =
                    issue == RecurringItemMutationResult.InvalidAmount ||
                        issue == RecurringItemMutationResult.AmountMustBePositive,
            )
            AccountSelector(
                accounts = seed.accounts,
                selectedAccountId = accountId,
                enabled = !saving,
                onAccountChange = onAccountChange,
            )
            if (direction == Direction.INCOME) {
                InfoHeading(
                    title = stringResource(R.string.activity_direction),
                    help = stringResource(R.string.recurring_expected_income_note),
                )
            } else {
                RecurringSectionHeading(stringResource(R.string.activity_direction))
            }
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
                title = stringResource(R.string.recurring_schedule),
                help = stringResource(R.string.recurring_timing_help),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimingChoiceChip(
                    selected = timingChoice == RecurringTimingChoice.SPECIFIC_DATE,
                    label = stringResource(R.string.recurring_specific_date_short),
                    enabled = !saving,
                    onClick = { onTimingChoiceChange(RecurringTimingChoice.SPECIFIC_DATE) },
                    modifier = Modifier.weight(1f),
                )
                TimingChoiceChip(
                    selected = timingChoice == RecurringTimingChoice.DATE_RANGE,
                    label = stringResource(R.string.recurring_date_range_short),
                    enabled = !saving,
                    onClick = { onTimingChoiceChange(RecurringTimingChoice.DATE_RANGE) },
                    modifier = Modifier.weight(1f),
                )
                TimingChoiceChip(
                    selected = timingChoice == RecurringTimingChoice.ANY_TIME_IN_MONTH,
                    label = stringResource(R.string.recurring_any_time_in_month_short),
                    enabled = !saving,
                    onClick = { onTimingChoiceChange(RecurringTimingChoice.ANY_TIME_IN_MONTH) },
                    modifier = Modifier.weight(1f),
                )
            }
            RecurringScheduleDateControls(
                firstOccurrenceLabel = firstOccurrenceLabel,
                timingChoice = timingChoice,
                firstPeriodEndsOnLabel = firstPeriodEndsOnLabel,
                enabled = !saving,
                issue = issue,
                intervalContent = {
                    RepeatIntervalControls(
                        repeatEveryInput = repeatEveryInput,
                        repeatUnit = repeatUnit,
                        enabled = !saving,
                        isError = issue == RecurringItemMutationResult.InvalidSchedule,
                        onRepeatEveryChange = onRepeatEveryChange,
                        onRepeatUnitChange = onRepeatUnitChange,
                    )
                },
                onSelectFirstOccurrence = onSelectFirstOccurrence,
                onSelectFirstPeriodEndsOn = onSelectFirstPeriodEndsOn,
            )
            InfoHeading(
                title = stringResource(R.string.recurring_counts_toward),
                help = stringResource(R.string.recurring_counts_toward_help),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimingChoiceChip(
                    selected = countsToward == BudgetMonthAssignment.OCCURRENCE_MONTH,
                    label = stringResource(R.string.recurring_occurrence_month),
                    enabled = !saving,
                    onClick = { onCountsTowardChange(BudgetMonthAssignment.OCCURRENCE_MONTH) },
                    modifier = Modifier.weight(1f),
                )
                TimingChoiceChip(
                    selected = countsToward == BudgetMonthAssignment.FOLLOWING_MONTH,
                    label = stringResource(R.string.recurring_following_month),
                    enabled = !saving,
                    onClick = { onCountsTowardChange(BudgetMonthAssignment.FOLLOWING_MONTH) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (direction == Direction.INCOME) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.recurring_include_expected_income),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.recurring_include_expected_income_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = includeExpectedIncome,
                        onCheckedChange = onIncludeExpectedIncomeChange,
                        enabled = !saving,
                    )
                }
            }
            RecurringConfirmationControl(
                requireManualConfirmation = requireManualConfirmation,
                enabled = !saving,
                onRequireManualConfirmationChange = onRequireManualConfirmationChange,
            )
            var endDateExpanded by rememberSaveable { mutableStateOf(false) }
            var remindersExpanded by rememberSaveable { mutableStateOf(false) }
            var organizationExpanded by rememberSaveable { mutableStateOf(false) }

            RecurringExpandableSection(
                title = stringResource(R.string.recurring_end_date_section),
                summary =
                    endsOnLabel?.let { stringResource(R.string.recurring_ends_on_value, it) }
                        ?: stringResource(R.string.recurring_no_end_date),
                expanded = endDateExpanded,
                onExpandedChange = { endDateExpanded = it },
                testTag = "recurring_end_date_section",
            ) {
                RecurringEndDateControl(
                    endsOnLabel = endsOnLabel,
                    enabled = !saving,
                    onSelectEndsOn = onSelectEndsOn,
                    onClearEndsOn = onClearEndsOn,
                )
            }
            RecurringExpandableSection(
                title = stringResource(R.string.recurring_reminders),
                summary =
                    if (
                        remindOnLabel != null ||
                        occurrenceReminder.enabled ||
                        remindReminder.enabled ||
                        endReminder.enabled
                    ) {
                        stringResource(R.string.section_configured)
                    } else {
                        stringResource(R.string.recurring_no_reminders)
                    },
                expanded = remindersExpanded,
                onExpandedChange = { remindersExpanded = it },
                testTag = "recurring_reminders_section",
            ) {
                RecurringReminderControls(
                    remindOnLabel = remindOnLabel,
                    occurrence = occurrenceReminder,
                    remind = remindReminder,
                    end = endReminder,
                    notificationsAvailable = notificationsAvailable,
                    remindDateIsSet = remindOnLabel != null,
                    endDateIsSet = endsOnLabel != null,
                    enabled = !saving,
                    issue = issue,
                    showHeading = false,
                    onSelectRemindOn = onSelectRemindOn,
                    onClearRemindOn = onClearRemindOn,
                    onOccurrenceEnabledChange = onOccurrenceReminderEnabledChange,
                    onOccurrenceDaysChange = onOccurrenceReminderDaysChange,
                    onRemindEnabledChange = onRemindReminderEnabledChange,
                    onRemindDaysChange = onRemindReminderDaysChange,
                    onEndEnabledChange = onEndReminderEnabledChange,
                    onEndDaysChange = onEndReminderDaysChange,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
            }
            RecurringExpandableSection(
                title = stringResource(R.string.recurring_organization_section),
                summary =
                    if (categoryId != null || tags.isNotEmpty()) {
                        stringResource(R.string.section_configured)
                    } else {
                        stringResource(R.string.recurring_organization_none)
                    },
                expanded = organizationExpanded,
                onExpandedChange = { organizationExpanded = it },
                testTag = "recurring_organization_section",
            ) {
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
            }
            RecurringIssueMessage(issue)
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.recurring_save))
                }
            }
            if (seed.isEditing) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.recurring_delete))
                }
            }
        }
    }
}

@Composable
private fun RecurringExpandableSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Surface(
                onClick = { onExpandedChange(!expanded) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag(testTag),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector =
                            if (expanded) {
                                Icons.Rounded.ExpandLess
                            } else {
                                Icons.Rounded.ExpandMore
                            },
                        contentDescription =
                            stringResource(
                                if (expanded) {
                                    R.string.section_collapse
                                } else {
                                    R.string.section_expand
                                },
                                title,
                            ),
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun RepeatIntervalControls(
    repeatEveryInput: String,
    repeatUnit: RecurrenceUnit,
    enabled: Boolean,
    isError: Boolean,
    onRepeatEveryChange: (String) -> Unit,
    onRepeatUnitChange: (RecurrenceUnit) -> Unit,
) {
    OutlinedTextField(
        value = repeatEveryInput,
        onValueChange = onRepeatEveryChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.recurring_repeat_every)) },
        supportingText =
            if (isError) {
                { Text(stringResource(R.string.recurring_repeat_every_help)) }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RepeatUnitChip(RecurrenceUnit.DAYS, repeatUnit, enabled, onRepeatUnitChange, Modifier.weight(1f))
        RepeatUnitChip(RecurrenceUnit.WEEKS, repeatUnit, enabled, onRepeatUnitChange, Modifier.weight(1f))
        RepeatUnitChip(RecurrenceUnit.MONTHS, repeatUnit, enabled, onRepeatUnitChange, Modifier.weight(1f))
        RepeatUnitChip(RecurrenceUnit.YEARS, repeatUnit, enabled, onRepeatUnitChange, Modifier.weight(1f))
    }
}

@Composable
private fun RepeatUnitChip(
    unit: RecurrenceUnit,
    selectedUnit: RecurrenceUnit,
    enabled: Boolean,
    onSelect: (RecurrenceUnit) -> Unit,
    modifier: Modifier,
) {
    FilterChip(
        selected = unit == selectedUnit,
        onClick = { onSelect(unit) },
        label = { Text(recurrenceUnitLabel(unit)) },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun TimingChoiceChip(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun RecurringSectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RecurringIssueMessage(issue: RecurringItemMutationResult?) {
    val message =
        when (issue) {
            null,
            RecurringItemMutationResult.Saved,
            RecurringItemMutationResult.Deleted,
            -> null

            RecurringItemMutationResult.InvalidName -> stringResource(R.string.recurring_invalid_name)
            RecurringItemMutationResult.InvalidAmount -> stringResource(R.string.entry_invalid_amount)
            RecurringItemMutationResult.AmountMustBePositive -> stringResource(R.string.entry_amount_positive)
            RecurringItemMutationResult.InvalidSchedule -> null
            RecurringItemMutationResult.InvalidReminder -> stringResource(R.string.recurring_invalid_reminder)
            RecurringItemMutationResult.Missing -> stringResource(R.string.recurring_missing_body)
            RecurringItemMutationResult.StorageFailed -> stringResource(R.string.entry_save_failed)
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
private fun RecurringEditorMessageScreen(
    title: String,
    body: String?,
    onRetry: (() -> Unit)?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
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
                    ).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.entry_back))
            }
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CircularProgressIndicator()
            }
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.home_try_again))
                }
            }
        }
    }
}

@Composable
private fun RecurringMessageCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun recurringScheduleLabel(item: RecurringListItemUi): String {
    val every = item.schedule.interval.every
    val unit =
        when (item.schedule.interval.unit) {
            RecurrenceUnit.DAYS -> pluralStringResource(R.plurals.recurring_days, every, every)
            RecurrenceUnit.WEEKS -> pluralStringResource(R.plurals.recurring_weeks, every, every)
            RecurrenceUnit.MONTHS -> pluralStringResource(R.plurals.recurring_months, every, every)
            RecurrenceUnit.YEARS -> pluralStringResource(R.plurals.recurring_years, every, every)
        }
    return stringResource(R.string.recurring_schedule_summary, unit, item.firstOccurrenceLabel)
}

@Composable
private fun recurrenceUnitLabel(unit: RecurrenceUnit): String =
    when (unit) {
        RecurrenceUnit.DAYS -> stringResource(R.string.recurring_unit_days)
        RecurrenceUnit.WEEKS -> stringResource(R.string.recurring_unit_weeks)
        RecurrenceUnit.MONTHS -> stringResource(R.string.recurring_unit_months)
        RecurrenceUnit.YEARS -> stringResource(R.string.recurring_unit_years)
    }
