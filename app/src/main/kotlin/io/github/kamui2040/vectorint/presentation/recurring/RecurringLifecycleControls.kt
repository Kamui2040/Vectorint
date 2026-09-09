package io.github.kamui2040.vectorint.presentation.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.presentation.component.ContextualHelpButton
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import java.time.LocalDate

internal enum class RecurringDateField {
    FIRST_OCCURRENCE,
    FIRST_PERIOD_ENDS_ON,
    ENDS_ON,
    REMIND_ON,
}

@Composable
internal fun RecurringScheduleDateControls(
    firstOccurrenceLabel: String,
    timingChoice: RecurringTimingChoice,
    firstPeriodEndsOnLabel: String?,
    endsOnLabel: String?,
    enabled: Boolean,
    issue: RecurringItemMutationResult?,
    intervalContent: @Composable () -> Unit,
    onSelectFirstOccurrence: () -> Unit,
    onSelectFirstPeriodEndsOn: () -> Unit,
    onSelectEndsOn: () -> Unit,
    onClearEndsOn: () -> Unit,
) {
    OutlinedButton(
        onClick = onSelectFirstOccurrence,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(
            stringResource(
                when (timingChoice) {
                    RecurringTimingChoice.SPECIFIC_DATE -> R.string.recurring_first_occurrence_value
                    RecurringTimingChoice.DATE_RANGE -> R.string.recurring_first_period_starts_value
                    RecurringTimingChoice.ANY_TIME_IN_MONTH -> R.string.recurring_first_month_value
                },
                firstOccurrenceLabel,
            ),
        )
    }
    if (timingChoice == RecurringTimingChoice.DATE_RANGE) {
        OutlinedButton(
            onClick = onSelectFirstPeriodEndsOn,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        ) {
            Text(
                firstPeriodEndsOnLabel?.let { stringResource(R.string.recurring_first_period_ends_value, it) }
                    ?: stringResource(R.string.recurring_set_first_period_end),
            )
        }
    }
    intervalContent()
    OptionalDateControl(
        label =
            endsOnLabel?.let { stringResource(R.string.recurring_ends_on_value, it) }
                ?: stringResource(R.string.recurring_set_ends_on),
        clearLabel = stringResource(R.string.recurring_clear_ends_on),
        valueIsSet = endsOnLabel != null,
        enabled = enabled,
        help = stringResource(R.string.recurring_ends_on_explanation),
        onSelect = onSelectEndsOn,
        onClear = onClearEndsOn,
    )
    if (issue == RecurringItemMutationResult.InvalidSchedule) {
        Text(
            text = stringResource(R.string.recurring_invalid_schedule),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun RecurringReminderControls(
    remindOnLabel: String?,
    occurrence: RecurringReminderInput,
    remind: RecurringReminderInput,
    end: RecurringReminderInput,
    notificationsAvailable: Boolean,
    remindDateIsSet: Boolean,
    endDateIsSet: Boolean,
    enabled: Boolean,
    issue: RecurringItemMutationResult?,
    onSelectRemindOn: () -> Unit,
    onClearRemindOn: () -> Unit,
    onOccurrenceEnabledChange: (Boolean) -> Unit,
    onOccurrenceDaysChange: (String) -> Unit,
    onRemindEnabledChange: (Boolean) -> Unit,
    onRemindDaysChange: (String) -> Unit,
    onEndEnabledChange: (Boolean) -> Unit,
    onEndDaysChange: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val anyReminderEnabled = occurrence.enabled || remind.enabled || end.enabled
    InfoHeading(
        title = stringResource(R.string.recurring_reminders),
        help = stringResource(R.string.recurring_reminders_delivery),
    )
    OptionalDateControl(
        label =
            remindOnLabel?.let { stringResource(R.string.recurring_remind_on_value, it) }
                ?: stringResource(R.string.recurring_set_remind_on),
        clearLabel = stringResource(R.string.recurring_clear_remind_on),
        valueIsSet = remindOnLabel != null,
        enabled = enabled,
        help = stringResource(R.string.recurring_remind_on_explanation),
        onSelect = onSelectRemindOn,
        onClear = onClearRemindOn,
    )
    if (anyReminderEnabled && !notificationsAvailable) {
        Text(
            text = stringResource(R.string.recurring_reminders_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onOpenNotificationSettings, enabled = enabled) {
            Text(stringResource(R.string.recurring_open_notification_settings))
        }
    }
    ReminderControl(
        title = stringResource(R.string.recurring_occurrence_reminder),
        input = occurrence,
        enabled = enabled,
        isError = issue == RecurringItemMutationResult.InvalidReminder,
        onEnabledChange = onOccurrenceEnabledChange,
        onDaysChange = onOccurrenceDaysChange,
    )
    if (remindDateIsSet) {
        ReminderControl(
            title = stringResource(R.string.recurring_remind_date_reminder),
            input = remind,
            enabled = enabled,
            isError = issue == RecurringItemMutationResult.InvalidReminder,
            onEnabledChange = onRemindEnabledChange,
            onDaysChange = onRemindDaysChange,
        )
    }
    if (endDateIsSet) {
        ReminderControl(
            title = stringResource(R.string.recurring_end_reminder),
            input = end,
            enabled = enabled,
            isError = issue == RecurringItemMutationResult.InvalidReminder,
            onEnabledChange = onEndEnabledChange,
            onDaysChange = onEndDaysChange,
        )
    }
}

@Composable
internal fun RecurringConfirmationControl(
    requireManualConfirmation: Boolean,
    enabled: Boolean,
    onRequireManualConfirmationChange: (Boolean) -> Unit,
) {
    val manualConfirmationLabel = stringResource(R.string.recurring_require_manual_confirmation)
    InfoHeading(
        title = stringResource(R.string.recurring_confirmation),
        help =
            stringResource(
                if (requireManualConfirmation) {
                    R.string.recurring_manual_confirmation_on_help
                } else {
                    R.string.recurring_manual_confirmation_off_help
                },
            ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = manualConfirmationLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = requireManualConfirmation,
            onCheckedChange = onRequireManualConfirmationChange,
            modifier =
                Modifier.semantics {
                    contentDescription = manualConfirmationLabel
                },
            enabled = enabled,
        )
    }
}

@Composable
private fun OptionalDateControl(
    label: String,
    clearLabel: String,
    valueIsSet: Boolean,
    enabled: Boolean,
    help: String,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSelect,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text(label)
            }
            ContextualHelpButton(title = label, help = help)
        }
        if (valueIsSet) {
            TextButton(
                onClick = onClear,
                enabled = enabled,
            ) {
                Text(clearLabel)
            }
        }
    }
}

@Composable
private fun ReminderControl(
    title: String,
    input: RecurringReminderInput,
    enabled: Boolean,
    isError: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDaysChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = input.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics { contentDescription = title },
                enabled = enabled,
            )
        }
        if (input.enabled) {
            OutlinedTextField(
                value = input.daysBeforeInput,
                onValueChange = onDaysChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.recurring_days_before)) },
                supportingText = { Text(stringResource(R.string.recurring_days_before_help)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isError,
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    key(initialDate) {
        val state =
            androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = initialDate.toDatePickerMillis(),
            )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = state.selectedDateMillis ?: return@TextButton
                        onDateSelected(localDateFromDatePickerMillis(selected))
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
                state = state,
                showModeToggle = false,
            )
        }
    }
}

internal fun LocalDate.toDatePickerMillis(): Long = Math.multiplyExact(toEpochDay(), MILLIS_PER_DAY)

internal fun localDateFromDatePickerMillis(value: Long): LocalDate {
    require(Math.floorMod(value, MILLIS_PER_DAY) == 0L) { "Date picker value must be UTC midnight" }
    return LocalDate.ofEpochDay(Math.floorDiv(value, MILLIS_PER_DAY))
}

private const val MILLIS_PER_DAY = 86_400_000L
