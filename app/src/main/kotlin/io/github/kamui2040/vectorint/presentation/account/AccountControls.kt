package io.github.kamui2040.vectorint.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.presentation.component.InfoHeading

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountSelector(
    accounts: List<Account>,
    selectedAccountId: AccountId,
    enabled: Boolean,
    onAccountChange: (AccountId) -> Unit,
) {
    if (accounts.size <= 1) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoHeading(
            title = stringResource(R.string.account_entry_section),
            help = stringResource(R.string.account_entry_help),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEach { account ->
                FilterChip(
                    selected = account.id == selectedAccountId,
                    onClick = { onAccountChange(account.id) },
                    enabled = enabled,
                    label = { Text(account.name) },
                )
            }
        }
    }
}
