package com.reminder.local.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAlertLaunchPolicyTest {

    @Test
    fun backgroundActivityOptionsAreNeededFromAndroid14() {
        assertFalse(AlarmAlertLaunchPolicy.needsBackgroundActivityLaunchOptions(33))
        assertTrue(AlarmAlertLaunchPolicy.needsBackgroundActivityLaunchOptions(34))
        assertTrue(AlarmAlertLaunchPolicy.needsBackgroundActivityLaunchOptions(36))
    }

    @Test
    fun android16UsesExplicitAlarmBackgroundLaunchMode() {
        assertEquals(
            AlarmBackgroundLaunchMode.ALLOW_ALWAYS,
            AlarmAlertLaunchPolicy.backgroundLaunchMode(36)
        )
        assertEquals(
            AlarmBackgroundLaunchMode.ALLOW_WHILE_ALARM_ACTIVE,
            AlarmAlertLaunchPolicy.backgroundLaunchMode(34)
        )
        assertEquals(
            AlarmBackgroundLaunchMode.LEGACY,
            AlarmAlertLaunchPolicy.backgroundLaunchMode(33)
        )
    }

    @Test
    fun wakeLockIsShortLivedFallbackOnly() {
        assertEquals(15_000L, AlarmAlertLaunchPolicy.WAKE_SCREEN_TIMEOUT_MILLIS)
    }
}
