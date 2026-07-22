package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderScheduleValidatorTest {

    @Test
    fun advanceEqualToOrLongerThanRepeatCycleIsRejected() {
        val trigger = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        assertNotNull(
            ReminderScheduleValidator.validate(
                reminder(trigger, RepeatType.DAILY, AdvanceReminderType.ONE_DAY)
            )
        )
        assertNotNull(
            ReminderScheduleValidator.validate(
                reminder(trigger, RepeatType.WEEKLY, AdvanceReminderType.TWO_WEEKS)
            )
        )
    }

    @Test
    fun smallerAdvanceAndNonRepeatingReminderRemainValid() {
        val trigger = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        assertNull(
            ReminderScheduleValidator.validate(
                reminder(trigger, RepeatType.DAILY, AdvanceReminderType.FIVE_MINUTES)
            )
        )
        assertNull(
            ReminderScheduleValidator.validate(
                reminder(trigger, RepeatType.NONE, AdvanceReminderType.TWO_WEEKS)
            )
        )
    }

    private fun reminder(
        trigger: Long,
        repeatType: RepeatType,
        advanceType: AdvanceReminderType
    ) = Reminder(
        title = "校验测试",
        triggerTime = trigger,
        nextTriggerTime = trigger,
        repeatType = repeatType,
        advanceReminderType = advanceType
    )
}
