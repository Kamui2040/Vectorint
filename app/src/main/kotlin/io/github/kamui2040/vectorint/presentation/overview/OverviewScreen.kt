package io.github.kamui2040.vectorint.presentation.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.presentation.category.categoryInfo
import io.github.kamui2040.vectorint.presentation.category.imageVector
import io.github.kamui2040.vectorint.presentation.component.MonthBrowser
import io.github.kamui2040.vectorint.presentation.theme.flowColors

@Composable
internal fun OverviewScreen(
    state: OverviewUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSetCurrentFunds: () -> Unit = {},
    selectedMonthIsCurrent: Boolean = true,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onCurrentMonth: () -> Unit = {},
) {
    val screenTitle = stringResource(R.string.navigation_overview)
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
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MonthBrowser(
                monthLabel = state.monthLabel,
                selectedMonthIsCurrent = selectedMonthIsCurrent,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCurrentMonth = onCurrentMonth,
            )
            when (state) {
                is OverviewUiState.Loading -> OverviewLoading()
                is OverviewUiState.NeedsCurrentFunds ->
                    OverviewMessageCard(
                        title = stringResource(R.string.overview_needs_funds_title),
                        body = stringResource(R.string.overview_needs_funds_body),
                        actionLabel = stringResource(R.string.overview_set_current_funds),
                        onAction = onSetCurrentFunds,
                    )

                is OverviewUiState.Empty ->
                    OverviewMessageCard(
                        title = stringResource(R.string.overview_empty_title),
                        body = stringResource(R.string.overview_empty_body),
                    )

                is OverviewUiState.Ready -> OverviewReady(state)
                is OverviewUiState.Unsafe ->
                    OverviewMessageCard(
                        title = stringResource(R.string.overview_unavailable),
                        body = stringResource(R.string.overview_unsafe_body),
                        actionLabel = stringResource(R.string.overview_try_again),
                        onAction = onRetry,
                    )

                is OverviewUiState.LoadFailed ->
                    OverviewMessageCard(
                        title = stringResource(R.string.overview_load_failed),
                        body = stringResource(R.string.overview_load_failed_body),
                        actionLabel = stringResource(R.string.overview_try_again),
                        onAction = onRetry,
                    )
            }
        }
    }
}

@Composable
private fun OverviewLoading() {
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
                text = stringResource(R.string.overview_loading),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun OverviewMessageCard(
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
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun OverviewReady(state: OverviewUiState.Ready) {
    val flowColors = MaterialTheme.flowColors
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sliceColors = overviewSliceColors(state.slices.size, darkTheme)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = flowColors.expenseContainer,
            contentColor = flowColors.onExpenseContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.overview_total_expenses),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = state.totalExpenses,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.overview_by_category),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ExpensePieChart(
                    slices = state.slices,
                    colors = sliceColors,
                    contentDescription = stringResource(R.string.overview_chart_description),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Column {
                    state.slices.forEachIndexed { index, slice ->
                        val info = categoryInfo(slice.categoryId, state.customCategories)
                        OverviewLegendRow(
                            name = info.name,
                            icon = info.icon.imageVector(),
                            amount = slice.amount,
                            share = slice.share,
                            color = sliceColors[index % sliceColors.size],
                        )
                        if (index != state.slices.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensePieChart(
    slices: List<OverviewSliceUi>,
    colors: List<Color>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .fillMaxWidth(0.78f)
                .sizeIn(maxWidth = 280.dp)
                .aspectRatio(1f)
                .semantics { this.contentDescription = contentDescription },
    ) {
        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweepAngle =
                if (index == slices.lastIndex) {
                    270f - startAngle
                } else {
                    slice.fraction * 360f
                }
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun OverviewLegendRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    amount: String,
    share: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
                    .clearAndSetSemantics {},
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                color = MaterialTheme.flowColors.onExpenseContainer,
            )
            Text(
                text = share,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun overviewSliceColors(
    count: Int,
    darkTheme: Boolean,
): List<Color> {
    require(count > 0) { "At least one category is required" }
    val lightness = if (darkTheme) 0.68f else 0.42f
    return List(count) { index ->
        Color.hsl(
            hue = (205f + index * 137.508f) % 360f,
            saturation = 0.72f,
            lightness = lightness,
        )
    }
}
