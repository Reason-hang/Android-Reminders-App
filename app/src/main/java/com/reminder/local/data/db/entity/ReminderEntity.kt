package com.reminder.local.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["alarmId"], unique = true)]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val note: String? = null,
    val triggerTime: Long,
    val nextTriggerTime: Long? = null,
    val categoryId: Long? = null,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEndDate: Long? = null,
    val advanceReminderType: AdvanceReminderType = AdvanceReminderType.NONE,
    val customAdvanceValue: Int = 1,
    val customAdvanceUnit: AdvanceReminderUnit = AdvanceReminderUnit.HOURS,
    val notifyVibrate: Boolean = true,
    val notifySound: Boolean = true,
    val alarmId: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "completedAt")
    val completedAt: Long? = null
)
