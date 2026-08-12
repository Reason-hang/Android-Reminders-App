package com.reminder.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPresentationSourceContractTest {

    @Test
    fun fallbackNotificationUsesSystemFullScreenDeliveryWithoutSelfLaunchingActivity() {
        val source = File("src/main/java/com/reminder/local/receiver/AlarmReceiver.kt").readText()

        assertTrue(source.contains("setFullScreenIntent(fullScreenPendingIntent, true)"))
        assertFalse(source.contains("contentPendingIntent.send("))
    }

    @Test
    fun alarmPageShowsOverKeyguardWithoutRequestingDeviceUnlock() {
        val source = File("src/main/java/com/reminder/local/AlarmActivity.kt").readText()

        assertTrue(source.contains("setShowWhenLocked(true)"))
        assertTrue(source.contains("setTurnScreenOn(true)"))
        assertFalse(source.contains("requestDismissKeyguard"))
        assertFalse(source.contains("FLAG_DISMISS_KEYGUARD"))
    }

    @Test
    fun retainedNotificationAndLegacyEntryDoNotNavigateToEditor() {
        val helper = File("src/main/java/com/reminder/local/notification/NotificationHelper.kt").readText()
        val legacyEntry = File("src/main/java/com/reminder/local/ReminderEntryActivity.kt").readText()

        assertTrue(helper.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(helper.contains("NotificationCompat.Builder(context, CHANNEL_UNLOCKED_OVERLAY_ALERT)"))
        assertFalse(helper.contains("ReminderEntryActivity"))
        assertFalse(legacyEntry.contains("startReminderId"))
    }
}
