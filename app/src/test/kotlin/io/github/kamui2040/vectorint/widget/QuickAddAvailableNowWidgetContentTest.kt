package io.github.kamui2040.vectorint.widget

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kamui2040.vectorint.MainActivity
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.presentation.home.ExpectedIncomeUi
import io.github.kamui2040.vectorint.presentation.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickAddAvailableNowWidgetContentTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `ready state contains only the Available now label and value`() {
        val content =
            QuickAddAvailableNowWidgetContent.from(
                HomeUiState.Ready(
                    monthLabel = "September 2026",
                    availableNow = "€650.00",
                    currentFunds = "€900.00",
                    reservedExpenses = "€250.00",
                    expectedIncome = ExpectedIncomeUi.Excluded,
                ),
                context,
            )

        val rendered = content.toRemoteViews(context, 17).apply(context, FrameLayout(context))

        assertEquals(2, (rendered as LinearLayout).childCount)
        assertEquals("Available now", rendered.text(R.id.widget_quick_add_headline))
        assertEquals("€650.00", rendered.text(R.id.widget_quick_add_value))
        assertNull(rendered.findViewById<View>(R.id.widget_brand))
        assertNull(rendered.findViewById<View>(R.id.widget_month))
        assertNull(rendered.findViewById<View>(R.id.widget_details))
        assertEquals(
            "Available now. €650.00. Tap to add an entry.",
            rendered.contentDescription.toString(),
        )
    }

    @Test
    fun `unsafe state fails closed without adding visual detail`() {
        val content =
            QuickAddAvailableNowWidgetContent.from(
                HomeUiState.Unsafe(
                    monthLabel = "September 2026",
                    reasons = setOf(UnsafeReason.CURRENCY_MISMATCH),
                ),
                context,
            )

        val rendered = content.toRemoteViews(context, 18).apply(context, FrameLayout(context))

        assertEquals("Available now", rendered.text(R.id.widget_quick_add_headline))
        assertEquals("—", rendered.text(R.id.widget_quick_add_value))
        assertEquals(
            "Available now. Amount unavailable. Tap to add an entry.",
            rendered.contentDescription.toString(),
        )
    }

    @Test
    fun `quick add intent targets the entry action in MainActivity`() {
        val intent = quickAddActivityIntent(context)

        assertEquals(QUICK_ADD_INTENT_ACTION, intent.action)
        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags,
        )
    }
}

private fun View.text(viewId: Int): String = findViewById<TextView>(viewId).text.toString()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de")
class GermanQuickAddAvailableNowWidgetContentTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `quick add accessibility copy follows German resources`() {
        val content =
            QuickAddAvailableNowWidgetContent.from(
                HomeUiState.LoadFailed(monthLabel = "September 2026"),
                context,
            )

        val rendered = content.toRemoteViews(context, 19).apply(context, FrameLayout(context))

        assertEquals("Jetzt verfügbar", rendered.text(R.id.widget_quick_add_headline))
        assertEquals("—", rendered.text(R.id.widget_quick_add_value))
        assertEquals(
            "Jetzt verfügbar. Aktualisierung fehlgeschlagen. Tippe, um einen Eintrag hinzuzufügen.",
            rendered.contentDescription.toString(),
        )
    }
}
