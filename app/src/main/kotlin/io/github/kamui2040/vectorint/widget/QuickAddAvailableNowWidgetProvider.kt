package io.github.kamui2040.vectorint.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.kamui2040.vectorint.MainActivity
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.presentation.home.HomeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val QUICK_ADD_INTENT_ACTION =
    "io.github.kamui2040.vectorint.action.QUICK_ADD_ACTIVITY"

class QuickAddAvailableNowWidgetProvider : AppWidgetProvider() {
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
        val content = QuickAddAvailableNowWidgetContent.from(snapshot.state, snapshot.textContext)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                content.toRemoteViews(snapshot.textContext, appWidgetId),
            )
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestWidgetUpdate(context, QuickAddAvailableNowWidgetProvider::class.java)
        }
    }
}

internal data class QuickAddAvailableNowWidgetContent(
    val headline: String,
    val value: String,
    val accessibilityValue: String,
) {
    fun toRemoteViews(
        context: Context,
        appWidgetId: Int,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_available_now_quick_add).apply {
            setTextViewText(R.id.widget_quick_add_headline, headline)
            setTextViewText(R.id.widget_quick_add_value, value)
            setContentDescription(
                R.id.widget_quick_add_root,
                context.getString(
                    R.string.widget_quick_add_content_description,
                    headline,
                    accessibilityValue,
                ),
            )
            setOnClickPendingIntent(
                R.id.widget_quick_add_root,
                quickAddPendingIntent(context, appWidgetId),
            )
        }

    companion object {
        fun from(
            state: HomeUiState,
            context: Context,
        ): QuickAddAvailableNowWidgetContent {
            val headline = context.getString(R.string.home_available_now)
            return when (state) {
                is HomeUiState.Ready ->
                    QuickAddAvailableNowWidgetContent(
                        headline = headline,
                        value = state.availableNow,
                        accessibilityValue = state.availableNow,
                    )

                is HomeUiState.NeedsCurrentFunds ->
                    unavailable(
                        headline = headline,
                        accessibilityValue = context.getString(R.string.widget_setup_needed),
                        context = context,
                    )

                is HomeUiState.Unsafe,
                is HomeUiState.MonthOverview,
                ->
                    unavailable(
                        headline = headline,
                        accessibilityValue = context.getString(R.string.widget_amount_unavailable),
                        context = context,
                    )

                is HomeUiState.LoadFailed ->
                    unavailable(
                        headline = headline,
                        accessibilityValue = context.getString(R.string.widget_update_failed),
                        context = context,
                    )

                is HomeUiState.Loading ->
                    unavailable(
                        headline = headline,
                        accessibilityValue = context.getString(R.string.widget_updating),
                        context = context,
                    )
            }
        }

        private fun unavailable(
            headline: String,
            accessibilityValue: String,
            context: Context,
        ): QuickAddAvailableNowWidgetContent =
            QuickAddAvailableNowWidgetContent(
                headline = headline,
                value = context.getString(R.string.widget_empty_value),
                accessibilityValue = accessibilityValue,
            )
    }
}

internal fun quickAddActivityIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = QUICK_ADD_INTENT_ACTION
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

private fun quickAddPendingIntent(
    context: Context,
    appWidgetId: Int,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        appWidgetId,
        quickAddActivityIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
