package io.github.kamui2040.vectorint.presentation.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun AccountManagementRoute(
    manager: AccountManager,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var loadKey by remember { mutableIntStateOf(0) }
    val result by produceState<AccountLoadResult?>(initialValue = null, key1 = loadKey) { value = manager.load() }

    when (val current = result) {
        null -> AccountMessageScreen(title = stringResource(R.string.entry_loading), onBack = onBack)
        AccountLoadResult.Failed ->
            AccountMessageScreen(
                title = stringResource(R.string.account_load_failed),
                body = stringResource(R.string.entry_load_failed_body),
                onRetry = { loadKey++ },
                onBack = onBack,
            )

        is AccountLoadResult.Ready ->
            key(current.seed) {
                AccountManagementReadyRoute(
                    manager = manager,
                    seed = current.seed,
                    onReload = {
                        onChanged()
                        loadKey++
                    },
                    onBack = onBack,
                )
            }
    }
}

@Composable
private fun AccountManagementReadyRoute(
    manager: AccountManager,
    seed: AccountManagementSeed,
    onReload: () -> Unit,
    onBack: () -> Unit,
) {
    var editingIdValue by rememberSaveable { mutableStateOf<String?>(null) }
    var showForm by rememberSaveable { mutableStateOf(seed.accounts.isEmpty()) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var currencyInput by rememberSaveable { mutableStateOf(seed.currencyCode?.value.orEmpty()) }
    var included by rememberSaveable { mutableStateOf(true) }
    var issue by remember { mutableStateOf<AccountMutationResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingDeleteIdValue by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun closeForm() {
        showForm = false
        editingIdValue = null
        nameInput = ""
        amountInput = ""
        included = true
        issue = null
    }

    fun edit(item: AccountListItemUi) {
        editingIdValue = item.account.id.value
        nameInput = item.account.name
        amountInput = item.amountInput
        currencyInput = item.account.currentFunds.amount.currency.value
        included = item.account.includeInAvailableNow
        issue = null
        showForm = true
    }

    fun runMutation(block: suspend () -> AccountMutationResult) {
        busy = true
        issue = null
        scope.launch {
            when (val result = block()) {
                AccountMutationResult.Saved,
                AccountMutationResult.Deleted,
                -> {
                    closeForm()
                    onReload()
                }

                else -> {
                    issue = result
                    busy = false
                }
            }
        }
    }

    val backAction = if (showForm && seed.accounts.isNotEmpty()) ::closeForm else onBack
    BackHandler(enabled = !busy, onBack = backAction)
    AccountScaffold(
        screenTitle =
            stringResource(
                if (showForm) {
                    if (editingIdValue == null) R.string.account_setup_title else R.string.account_edit_title
                } else {
                    R.string.accounts_title
                },
            ),
        onBack = backAction,
        backEnabled = !busy,
    ) {
        if (showForm) {
            AccountForm(
                nameInput = nameInput,
                amountInput = amountInput,
                currencyInput = currencyInput,
                currencyLocked = seed.accounts.isNotEmpty(),
                included = included,
                editing = editingIdValue != null,
                busy = busy,
                issue = issue,
                onNameChange = {
                    nameInput = it.take(Account.MAX_NAME_LENGTH)
                    issue = null
                },
                onAmountChange = {
                    amountInput = it
                    issue = null
                },
                onCurrencyChange = {
                    currencyInput = it.uppercase(Locale.ROOT).take(3)
                    issue = null
                },
                onIncludedChange = {
                    included = it
                    issue = null
                },
                onSave = {
                    runMutation {
                        manager.save(
                            existingId = editingIdValue?.let(::AccountId),
                            nameInput = nameInput,
                            amountInput = amountInput,
                            currencyCodeInput = currencyInput,
                            includeInAvailableNow = included,
                        )
                    }
                },
            )
        } else {
            Text(
                text = stringResource(R.string.accounts_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.accounts_explanation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            seed.accounts.forEach { item ->
                AccountCard(
                    item = item,
                    enabled = !busy,
                    onIncludedChange = { value ->
                        runMutation { manager.setIncluded(item.account.id, value) }
                    },
                    onEdit = { edit(item) },
                    onDelete = { pendingDeleteIdValue = item.account.id.value },
                )
            }
            accountIssueMessage(issue)?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    currencyInput = seed.currencyCode?.value.orEmpty()
                    showForm = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(stringResource(R.string.account_add))
            }
        }
    }

    pendingDeleteIdValue?.let { accountIdValue ->
        val accountName =
            seed.accounts
                .singleOrNull { it.account.id.value == accountIdValue }
                ?.account
                ?.name
                .orEmpty()
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDeleteIdValue = null },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_body, accountName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteIdValue = null
                        runMutation { manager.delete(AccountId(accountIdValue)) }
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.account_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIdValue = null }, enabled = !busy) {
                    Text(stringResource(R.string.activity_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountForm(
    nameInput: String,
    amountInput: String,
    currencyInput: String,
    currencyLocked: Boolean,
    included: Boolean,
    editing: Boolean,
    busy: Boolean,
    issue: AccountMutationResult?,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onIncludedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        text = stringResource(if (editing) R.string.account_edit_title else R.string.account_setup_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.account_setup_explanation),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = nameInput,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        label = { Text(stringResource(R.string.account_name)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        isError = issue == AccountMutationResult.InvalidName || issue == AccountMutationResult.DuplicateName,
    )
    OutlinedTextField(
        value = amountInput,
        onValueChange = onAmountChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        label = { Text(stringResource(R.string.account_current_funds)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = issue == AccountMutationResult.InvalidAmount,
    )
    OutlinedTextField(
        value = currencyInput,
        onValueChange = onCurrencyChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        readOnly = currencyLocked,
        label = { Text(stringResource(R.string.entry_currency)) },
        supportingText = {
            Text(
                stringResource(
                    if (currencyLocked) R.string.account_currency_locked else R.string.current_funds_currency_help,
                ),
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        singleLine = true,
        isError = issue == AccountMutationResult.InvalidCurrency,
    )
    AccountInclusionRow(
        accountName = nameInput.ifBlank { stringResource(R.string.account_unnamed) },
        included = included,
        enabled = !busy,
        onIncludedChange = onIncludedChange,
    )
    accountIssueMessage(issue)?.let { message ->
        Text(
            text = message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.account_save))
        }
    }
}

@Composable
private fun AccountCard(
    item: AccountListItemUi,
    enabled: Boolean,
    onIncludedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            R.string.account_balance_value,
                            item.amountInput,
                            item.account.currentFunds.amount.currency.value,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.account_edit_named, item.account.name))
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.account_delete_named, item.account.name))
                }
            }
            AccountInclusionRow(
                accountName = item.account.name,
                included = item.account.includeInAvailableNow,
                enabled = enabled,
                onIncludedChange = onIncludedChange,
            )
        }
    }
}

@Composable
private fun AccountInclusionRow(
    accountName: String,
    included: Boolean,
    enabled: Boolean,
    onIncludedChange: (Boolean) -> Unit,
) {
    val switchLabel = stringResource(R.string.account_include_named, accountName)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.account_include_available), fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    if (included) R.string.account_included_supporting else R.string.account_excluded_supporting,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = included,
            onCheckedChange = onIncludedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = switchLabel },
        )
    }
}

@Composable
private fun accountIssueMessage(issue: AccountMutationResult?): String? =
    when (issue) {
        AccountMutationResult.InvalidName -> stringResource(R.string.account_invalid_name)
        AccountMutationResult.DuplicateName -> stringResource(R.string.account_duplicate_name)
        AccountMutationResult.InvalidCurrency -> stringResource(R.string.entry_invalid_currency)
        AccountMutationResult.InvalidAmount -> stringResource(R.string.entry_invalid_amount)
        AccountMutationResult.InUse -> stringResource(R.string.account_in_use)
        AccountMutationResult.Missing -> stringResource(R.string.account_missing)
        AccountMutationResult.StorageFailed -> stringResource(R.string.account_storage_failed)
        AccountMutationResult.Saved,
        AccountMutationResult.Deleted,
        null,
        -> null
    }

@Composable
private fun AccountMessageScreen(
    title: String,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    AccountScaffold(screenTitle = title, onBack = onBack) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        if (body == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.home_try_again)) }
        }
    }
}

@Composable
private fun AccountScaffold(
    screenTitle: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().semantics { paneTitle = screenTitle },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, enabled = backEnabled) {
                Text(stringResource(R.string.entry_back))
            }
            content()
        }
    }
}
