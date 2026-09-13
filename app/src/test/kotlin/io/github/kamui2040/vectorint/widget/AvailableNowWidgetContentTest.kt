package io.github.kamui2040.vectorint.widget

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.presentation.home.ExpectedIncomeUi
import io.github.kamui2040.vectorint.presentation.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AvailableNowWidgetContentTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `ready state keeps Available now primary and explains the active policy`() {
        val expectedIncomeOff =
            AvailableNowWidgetContent.from(
                HomeUiState.Ready(
                    monthLabel = "September 2026",
                    availableNow = "€650.00",
                    currentFunds = "€900.00",
                    reservedExpenses = "€250.00",
                    expectedIncome = ExpectedIncomeUi.Excluded,
                ),
                context,
            )
        val expectedIncomeOn =
            AvailableNowWidgetContent.from(
                HomeUiState.Ready(
                    monthLabel = "September 2026",
                    availableNow = "€750.00",
                    currentFunds = "€900.00",
                    reservedExpenses = "€250.00",
                    expectedIncome = ExpectedIncomeUi.Included("€100.00"),
                ),
                context,
            )

        assertEquals("Available now", expectedIncomeOff.headline)
        assertEquals("€650.00", expectedIncomeOff.value)
        assertEquals("Reserved: €250.00 · Expected income off", expectedIncomeOff.details)
        assertEquals("€750.00", expectedIncomeOn.value)
        assertEquals(
            "Reserved: €250.00 · Expected income: €100.00",
            expectedIncomeOn.details,
        )
    }

    @Test
    fun `unsafe and failed states never retain a reassuring amount`() {
        val unsafe =
            AvailableNowWidgetContent.from(
                HomeUiState.Unsafe(
                    monthLabel = "September 2026",
                    reasons = setOf(UnsafeReason.CURRENCY_MISMATCH),
                ),
                context,
            )
        val failed =
            AvailableNowWidgetContent.from(
                HomeUiState.LoadFailed(monthLabel = "September 2026"),
                context,
            )

        assertEquals("—", unsafe.value)
        assertEquals("Amount unavailable", unsafe.headline)
        assertEquals("Open Vectorint to review your budget data.", unsafe.details)
        assertEquals("—", failed.value)
        assertEquals("Couldn’t update", failed.headline)
        assertFalse(failed.details.contains("€"))
    }

    @Test
    fun `rendered widget contains the complete ready state and accessibility summary`() {
        val content =
            AvailableNowWidgetContent(
                monthLabel = "September 2026",
                headline = "Available now",
                value = "€650.00",
                details = "Reserved: €250.00 · Expected income off",
            )

        val rendered = content.toRemoteViews(context, 7).apply(context, FrameLayout(context))

        assertEquals("Vectorint", rendered.text(R.id.widget_brand))
        assertEquals("September 2026", rendered.text(R.id.widget_month))
        assertEquals("Available now", rendered.text(R.id.widget_headline))
        assertEquals("€650.00", rendered.text(R.id.widget_value))
        assertEquals(
            "Reserved: €250.00 · Expected income off",
            rendered.text(R.id.widget_details),
        )
        assertEquals(
            "September 2026. Available now. €650.00. Reserved: €250.00 · Expected income off",
            rendered.contentDescription.toString(),
        )
    }

    @Test
    fun `loading state hides empty supporting text`() {
        val content =
            AvailableNowWidgetContent.from(
                HomeUiState.Loading(monthLabel = "September 2026"),
                context,
            )

        val rendered = content.toRemoteViews(context, 8).apply(context, FrameLayout(context))

        assertEquals("Updating…", rendered.text(R.id.widget_headline))
        assertEquals(View.GONE, rendered.findViewById<View>(R.id.widget_details).visibility)
    }
}

private fun View.text(viewId: Int): String = findViewById<TextView>(viewId).text.toString()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de")
class GermanAvailableNowWidgetContentTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `widget action copy follows German resources`() {
        val content =
            AvailableNowWidgetContent.from(
                HomeUiState.NeedsCurrentFunds(monthLabel = "September 2026"),
                context,
            )

        assertEquals("Einrichtung nötig", content.headline)
        assertEquals("Öffne Vectorint und richte ein Konto ein.", content.details)
    }
}
