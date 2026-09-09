package io.github.kamui2040.vectorint.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.presentation.component.InfoHeading
import kotlinx.coroutines.launch

@Composable
internal fun CategorySelector(
    selectedCategoryId: CategoryId?,
    enabled: Boolean,
    manager: CategoryManager,
    onCategoryChange: (CategoryId?) -> Unit,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val library by produceState<CategoryLibraryLoadResult?>(null, reloadKey) {
        value = manager.load()
    }
    val customCategories = (library as? CategoryLibraryLoadResult.Ready)?.customCategories.orEmpty()
    val selection = categoryInfo(selectedCategoryId, customCategories)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoHeading(
            title = stringResource(R.string.category_section),
            help = stringResource(R.string.category_other_help),
        )
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && library is CategoryLibraryLoadResult.Ready,
        ) {
            Icon(selection.icon.imageVector(), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(selection.name, modifier = Modifier.padding(start = 12.dp))
        }
        if (library == CategoryLibraryLoadResult.Failed) {
            Text(
                text = stringResource(R.string.category_load_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPicker && library is CategoryLibraryLoadResult.Ready) {
        CategoryPickerDialog(
            selectedCategoryId = selectedCategoryId,
            customCategories = customCategories,
            manager = manager,
            onSelect = {
                onCategoryChange(it)
                showPicker = false
            },
            onLibraryChanged = {
                onCategoryChange(it)
                reloadKey++
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun CategoryOptionRow(
    info: CategoryInfo,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(info.icon.imageVector(), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(info.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (selected) {
                Text(stringResource(R.string.category_selected), style = MaterialTheme.typography.labelMedium)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.category_delete, info.name),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomCategoryForm(
    nameInput: String,
    selectedIcon: CategoryIcon,
    busy: Boolean,
    issue: CategoryMutationResult?,
    onNameChange: (String) -> Unit,
    onIconChange: (CategoryIcon) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    TextButton(onClick = onBack, enabled = !busy) {
        Text(stringResource(R.string.entry_back))
    }
    Text(
        text = stringResource(R.string.category_add_custom),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    OutlinedTextField(
        value = nameInput,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        label = { Text(stringResource(R.string.category_custom_name)) },
        singleLine = true,
        isError = issue == CategoryMutationResult.InvalidName || issue == CategoryMutationResult.DuplicateName,
    )
    Text(stringResource(R.string.category_choose_icon), fontWeight = FontWeight.SemiBold)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        CategoryIcon.entries.forEach { icon ->
            val label = stringResource(icon.labelResource())
            Surface(
                onClick = { onIconChange(icon) },
                modifier = Modifier.width(96.dp),
                enabled = !busy,
                shape = MaterialTheme.shapes.medium,
                color =
                    if (selectedIcon ==
                        icon
                    ) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(icon.imageVector(), contentDescription = label)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    categoryIssueText(issue)?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
        Text(stringResource(R.string.category_create))
    }
}

@Composable
private fun categoryIssueText(issue: CategoryMutationResult?): String? =
    when (issue) {
        CategoryMutationResult.InvalidName -> stringResource(R.string.category_invalid_name)
        CategoryMutationResult.DuplicateName -> stringResource(R.string.category_duplicate_name)
        CategoryMutationResult.Missing,
        CategoryMutationResult.StorageFailed,
        -> stringResource(R.string.category_storage_failed)

        CategoryMutationResult.Deleted,
        is CategoryMutationResult.Created,
        null,
        -> null
    }

@Composable
private fun DeleteCategoryDialog(
    category: CustomCategory,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_delete_title)) },
        text = { Text("${category.name}\n\n${stringResource(R.string.category_delete_body)}") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.category_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.activity_delete_cancel))
            }
        },
    )
}

@Composable
private fun CategoryLibraryList(
    selectedCategoryId: CategoryId?,
    customCategories: List<CustomCategory>,
    enabled: Boolean,
    onSelect: (CategoryId?) -> Unit,
    onAdd: () -> Unit,
    onDelete: (CustomCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = stringResource(R.string.category_choose),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    CategoryOptionRow(
        info = categoryInfo(null, customCategories),
        selected = selectedCategoryId == null,
        enabled = enabled,
        onClick = { onSelect(null) },
    )
    Text(stringResource(R.string.category_common), fontWeight = FontWeight.SemiBold)
    PredefinedCategory.entries.forEach { category ->
        CategoryOptionRow(
            info = categoryInfo(category.id, customCategories),
            selected = selectedCategoryId == category.id,
            enabled = enabled,
            onClick = { onSelect(category.id) },
        )
    }
    Text(stringResource(R.string.category_custom), fontWeight = FontWeight.SemiBold)
    customCategories.forEach { category ->
        CategoryOptionRow(
            info = categoryInfo(category.id, customCategories),
            selected = selectedCategoryId == category.id,
            enabled = enabled,
            onClick = { onSelect(category.id) },
            onDelete = { onDelete(category) },
        )
    }
    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Text(stringResource(R.string.category_add_custom))
    }
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Text(stringResource(R.string.settings_close))
    }
}

@Composable
private fun CategoryPickerDialog(
    selectedCategoryId: CategoryId?,
    customCategories: List<CustomCategory>,
    manager: CategoryManager,
    onSelect: (CategoryId?) -> Unit,
    onLibraryChanged: (CategoryId?) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var selectedIconName by rememberSaveable { mutableStateOf(CategoryIcon.OTHER.name) }
    var issue by remember { mutableStateOf<CategoryMutationResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CustomCategory?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (adding) {
                        CustomCategoryForm(
                            nameInput = nameInput,
                            selectedIcon = CategoryIcon.valueOf(selectedIconName),
                            busy = busy,
                            issue = issue,
                            onNameChange = {
                                nameInput = it.take(CustomCategory.MAX_NAME_LENGTH)
                                issue = null
                            },
                            onIconChange = {
                                selectedIconName = it.name
                                issue = null
                            },
                            onCreate = {
                                busy = true
                                scope.launch {
                                    when (val result = manager.create(nameInput, CategoryIcon.valueOf(selectedIconName))) {
                                        is CategoryMutationResult.Created -> onLibraryChanged(result.category.id)
                                        else -> {
                                            issue = result
                                            busy = false
                                        }
                                    }
                                }
                            },
                            onBack = {
                                adding = false
                                issue = null
                            },
                        )
                    } else {
                        CategoryLibraryList(
                            selectedCategoryId = selectedCategoryId,
                            customCategories = customCategories,
                            enabled = !busy,
                            onSelect = onSelect,
                            onAdd = { adding = true },
                            onDelete = { pendingDelete = it },
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { category ->
        DeleteCategoryDialog(
            category = category,
            onConfirm = {
                busy = true
                scope.launch {
                    val result = manager.delete(category.id)
                    if (result == CategoryMutationResult.Deleted) {
                        onLibraryChanged(selectedCategoryId.takeUnless { it == category.id })
                    } else {
                        issue = result
                        pendingDelete = null
                        busy = false
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
