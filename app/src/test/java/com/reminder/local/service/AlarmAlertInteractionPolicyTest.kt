package com.reminder.local.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAlertInteractionPolicyTest {

    @Test
    fun closeStopsInterruptionButKeepsNotificationRecord() {
        assertTrue(
            AlarmAlertInteractionPolicy.shouldKeepNotification(AlarmAlertAction.CLOSE)
        )
    }

    @Test
    fun snoozeAndDoneRemoveCurrentNotificationRecord() {
        assertFalse(
            AlarmAlertInteractionPolicy.shouldKeepNotification(AlarmAlertAction.SNOOZE)
        )
        assertFalse(
            AlarmAlertInteractionPolicy.shouldKeepNotification(AlarmAlertAction.MARK_DONE)
        )
    }
}
