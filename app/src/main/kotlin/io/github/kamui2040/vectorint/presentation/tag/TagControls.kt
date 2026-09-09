package io.github.kamui2040.vectorint.presentation.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import java.util.Locale

internal fun tagFromInput(input: String): Tag? =
    input
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let(::Tag)

internal fun sortedTags(tags: Set<Tag>): List<Tag> =
    tags.sortedWith(
        compareBy<Tag>(
            { it.value.lowercase(Locale.ROOT) },
            { it.value },
        ),
    )

private val tagStateSaver =
    Saver<MutableState<Set<Tag>>, ArrayList<String>>(
        save = { state -> ArrayList(sortedTags(state.value).map(Tag::value)) },
        restore = { values -> mutableStateOf(values.map(::Tag).toSet()) },
    )

@Composable
internal fun rememberTagState(initialTags: Set<Tag>): MutableState<Set<Tag>> =
    rememberSaveable(saver = tagStateSaver) { mutableStateOf(initialTags) }

@Composable
internal fun TagEditor(
    tags: Set<Tag>,
    enabled: Boolean,
    onTagsChange: (Set<Tag>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }

    fun addCurrentInput() {
        val tag = tagFromInput(input) ?: return
        onTagsChange(tags + tag)
        input = ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InfoHeading(
            title = stringResource(R.string.tags_title),
            help = stringResource(R.string.tags_explanation),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                label = { Text(stringResource(R.string.tags_add_label)) },
                singleLine = true,
            )
            Button(
                onClick = ::addCurrentInput,
                enabled = enabled && tagFromInput(input) != null,
            ) {
                Text(stringResource(R.string.tags_add))
            }
        }
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortedTags(tags).forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text(tag.value) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TagSummary(tags: List<String>) {
    if (tags.isNotEmpty()) {
        Text(
            text = stringResource(R.string.tags_summary, tags.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
