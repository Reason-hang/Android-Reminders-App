package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import java.time.Instant
import java.time.ZoneId

object AdvanceReminderCalculator {

    private val zone: ZoneId = ZoneId.systemDefault()

    fun computeAdvanceTrigger(
        triggerTime: Long,
        type: AdvanceReminderType,
        customValue: Int = 1,
        customUnit: AdvanceReminderUnit = AdvanceReminderUnit.HOURS
    ): Long? {
        if (type == AdvanceReminderType.NONE) return null

        val dateTime = Instant.ofEpochMilli(triggerTime).atZone(zone).toLocalDateTime()
        val safeCustomValue = customValue.coerceIn(1, 200).toLong()
        val advance = when (type) {
            AdvanceReminderType.FIVE_MINUTES -> dateTime.minusMinutes(5)
            AdvanceReminderType.TEN_MINUTES -> dateTime.minusMinutes(10)
            AdvanceReminderType.FIFTEEN_MINUTES -> dateTime.minusMinutes(15)
            AdvanceReminderType.THIRTY_MINUTES -> dateTime.minusMinutes(30)
            AdvanceReminderType.ONE_HOUR -> dateTime.minusHours(1)
            AdvanceReminderType.TWO_HOURS -> dateTime.minusHours(2)
            AdvanceReminderType.THREE_HOURS -> dateTime.minusHours(3)
            AdvanceReminderType.ONE_DAY -> dateTime.minusDays(1)
            AdvanceReminderType.TWO_DAYS -> dateTime.minusDays(2)
            AdvanceReminderType.ONE_WEEK -> dateTime.minusWeeks(1)
            AdvanceReminderType.TWO_WEEKS -> dateTime.minusWeeks(2)
            AdvanceReminderType.ONE_MONTH -> dateTime.minusMonths(1)
            AdvanceReminderType.CUSTOM -> when (customUnit) {
                AdvanceReminderUnit.MINUTES -> dateTime.minusMinutes(safeCustomValue)
                AdvanceReminderUnit.HOURS -> dateTime.minusHours(safeCustomValue)
                AdvanceReminderUnit.DAYS -> dateTime.minusDays(safeCustomValue)
                AdvanceReminderUnit.WEEKS -> dateTime.minusWeeks(safeCustomValue)
                AdvanceReminderUnit.MONTHS -> dateTime.minusMonths(safeCustomValue)
            }
            AdvanceReminderType.NONE -> return null
        }
        return advance.atZone(zone).toInstant().toEpochMilli()
    }
}
