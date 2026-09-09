package io.github.kamui2040.vectorint.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import io.github.kamui2040.vectorint.VectorintApplication
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.PlannedReminder
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.ReminderDeliveryKey
import io.github.kamui2040.vectorint.core.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidReminderRuntimeTest {
    private val application: VectorintApplication
        get() = RuntimeEnvironment.getApplication() as VectorintApplication

    @Test
    fun `manifest requests only reminder capabilities and keeps receivers private`() {
        val packageInfo =
            application.packageManager.getPackageInfo(
                application.packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS).toLong(),
                ),
            )

        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertEquals(
            setOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
            ),
            requestedPermissions.filterTo(mutableSetOf()) { it.startsWith("android.permission.") },
        )
        val appLocalPermissions = requestedPermissions - requestedPermissions.filter { it.startsWith("android.permission.") }.toSet()
        assertEquals(
            setOf("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
            appLocalPermissions,
        )
        val appLocalPermission =
            @Suppress("DEPRECATION")
            application.packageManager.getPermissionInfo(
                appLocalPermissions.single(),
                0,
            )
        assertEquals(
            android.content.pm.PermissionInfo.PROTECTION_SIGNATURE,
            appLocalPermission.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE,
        )
        val receivers = packageInfo.receivers.orEmpty().associateBy { it.name }
        assertFalse(receivers.getValue(ReminderAlarmReceiver::class.java.name).exported)
        assertFalse(receivers.getValue(ReminderSystemEventReceiver::class.java.name).exported)
    }

    @Test
    fun `permission gateway reflects the Android notification permission`() {
        val shadowApplication = shadowOf(application)
        val gateway = AndroidNotificationPermissionGateway(application)

        shadowApplication.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(gateway.runtimePermissionRequired())
        assertFalse(gateway.notificationsAllowed())

        shadowApplication.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(gateway.runtimePermissionRequired())
        assertTrue(gateway.notificationsAllowed())
    }

    @Test
    fun `notification is confirmed active and hides item details from public lock screen content`() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val permissionGateway = AndroidNotificationPermissionGateway(application)
        val gateway = AndroidReminderNotificationGateway(application, permissionGateway)
        gateway.createChannel()
        val reminder = reminder()

        assertTrue(gateway.post(reminder))
        assertTrue(gateway.isActive(reminder.key.encoded))

        val manager = application.getSystemService(NotificationManager::class.java)
        val channel = manager.notificationChannels.single { it.name == "Vectorint reminders" }
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        val notification = manager.activeNotifications.single().notification
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .startsWith("Synthetic item occurs on "),
        )
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertEquals(
            "Open Vectorint to view this reminder.",
            notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT),
        )

        gateway.cancelNotificationsNotIn(emptySet())
        assertFalse(gateway.isActive(reminder.key.encoded))
    }

    private fun reminder(): PlannedReminder {
        val anchor = LocalDate.of(2026, 9, 6)
        return PlannedReminder(
            key =
                ReminderDeliveryKey(
                    itemId = RecurringItemId("synthetic-item"),
                    kind = ReminderKind.OCCURRENCE,
                    anchorDate = anchor,
                    daysBefore = 0,
                ),
            itemName = "Synthetic item",
            direction = Direction.EXPENSE,
            notifyAt = Instant.parse("2026-09-06T09:00:00Z"),
            relevantUntil = anchor,
        )
    }
}
