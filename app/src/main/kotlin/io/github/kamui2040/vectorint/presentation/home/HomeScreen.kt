package io.github.kamui2040.vectorint.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.presentation.component.MonthBrowser
import io.github.kamui2040.vectorint.presentation.theme.flowColors

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSetCurrentFunds: () -> Unit = {},
    onEditCurrentFunds: () -> Unit = {},
    onAddActivity: () -> Unit = {},
    onViewRecurringItems: () -> Unit = {},
    selectedMonthIsCurrent: Boolean = true,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onCurrentMonth: () -> Unit = {},
) {
    val screenTitle = stringResource(R.string.navigation_home)
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .semantics { paneTitle = screenTitle },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MonthBrowser(
                monthLabel = state.monthLabel(),
                selectedMonthIsCurrent = selectedMonthIsCurrent,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCurrentMonth = onCurrentMonth,
            )
            when (state) {
                is HomeUiState.Loading -> LoadingContent()
                is HomeUiState.NeedsCurrentFunds -> NeedsCurrentFundsContent(onSetCurrentFunds)
                is HomeUiState.Ready ->
                    ReadyContent(
                        state = state,
                        onManageAccounts = onEditCurrentFunds,
                        onAddActivity = onAddActivity,
                        onViewRecurringItems = onViewRecurringItems,
                    )
                is HomeUiState.MonthOverview -> MonthOverviewContent(state)
                is HomeUiState.Unsafe -> UnsafeContent(onRetry)
                is HomeUiState.LoadFailed -> LoadFailedContent(onRetry)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun NeedsCurrentFundsContent(onSetAccounts: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_onboarding_step),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.home_current_funds_needed),
                modifier =
                    Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_current_funds_needed_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onSetAccounts,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_set_current_funds))
            }
        }
    }
}

@Composable
private fun UnsafeContent(onRetry: () -> Unit) {
    MessageCard(
        title = stringResource(R.string.home_available_unavailable),
        body = stringResource(R.string.home_unsafe_body),
        actionLabel = stringResource(R.string.home_try_again),
        onAction = onRetry,
    )
}

@Composable
private fun LoadFailedContent(onRetry: () -> Unit) {
    MessageCard(
        title = stringResource(R.string.home_load_failed),
        body = stringResource(R.string.home_load_failed_body),
        actionLabel = stringResource(R.string.home_try_again),
        onAction = onRetry,
    )
}

@Composable
private fun MonthOverviewContent(state: HomeUiState.MonthOverview) {
    val flowColors = MaterialTheme.flowColors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (state.relation == HomeMonthRelation.PAST) {
                                R.string.home_past_month_summary
                            } else {
                                R.string.home_upcoming_month_forecast
                            },
                        ),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_month_overview_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                DetailRow(
                    label = stringResource(R.string.home_month_income),
                    value = state.income,
                    valueColor = flowColors.onIncomeContainer,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(
                    label = stringResource(R.string.home_month_expenses),
                    value = state.expenses,
                    valueColor = flowColors.onExpenseContainer,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(
                    label = stringResource(R.string.home_month_net),
                    value = state.net,
                    valueColor =
                        if (state.netIsNegative) {
                            flowColors.onExpenseContainer
                        } else {
                            flowColors.onIncomeContainer
                        },
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                modifier =
                    Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    onManageAccounts: () -> Unit,
    onAddActivity: () -> Unit,
    onViewRecurringItems: () -> Unit,
) {
    val flowColors = MaterialTheme.flowColors
    var breakdownExpanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_available_now),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = state.availableNow,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_available_now_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Button(
            onClick = onAddActivity,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        ) {
            Text(
                text = stringResource(R.string.home_add_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                Surface(
                    onClick = { breakdownExpanded = !breakdownExpanded },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_breakdown),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector =
                                if (breakdownExpanded) {
                                    Icons.Rounded.ExpandLess
                                } else {
                                    Icons.Rounded.ExpandMore
                                },
                            contentDescription =
                                stringResource(
                                    if (breakdownExpanded) {
                                        R.string.home_hide_breakdown
                                    } else {
                                        R.string.home_show_breakdown
                                    },
                                ),
                        )
                    }
                }
                if (breakdownExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        DetailRow(
                            label = stringResource(R.string.home_current_funds),
                            value = state.currentFunds,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(R.string.home_reserved_expenses),
                            value = state.reservedExpenses,
                            valueColor = flowColors.onExpenseContainer,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        when (val expectedIncome = state.expectedIncome) {
                            ExpectedIncomeUi.Excluded ->
                                DetailRow(
                                    label = stringResource(R.string.home_expected_income),
                                    value = stringResource(R.string.home_not_included),
                                )

                            is ExpectedIncomeUi.Included ->
                                DetailRow(
                                    label = stringResource(R.string.home_expected_income),
                                    value = expectedIncome.amount,
                                    supporting = stringResource(R.string.home_included),
                                    valueColor = flowColors.onIncomeContainer,
                                )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onViewRecurringItems,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_recurring_items))
            }
            OutlinedButton(
                onClick = onManageAccounts,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_edit_current_funds))
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    supporting: String? = null,
    valueColor: Color? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun HomeUiState.monthLabel(): String =
    when (this) {
        is HomeUiState.Loading -> monthLabel
        is HomeUiState.NeedsCurrentFunds -> monthLabel
        is HomeUiState.Ready -> monthLabel
        is HomeUiState.MonthOverview -> monthLabel
        is HomeUiState.Unsafe -> monthLabel
        is HomeUiState.LoadFailed -> monthLabel
    }
