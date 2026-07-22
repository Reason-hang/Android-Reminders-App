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
            AlarmIntentIdentity.action(1L, 101, AlarmAlertKind.DUE, 1_000L, "close"),
            AlarmIntentIdentity.action(1L, 101, AlarmAlertKind.DUE, 2_000L, "close")
        )
    }
}
