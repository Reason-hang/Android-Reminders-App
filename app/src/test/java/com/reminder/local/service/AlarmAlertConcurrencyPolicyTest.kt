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
        val current = AlarmAlertInstanceKey(202, AlarmAlertKind.DUE, 2_000L)
        assertFalse(
            AlarmAlertConcurrencyPolicy.actionTargetsCurrent(
                current,
                AlarmAlertInstanceKey(101, AlarmAlertKind.DUE, 1_000L)
            )
        )
        assertFalse(
            AlarmAlertConcurrencyPolicy.actionTargetsCurrent(
                current,
                AlarmAlertInstanceKey(202, AlarmAlertKind.ADVANCE, 2_000L)
            )
        )
        assertTrue(AlarmAlertConcurrencyPolicy.actionTargetsCurrent(current, current))
        assertTrue(
            AlarmAlertConcurrencyPolicy.actionTargetsCurrent(
                null,
                AlarmAlertInstanceKey(101, AlarmAlertKind.DUE, 1_000L)
            )
        )
    }

    @Test
    fun incomingOccurrenceRestartsPlaybackWhenAnAlertIsAlreadyActive() {
        assertTrue(AlarmAlertConcurrencyPolicy.shouldRestartPlayback(101))
        assertFalse(AlarmAlertConcurrencyPolicy.shouldRestartPlayback(null))
    }
}
