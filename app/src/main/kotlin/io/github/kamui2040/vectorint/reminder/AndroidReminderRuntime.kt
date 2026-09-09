package io.github.kamui2040.vectorint.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import io.github.kamui2040.vectorint.MainActivity
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.PlannedReminder
import io.github.kamui2040.vectorint.core.ReminderKind
import io.github.kamui2040.vectorint.data.ReminderAlarmGateway
import io.github.kamui2040.vectorint.data.ReminderNotificationGateway
import io.github.kamui2040.vectorint.presentation.format.RegionalFormatter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

internal const val REMINDER_INTENT_ACTION =
    "io.github.kamui2040.vectorint.action.OPEN_RECURRING_REMINDER"
internal const val REMINDER_ITEM_ID_EXTRA =
    "io.github.kamui2040.vectorint.extra.RECURRING_ITEM_ID"

internal const val REMINDER_ALARM_ACTION =
    "io.github.kamui2040.vectorint.action.REFRESH_REMINDERS"
private const val REMINDER_CHANNEL_ID = "vectorint_reminders_v1"
private const val REMINDER_NOTIFICATION_ID = 1
private const val REMINDER_NOTIFICATION_TAG_PREFIX = "vectorint.reminder."

internal class AndroidReminderAlarmGateway(
    private val context: Context,
) : ReminderAlarmGateway {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilli(),
            pendingIntent(),
        )
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderAlarmReceiver::class.java).setAction(REMINDER_ALARM_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

internal class AndroidNotificationPermissionGateway(
    private val context: Context,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun notificationsAllowed(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.areNotificationsEnabled()) {
            return false
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(REMINDER_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            return false
        }
        return true
    }

    fun runtimePermissionRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}

internal class AndroidReminderNotificationGateway(
    private val context: Context,
    private val permissionGateway: AndroidNotificationPermissionGateway,
    private val formatter: RegionalFormatter = RegionalFormatter(),
) : ReminderNotificationGateway {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        notificationManager.createNotificationChannel(channel)
    }

    override fun notificationsAllowed(): Boolean = permissionGateway.notificationsAllowed()

    override fun isActive(key: String): Boolean {
        val expectedTag = notificationTag(key)
        return notificationManager.activeNotifications.any { notification ->
            notification.id == REMINDER_NOTIFICATION_ID && notification.tag == expectedTag
        }
    }

    override fun post(reminder: PlannedReminder): Boolean {
        if (!notificationsAllowed()) return false
        return try {
            val encodedKey = reminder.key.encoded
            val tag = notificationTag(encodedKey)
            notificationManager.notify(tag, REMINDER_NOTIFICATION_ID, reminder.toNotification(encodedKey))
            isActive(encodedKey)
        } catch (_: RuntimeException) {
            false
        }
    }

    override fun cancelNotificationsNotIn(relevantKeys: Set<String>) {
        val relevantTags = relevantKeys.mapTo(hashSetOf(), ::notificationTag)
        notificationManager.activeNotifications
            .filter { notification ->
                notification.id == REMINDER_NOTIFICATION_ID &&
                    notification.tag?.startsWith(REMINDER_NOTIFICATION_TAG_PREFIX) == true &&
                    notification.tag !in relevantTags
            }.forEach { notification ->
                notificationManager.cancel(notification.tag, notification.id)
            }
    }

    private fun PlannedReminder.toNotification(encodedKey: String): Notification {
        val formattedDate = formatter.formatDate(key.anchorDate)
        val title =
            when (key.kind) {
                ReminderKind.OCCURRENCE -> context.getString(R.string.reminder_occurrence_title)
                ReminderKind.REMIND -> context.getString(R.string.reminder_remind_title)
                ReminderKind.END -> context.getString(R.string.reminder_end_title)
            }
        val body =
            when (key.kind) {
                ReminderKind.OCCURRENCE ->
                    context.getString(R.string.reminder_occurrence_body, itemName, formattedDate)
                ReminderKind.REMIND -> context.getString(R.string.reminder_remind_body, itemName, formattedDate)
                ReminderKind.END -> context.getString(R.string.reminder_end_body, itemName, formattedDate)
            }
        val publicVersion =
            newBuilder()
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminder_public_title))
                .setContentText(context.getString(R.string.reminder_public_body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()
        return newBuilder()
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent(encodedKey))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    setPriority(Notification.PRIORITY_DEFAULT)
                }
            }.build()
    }

    private fun newBuilder(): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, REMINDER_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

    private fun PlannedReminder.contentIntent(encodedKey: String): PendingIntent {
        val digest = encodedKey.digest()
        val requestCode =
            (
                ((digest[0].toInt() and 0xff) shl 24) or
                    ((digest[1].toInt() and 0xff) shl 16) or
                    ((digest[2].toInt() and 0xff) shl 8) or
                    (digest[3].toInt() and 0xff)
            ) and Int.MAX_VALUE
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(REMINDER_INTENT_ACTION)
                .setData(Uri.parse("vectorint://reminder/${digest.toHex()}"))
                .putExtra(REMINDER_ITEM_ID_EXTRA, key.itemId.value)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun notificationTag(encodedKey: String): String = REMINDER_NOTIFICATION_TAG_PREFIX + encodedKey.digest().toHex()

private fun String.digest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(toByteArray(StandardCharsets.UTF_8))

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
