package io.github.kamui2040.vectorint

import android.content.Intent
import io.github.kamui2040.vectorint.reminder.REMINDER_INTENT_ACTION
import io.github.kamui2040.vectorint.reminder.REMINDER_ITEM_ID_EXTRA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
