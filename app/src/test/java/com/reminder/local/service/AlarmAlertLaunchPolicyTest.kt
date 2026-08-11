package com.reminder.local.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmAlertLaunchPolicyTest {

    @Test
    fun strongAlertAutoStopsAfterTenMinutes() {
        assertEquals(600_000L, AlarmAlertLaunchPolicy.ALERT_AUTO_STOP_TIMEOUT_MILLIS)
    }
}
