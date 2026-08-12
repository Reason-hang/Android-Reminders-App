package com.reminder.local.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAlertServiceSourceContractTest {

    @Test
    fun serviceOwnsInstanceScopedAutoStopLifecycle() {
        val source = File("src/main/java/com/reminder/local/service/AlarmAlertService.kt").readText()

        assertTrue(source.contains("scheduleAutoStop(incomingInstance)"))
        assertTrue(source.contains("timeoutTargetsCurrent"))
        assertTrue(source.contains("sendAutoDismissBroadcast(scheduledInstance)"))
        assertTrue(source.contains("showRetainedAlertNotification"))
    }

    @Test
    fun serviceUsesSystemFullScreenNotificationWithoutSelfLaunchingActivity() {
        val source = File("src/main/java/com/reminder/local/service/AlarmAlertService.kt").readText()

        assertTrue(source.contains("setFullScreenIntent(fullScreenPendingIntent, true)"))
        assertTrue(source.contains("notification_manager_delivery"))
        assertFalse(source.contains("channelId = alertChannelId\n                        )\n                    )\n                    true"))
        assertFalse(source.contains("launchAlarmActivity("))
        assertFalse(source.contains("wakeScreen("))
    }

    @Test
    fun serviceRecordsDistinctNotificationDismissalSource() {
        val source = File("src/main/java/com/reminder/local/service/AlarmAlertService.kt").readText()

        assertTrue(source.contains("STOP_SOURCE_NOTIFICATION_CLOSE_ACTION"))
        assertTrue(source.contains("STOP_SOURCE_NOTIFICATION_DISMISSED"))
        assertTrue(source.contains("setDeleteIntent(dismissedPendingIntent)"))
    }

    @Test
    fun unlockedRouteOwnsOverlayLifecycleAndAvoidsDuplicateFullScreenIntent() {
        val source = File("src/main/java/com/reminder/local/service/AlarmAlertService.kt").readText()

        assertTrue(source.contains("AlarmVisualRoutePolicy.decide"))
        assertTrue(source.contains("AlarmOverlayShowResult.SHOWN"))
        assertTrue(source.contains("if (overlayShown) null else fullScreenPendingIntent"))
        assertTrue(source.contains("dismissOverlay(\"timer_expired\")"))
        assertTrue(source.contains("STOP_SOURCE_OVERLAY_SNOOZE"))
    }
}
