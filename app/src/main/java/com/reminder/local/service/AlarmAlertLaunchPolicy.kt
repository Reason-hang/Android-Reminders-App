package com.reminder.local.service

enum class AlarmBackgroundLaunchMode {
    LEGACY,
    ALLOW_WHILE_ALARM_ACTIVE,
    ALLOW_ALWAYS
}

object AlarmAlertLaunchPolicy {
    const val WAKE_SCREEN_TIMEOUT_MILLIS: Long = 15_000L

    fun needsBackgroundActivityLaunchOptions(sdkInt: Int): Boolean = sdkInt >= 34

    fun backgroundLaunchMode(sdkInt: Int): AlarmBackgroundLaunchMode = when {
        sdkInt >= 36 -> AlarmBackgroundLaunchMode.ALLOW_ALWAYS
        sdkInt >= 34 -> AlarmBackgroundLaunchMode.ALLOW_WHILE_ALARM_ACTIVE
        else -> AlarmBackgroundLaunchMode.LEGACY
    }
}
