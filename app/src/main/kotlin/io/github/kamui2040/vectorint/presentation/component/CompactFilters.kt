package io.github.kamui2040.vectorint.presentation.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R

internal data class ActiveFilterUi(
    val key: String,
    val label: String,
    val onRemove: () -> Unit,
)

@Composable
internal fun CompactFilterBar(
    activeFilters: List<ActiveFilterUi>,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                focusManager.clearFocus()
                onOpenFilters()
            },
            modifier = Modifier.testTag("open_filters"),
        ) {
            Text(
                if (activeFilters.isEmpty()) {
                    stringResource(R.string.filters)
                } else {
                    stringResource(R.string.filters_count, activeFilters.size)
                },
            )
        }
        activeFilters.forEach { filter ->
            FilterChip(
                selected = true,
                onClick = filter.onRemove,
                modifier = Modifier.testTag("active_filter:${filter.key}"),
                label = { Text(filter.label) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
internal fun <T> FilterOptionRow(
    label: String,
    tagPrefix: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (option, optionLabel) ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier =
                    Modifier
                        .testTag("$tagPrefix:$option")
                        .semantics { contentDescription = "$label: $optionLabel" },
                label = { Text(optionLabel) },
            )
        }
    }
}

@Composable
internal fun FilterSheetActions(
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.filters_reset))
        }
        Button(onClick = onApply) {
            Text(stringResource(R.string.filters_apply))
        }
    }
}
