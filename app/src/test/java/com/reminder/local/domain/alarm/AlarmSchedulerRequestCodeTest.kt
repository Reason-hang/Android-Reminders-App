package com.reminder.local.domain.alarm

import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmSchedulerRequestCodeTest {

    @Test
    fun advanceAndDueAlarmsUseDifferentPendingIntentRequestCodes() {
        val alarmId = 123456
        assertNotEquals(alarmId, AlarmSchedulerImpl.advanceAlarmRequestCode(alarmId))
        assertNotEquals(alarmId, AlarmSchedulerImpl.snoozeAlarmRequestCode(alarmId))
        assertNotEquals(
            AlarmSchedulerImpl.advanceAlarmRequestCode(alarmId),
            AlarmSchedulerImpl.snoozeAlarmRequestCode(alarmId)
        )
    }

    @Test
    fun advanceAndDueShowIntentsUseDifferentPendingIntentRequestCodes() {
        val alarmId = 123456
        assertNotEquals(
            AlarmSchedulerImpl.showActivityRequestCode(alarmId, "due"),
            AlarmSchedulerImpl.showActivityRequestCode(alarmId, "advance")
        )
        assertNotEquals(
            AlarmSchedulerImpl.showActivityRequestCode(alarmId, "due"),
            AlarmSchedulerImpl.showActivityRequestCode(alarmId, "snooze")
        )
    }
}
