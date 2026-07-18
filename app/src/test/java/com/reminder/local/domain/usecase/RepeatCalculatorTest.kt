package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.RepeatType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatCalculatorTest {

    private val zone = ZoneId.systemDefault()

    @Test
    fun noneHasNoNextTrigger() {
        val trigger = millis(2026, 7, 7, 9, 30)

        assertNull(RepeatCalculator.computeNext(trigger, trigger, RepeatType.NONE))
    }

    @Test
    fun hourlyAddsOneHour() {
        val trigger = millis(2026, 7, 7, 9, 30)

        val next = RepeatCalculator.computeNext(trigger, trigger, RepeatType.HOURLY)

        assertEquals(millis(2026, 7, 7, 10, 30), next)
    }

    @Test
    fun everyFiveHoursAddsFiveHours() {
        val trigger = millis(2026, 7, 7, 21, 30)

        val next = RepeatCalculator.computeNext(trigger, trigger, RepeatType.EVERY_FIVE_HOURS)

        assertEquals(millis(2026, 7, 8, 2, 30), next)
    }

    @Test
    fun weeklySundayMovesToNextSundayAtOriginalTime() {
        val trigger = millis(2026, 7, 7, 9, 30)

        val next = RepeatCalculator.computeNext(trigger, trigger, RepeatType.WEEKLY_SUNDAY)

        assertEquals(millis(2026, 7, 12, 9, 30), next)
    }

    @Test
    fun weekendMovesSaturdayToSundayAndSundayToNextSaturday() {
        val saturday = millis(2026, 7, 11, 9, 30)
        val sunday = millis(2026, 7, 12, 9, 30)

        val sundayNext = RepeatCalculator.computeNext(saturday, saturday, RepeatType.WEEKEND)
        val nextSaturday = RepeatCalculator.computeNext(sunday, sunday, RepeatType.WEEKEND)

        assertEquals(sunday, sundayNext)
        assertEquals(millis(2026, 7, 18, 9, 30), nextSaturday)
    }

    @Test
    fun everyTwoWeeksAddsTwoWeeks() {
        val trigger = millis(2026, 7, 7, 9, 30)

        val next = RepeatCalculator.computeNext(trigger, trigger, RepeatType.BIWEEKLY)

        assertEquals(millis(2026, 7, 21, 9, 30), next)
    }

    @Test
    fun workdaysUseBeijingMondayToFriday() {
        val monday = beijingMillis(2026, 7, 6, 9, 30)
        val friday = beijingMillis(2026, 7, 10, 9, 30)

        val tuesdayNext = RepeatCalculator.computeNext(monday, monday, RepeatType.WORKDAYS)
        val nextMonday = RepeatCalculator.computeNext(friday, friday, RepeatType.WORKDAYS)

        assertEquals(beijingMillis(2026, 7, 7, 9, 30), tuesdayNext)
        assertEquals(beijingMillis(2026, 7, 13, 9, 30), nextMonday)
    }

    @Test
    fun multiMonthRulesPreserveOriginalDayWhenPossible() {
        val trigger = millis(2026, 1, 31, 9, 30)
        val february = RepeatCalculator.computeNext(trigger, trigger, RepeatType.QUARTERLY)
        val july = RepeatCalculator.computeNext(trigger, trigger, RepeatType.SEMIANNUALLY)

        assertEquals(millis(2026, 4, 30, 9, 30), february)
        assertEquals(millis(2026, 7, 31, 9, 30), july)
    }

    @Test
    fun yearlyClipsLeapDayToFebruaryTwentyEight() {
        val trigger = millis(2024, 2, 29, 9, 30)

        val next = RepeatCalculator.computeNext(trigger, trigger, RepeatType.YEARLY)

        assertEquals(millis(2025, 2, 28, 9, 30), next)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun beijingMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
}
