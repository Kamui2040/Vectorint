package io.github.kamui2040.vectorint.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import io.github.kamui2040.vectorint.MainActivity
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.VectorintApplication
import io.github.kamui2040.vectorint.data.RecurringOccurrenceCoordinator
import io.github.kamui2040.vectorint.presentation.home.ExpectedIncomeUi
import io.github.kamui2040.vectorint.presentation.home.HomeStateLoader
import io.github.kamui2040.vectorint.presentation.home.HomeUiState
import io.github.kamui2040.vectorint.presentation.home.RegionalHomeValueFormatter
import io.github.kamui2040.vectorint.presentation.settings.currentAppLanguageSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class AvailableNowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = loadAvailableNowWidgetSnapshot(context)
        val content = AvailableNowWidgetContent.from(snapshot.state, snapshot.textContext)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                content.toRemoteViews(snapshot.textContext, appWidgetId),
            )
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestWidgetUpdate(context, AvailableNowWidgetProvider::class.java)
        }
    }
}

internal data class AvailableNowWidgetSnapshot(
    val state: HomeUiState,
    val textContext: Context,
)

internal suspend fun loadAvailableNowWidgetSnapshot(context: Context): AvailableNowWidgetSnapshot {
    val application = context.applicationContext as VectorintApplication
    val state =
        HomeStateLoader(
            budgetRepository = application.budgetRepository,
            settingsRepository = application.settingsRepository,
            formatter = RegionalHomeValueFormatter(),
            occurrenceUpdater = RecurringOccurrenceCoordinator(application.budgetRepository),
        ).load()
    return AvailableNowWidgetSnapshot(
        state = state,
        textContext = context.widgetTextContext(),
    )
}

internal fun requestWidgetUpdate(
    context: Context,
    providerClass: Class<out AppWidgetProvider>,
) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(context, providerClass)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
    if (appWidgetIds.isEmpty()) return
    context.sendBroadcast(
        Intent(context, providerClass).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        },
    )
}

internal data class AvailableNowWidgetContent(
    val monthLabel: String,
    val headline: String,
    val value: String,
    val details: String,
) {
    fun toRemoteViews(
        context: Context,
        appWidgetId: Int,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_available_now).apply {
            setTextViewText(R.id.widget_brand, context.getString(R.string.app_name))
            setTextViewText(R.id.widget_month, monthLabel)
            setTextViewText(R.id.widget_headline, headline)
            setTextViewText(R.id.widget_value, value)
            setTextViewText(R.id.widget_details, details)
            setViewVisibility(R.id.widget_details, if (details.isBlank()) View.GONE else View.VISIBLE)
            setContentDescription(
                R.id.widget_root,
                context.getString(
                    R.string.widget_content_description,
                    monthLabel,
                    headline,
                    value,
                    details,
                ),
            )
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, appWidgetId))
        }

    companion object {
        fun from(
            state: HomeUiState,
            context: Context,
        ): AvailableNowWidgetContent =
            when (state) {
                is HomeUiState.Ready ->
                    AvailableNowWidgetContent(
                        monthLabel = state.monthLabel,
                        headline = context.getString(R.string.home_available_now),
                        value = state.availableNow,
                        details =
                            when (val expectedIncome = state.expectedIncome) {
                                ExpectedIncomeUi.Excluded ->
                                    context.getString(
                                        R.string.widget_ready_details_expected_income_off,
                                        state.reservedExpenses,
                                    )

                                is ExpectedIncomeUi.Included ->
                                    context.getString(
                                        R.string.widget_ready_details_expected_income_on,
                                        state.reservedExpenses,
                                        expectedIncome.amount,
                                    )
                            },
                    )

                is HomeUiState.NeedsCurrentFunds ->
                    actionRequired(
                        monthLabel = state.monthLabel,
                        headline = context.getString(R.string.widget_setup_needed),
                        value = context.getString(R.string.widget_empty_value),
                        details = context.getString(R.string.widget_set_current_funds),
                    )

                is HomeUiState.Unsafe ->
                    actionRequired(
                        monthLabel = state.monthLabel,
                        headline = context.getString(R.string.widget_amount_unavailable),
                        value = context.getString(R.string.widget_empty_value),
                        details = context.getString(R.string.widget_review_data),
                    )

                is HomeUiState.LoadFailed ->
                    actionRequired(
                        monthLabel = state.monthLabel,
                        headline = context.getString(R.string.widget_update_failed),
                        value = context.getString(R.string.widget_empty_value),
                        details = context.getString(R.string.widget_open_to_retry),
                    )

                is HomeUiState.Loading ->
                    actionRequired(
                        monthLabel = state.monthLabel,
                        headline = context.getString(R.string.widget_updating),
                        value = context.getString(R.string.widget_empty_value),
                        details = "",
                    )

                is HomeUiState.MonthOverview ->
                    actionRequired(
                        monthLabel = state.monthLabel,
                        headline = context.getString(R.string.widget_amount_unavailable),
                        value = context.getString(R.string.widget_empty_value),
                        details = context.getString(R.string.widget_open_app),
                    )
            }

        private fun actionRequired(
            monthLabel: String,
            headline: String,
            value: String,
            details: String,
        ): AvailableNowWidgetContent =
            AvailableNowWidgetContent(
                monthLabel = monthLabel,
                headline = headline,
                value = value,
                details = details,
            )
    }
}

private fun openAppIntent(
    context: Context,
    appWidgetId: Int,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        appWidgetId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

@SuppressLint("AppBundleLocaleChanges")
internal fun Context.widgetTextContext(): Context {
    val languageTag = currentAppLanguageSnapshot().languageTag ?: return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(languageTag))
    return createConfigurationContext(configuration)
}
