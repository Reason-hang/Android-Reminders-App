package com.reminder.local.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTriggerPolicyTest {

    @Test
    fun advanceAndDueAlarmsBothStartStrongAlert() {
        assertTrue(AlarmTriggerPolicy.shouldStartStrongAlert(AlarmReceiver.KIND_ADVANCE))
        assertTrue(AlarmTriggerPolicy.shouldStartStrongAlert(AlarmReceiver.KIND_DUE))
    }

    @Test
    fun onlyDueAlarmProgressesRepeatingReminder() {
        assertFalse(AlarmTriggerPolicy.shouldProgressRepeatingReminder(AlarmReceiver.KIND_ADVANCE))
        assertTrue(AlarmTriggerPolicy.shouldProgressRepeatingReminder(AlarmReceiver.KIND_DUE))
    }

    @Test
    fun advanceAndDueFallbacksRemainAudibleStrongAlerts() {
        assertTrue(AlarmTriggerPolicy.shouldUseNoisyFallback(AlarmReceiver.KIND_ADVANCE))
        assertTrue(AlarmTriggerPolicy.shouldUseNoisyFallback(AlarmReceiver.KIND_DUE))
    }
}
