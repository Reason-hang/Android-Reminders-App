package com.reminder.local.notification

object AlarmNotificationPolicy {
    const val FOREGROUND_SERVICE_NOTIFICATION_ID: Int = Int.MAX_VALUE
    const val FOREGROUND_SERVICE_CHANNEL_ID = "reminder_alarm_service_v1"
    const val STRONG_ALERT_CHANNEL_ID = "reminder_fullscreen_alert_v3"
    const val NOISY_FALLBACK_CHANNEL_ID = "reminder_alarm_fallback_v1"
    const val SOUND_FALLBACK_CHANNEL_ID = "reminder_alarm_fallback_sound_v1"
    const val VIBRATION_FALLBACK_CHANNEL_ID = "reminder_alarm_fallback_vibration_v1"
    const val SILENT_FALLBACK_CHANNEL_ID = "reminder_alarm_fallback_silent_v1"

    fun fallbackChannelId(sound: Boolean, vibrate: Boolean): String = when {
        sound && vibrate -> NOISY_FALLBACK_CHANNEL_ID
        sound -> SOUND_FALLBACK_CHANNEL_ID
        vibrate -> VIBRATION_FALLBACK_CHANNEL_ID
        else -> SILENT_FALLBACK_CHANNEL_ID
    }
}
