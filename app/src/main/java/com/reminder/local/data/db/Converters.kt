package com.reminder.local.data.db

import androidx.room.TypeConverter
import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType

class Converters {

    @TypeConverter
    fun statusToString(value: ReminderStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): ReminderStatus = try {
        ReminderStatus.valueOf(value)
    } catch (_: IllegalArgumentException) {
        ReminderStatus.PENDING
    }

    @TypeConverter
    fun repeatTypeToString(value: RepeatType): String = value.name

    @TypeConverter
    fun stringToRepeatType(value: String): RepeatType = try {
        RepeatType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        RepeatType.NONE
    }

    @TypeConverter
    fun advanceReminderTypeToString(value: AdvanceReminderType): String = value.name

    @TypeConverter
    fun stringToAdvanceReminderType(value: String): AdvanceReminderType = try {
        AdvanceReminderType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AdvanceReminderType.NONE
    }

    @TypeConverter
    fun advanceReminderUnitToString(value: AdvanceReminderUnit): String = value.name

    @TypeConverter
    fun stringToAdvanceReminderUnit(value: String): AdvanceReminderUnit = try {
        AdvanceReminderUnit.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AdvanceReminderUnit.HOURS
    }
}
