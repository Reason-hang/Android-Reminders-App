package com.reminder.local.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAlertConcurrencyPolicyTest {

    @Test
    fun differentIncomingAlarmRetainsCurrentBeforeReplacement() {
        assertTrue(AlarmAlertConcurrencyPolicy.shouldRetainCurrent(101, 202))
        assertFalse(AlarmAlertConcurrencyPolicy.shouldRetainCurrent(101, 101))
        assertFalse(AlarmAlertConcurrencyPolicy.shouldRetainCurrent(null, 202))
    }

    @Test
    fun staleCloseActionCannotStopNewerActiveAlarm() {
        assertFalse(AlarmAlertConcurrencyPolicy.actionTargetsCurrent(202, 101))
        assertTrue(AlarmAlertConcurrencyPolicy.actionTargetsCurrent(202, 202))
        assertTrue(AlarmAlertConcurrencyPolicy.actionTargetsCurrent(null, 101))
    }
}
