package com.reminder.local.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmNotificationPolicyTest {

    @Test
    fun foregroundServiceAndUserAlertUseSeparateNotifications() {
        assertEquals(Int.MAX_VALUE, AlarmNotificationPolicy.FOREGROUND_SERVICE_NOTIFICATION_ID)
        assertNotEquals(
            AlarmNotificationPolicy.FOREGROUND_SERVICE_CHANNEL_ID,
            AlarmNotificationPolicy.STRONG_ALERT_CHANNEL_ID
        )
    }

    @Test
    fun strongAndFallbackChannelsAreVersionedAndIndependent() {
        assertEquals("reminder_fullscreen_alert_v3", AlarmNotificationPolicy.STRONG_ALERT_CHANNEL_ID)
        assertEquals("reminder_alarm_fallback_v1", AlarmNotificationPolicy.NOISY_FALLBACK_CHANNEL_ID)
        assertNotEquals(
            AlarmNotificationPolicy.STRONG_ALERT_CHANNEL_ID,
            AlarmNotificationPolicy.NOISY_FALLBACK_CHANNEL_ID
        )
    }

    @Test
    fun fallbackChannelRespectsPerReminderSoundAndVibrationSettings() {
        assertEquals(
            AlarmNotificationPolicy.NOISY_FALLBACK_CHANNEL_ID,
            AlarmNotificationPolicy.fallbackChannelId(sound = true, vibrate = true)
        )
        assertEquals(
            AlarmNotificationPolicy.SOUND_FALLBACK_CHANNEL_ID,
            AlarmNotificationPolicy.fallbackChannelId(sound = true, vibrate = false)
        )
        assertEquals(
            AlarmNotificationPolicy.VIBRATION_FALLBACK_CHANNEL_ID,
            AlarmNotificationPolicy.fallbackChannelId(sound = false, vibrate = true)
        )
        assertEquals(
            AlarmNotificationPolicy.SILENT_FALLBACK_CHANNEL_ID,
            AlarmNotificationPolicy.fallbackChannelId(sound = false, vibrate = false)
        )
    }
}
