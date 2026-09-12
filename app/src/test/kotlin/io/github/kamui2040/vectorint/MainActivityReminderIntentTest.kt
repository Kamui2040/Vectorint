package io.github.kamui2040.vectorint

import android.content.Intent
import io.github.kamui2040.vectorint.reminder.REMINDER_INTENT_ACTION
import io.github.kamui2040.vectorint.reminder.REMINDER_ITEM_ID_EXTRA
import io.github.kamui2040.vectorint.widget.QUICK_ADD_INTENT_ACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityReminderIntentTest {
    @Test
    fun `exact reminder intent opens its recurring item`() {
        val intent =
            Intent(REMINDER_INTENT_ACTION)
                .putExtra(REMINDER_ITEM_ID_EXTRA, "  synthetic-item  ")

        assertEquals("synthetic-item", reminderItemIdFrom(intent))
    }

    @Test
    fun `unrelated or empty intents cannot navigate`() {
        assertNull(reminderItemIdFrom(null))
        assertNull(reminderItemIdFrom(Intent("other.action").putExtra(REMINDER_ITEM_ID_EXTRA, "synthetic-item")))
        assertNull(reminderItemIdFrom(Intent(REMINDER_INTENT_ACTION)))
        assertNull(reminderItemIdFrom(Intent(REMINDER_INTENT_ACTION).putExtra(REMINDER_ITEM_ID_EXTRA, "  ")))
    }

    @Test
    fun `only the exact quick add action requests entry creation`() {
        assertTrue(quickAddRequestedFrom(Intent(QUICK_ADD_INTENT_ACTION)))
        assertFalse(quickAddRequestedFrom(null))
        assertFalse(quickAddRequestedFrom(Intent("other.action")))
    }

    @Test
    fun `quick add request is consumed so recreation cannot reopen it`() {
        val intent = Intent(QUICK_ADD_INTENT_ACTION)

        assertTrue(consumeQuickAddRequestFrom(intent))
        assertNull(intent.action)
        assertFalse(consumeQuickAddRequestFrom(intent))
    }
}
