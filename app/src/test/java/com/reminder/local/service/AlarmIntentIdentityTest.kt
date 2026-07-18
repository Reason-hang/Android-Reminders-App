package com.reminder.local.service

import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmIntentIdentityTest {

    @Test
    fun differentRemindersCannotShareTriggerOrShowIdentity() {
        assertNotEquals(
            AlarmIntentIdentity.trigger(reminderId = 1L, kind = "due"),
            AlarmIntentIdentity.trigger(reminderId = 2L, kind = "advance")
        )
        assertNotEquals(
            AlarmIntentIdentity.show(reminderId = 1L, kind = "due"),
            AlarmIntentIdentity.show(reminderId = 2L, kind = "advance")
        )
    }

    @Test
    fun alertAndActionIdentitiesIncludeReminderAndOccurrence() {
        assertNotEquals(
            AlarmIntentIdentity.alert(reminderId = 1L, kind = AlarmAlertKind.DUE),
            AlarmIntentIdentity.alert(reminderId = 1L, kind = AlarmAlertKind.ADVANCE)
        )
        assertNotEquals(
            AlarmIntentIdentity.action(reminderId = 1L, action = "close"),
            AlarmIntentIdentity.action(reminderId = 2L, action = "close")
        )
    }
}
