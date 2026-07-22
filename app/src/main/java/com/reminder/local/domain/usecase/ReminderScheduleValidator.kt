package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatType

object ReminderScheduleValidator {
    fun validate(reminder: Reminder): String? {
        if (reminder.repeatType == RepeatType.NONE) return null
        val next = RepeatCalculator.computeNext(
            reminder.triggerTime,
            reminder.triggerTime,
            reminder.repeatType
        ) ?: return null
        val nextAdvance = AdvanceReminderCalculator.computeAdvanceTrigger(
            next,
            reminder.advanceReminderType,
            reminder.customAdvanceValue,
            reminder.customAdvanceUnit
        ) ?: return null
        return if (nextAdvance <= reminder.triggerTime) {
            "提前时间必须小于重复周期，否则后续提前提醒无法触发"
        } else {
            null
        }
    }
}
