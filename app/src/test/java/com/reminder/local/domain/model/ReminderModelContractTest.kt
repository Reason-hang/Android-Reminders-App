package com.reminder.local.domain.model

import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderModelContractTest {

    @Test
    fun priorityFieldIsNotPartOfReminderDomainModel() {
        assertFalse(Reminder::class.java.declaredFields.any { it.name == "priority" })
    }
}
