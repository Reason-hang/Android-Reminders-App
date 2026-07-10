package com.reminder.local.domain.mapper

import com.reminder.local.data.db.entity.CategoryEntity
import com.reminder.local.data.db.entity.ReminderEntity
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.Reminder

fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    title = title,
    note = note,
    triggerTime = triggerTime,
    nextTriggerTime = nextTriggerTime,
    categoryId = categoryId,
    priority = priority,
    status = status,
    repeatType = repeatType,
    repeatEndDate = repeatEndDate,
    advanceReminderType = advanceReminderType,
    customAdvanceValue = customAdvanceValue,
    customAdvanceUnit = customAdvanceUnit,
    notifyVibrate = notifyVibrate,
    notifySound = notifySound,
    alarmId = alarmId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    title = title,
    note = note,
    triggerTime = triggerTime,
    nextTriggerTime = nextTriggerTime,
    categoryId = categoryId,
    priority = priority,
    status = status,
    repeatType = repeatType,
    repeatEndDate = repeatEndDate,
    advanceReminderType = advanceReminderType,
    customAdvanceValue = customAdvanceValue,
    customAdvanceUnit = customAdvanceUnit,
    notifyVibrate = notifyVibrate,
    notifySound = notifySound,
    alarmId = alarmId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    colorHex = colorHex,
    icon = icon,
    sortOrder = sortOrder,
    isDefault = isDefault
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    icon = icon,
    sortOrder = sortOrder,
    isDefault = isDefault
)
