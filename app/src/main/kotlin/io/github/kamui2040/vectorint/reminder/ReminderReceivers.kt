package io.github.kamui2040.vectorint.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kamui2040.vectorint.VectorintApplication

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != REMINDER_ALARM_ACTION) return
        context.refreshReminders(goAsync())
    }
}

class ReminderSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        context.refreshReminders(goAsync())
    }

    private companion object {
        val SUPPORTED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
    }
}

private fun Context.refreshReminders(pendingResult: BroadcastReceiver.PendingResult) {
    val vectorintApplication = applicationContext as? VectorintApplication
    if (vectorintApplication == null) {
        pendingResult.finish()
        return
    }
    vectorintApplication.refreshReminders(onComplete = pendingResult::finish)
}
