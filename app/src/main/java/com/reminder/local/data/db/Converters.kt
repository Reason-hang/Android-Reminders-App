package com.reminder.local.data.db

import androidx.room.TypeConverter
import com.reminder.local.domain.model.Priority
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType

class Converters {

    @TypeConverter
    fun priorityToString(value: Priority): String = value.name

    @TypeConverter
    fun stringToPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter
    fun statusToString(value: ReminderStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)

    @TypeConverter
    fun repeatTypeToString(value: RepeatType): String = value.name

    @TypeConverter
    fun stringToRepeatType(value: String): RepeatType = RepeatType.valueOf(value)
}
