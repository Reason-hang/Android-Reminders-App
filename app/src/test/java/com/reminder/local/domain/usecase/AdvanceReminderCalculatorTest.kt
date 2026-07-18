package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvanceReminderCalculatorTest {

    private val zone = ZoneId.systemDefault()

    @Test
    fun noneHasNoAdvanceTrigger() {
        val trigger = millis(2026, 7, 7, 9, 30)

        assertNull(AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.NONE))
    }

    @Test
    fun fixedOffsetsMoveBeforeTrigger() {
        val trigger = millis(2026, 7, 7, 9, 30)

        assertEquals(
            millis(2026, 7, 7, 9, 25),
            AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.FIVE_MINUTES)
        )
        assertEquals(
            millis(2026, 7, 7, 9, 20),
            AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.TEN_MINUTES)
        )
        assertEquals(
            millis(2026, 7, 7, 6, 30),
            AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.THREE_HOURS)
        )
        assertEquals(
            millis(2026, 6, 23, 9, 30),
            AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.TWO_WEEKS)
        )
        assertEquals(
            millis(2026, 7, 6, 9, 30),
            AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.ONE_DAY)
        )
        assertEquals(
            millis(2026, 6, 7, 9, 30),
            AdvanceReminderCalculator.computeAdvanceTrigger(trigger, AdvanceReminderType.ONE_MONTH)
        )
    }

    @Test
    fun customOffsetUsesValueAndUnit() {
        val trigger = millis(2026, 7, 7, 9, 30)

        assertEquals(
            millis(2026, 7, 7, 8, 30),
            AdvanceReminderCalculator.computeAdvanceTrigger(
                triggerTime = trigger,
                type = AdvanceReminderType.CUSTOM,
                customValue = 1,
                customUnit = AdvanceReminderUnit.HOURS
            )
        )
        assertEquals(
            millis(2026, 7, 6, 9, 30),
            AdvanceReminderCalculator.computeAdvanceTrigger(
                triggerTime = trigger,
                type = AdvanceReminderType.CUSTOM,
                customValue = 1,
                customUnit = AdvanceReminderUnit.DAYS
            )
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}
