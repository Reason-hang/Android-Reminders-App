package com.reminder.local.service

object AlarmAlertLaunchPolicy {
    const val WAKE_SCREEN_TIMEOUT_MILLIS: Long = 15_000L

    fun needsBackgroundActivityLaunchOptions(sdkInt: Int): Boolean = sdkInt >= 34
}
