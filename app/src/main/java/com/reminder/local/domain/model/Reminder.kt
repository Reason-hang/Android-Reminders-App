package com.reminder.local.domain.model

/**
 * 纯 Kotlin 领域模型，不依赖 Room / Android。
 *
 * @param triggerTime      用户设置的"原始"触发时间（毫秒时间戳），重复提醒的重复规则
 *                         （比如每月同一天）以这个时间为模板基准，永远不变。
 * @param nextTriggerTime  真正下一次会触发闹钟的时间戳。非重复提醒时始终等于 triggerTime；
 *                         重复提醒每次触发后会被重新计算并写回数据库，AlarmManager 注册时
 *                         必须使用这个字段，而不是 triggerTime，否则重启后会用错时间。
 * @param alarmId          系统闹钟 PendingIntent 的 requestCode，单独维护，避免用数据库自增
 *                         Long 主键直接转 Int 造成溢出冲突。
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    val note: String? = null,
    val triggerTime: Long,
    val nextTriggerTime: Long? = null,
    val categoryId: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEndDate: Long? = null,
    val advanceReminderType: AdvanceReminderType = AdvanceReminderType.NONE,
    val customAdvanceValue: Int = 1,
    val customAdvanceUnit: AdvanceReminderUnit = AdvanceReminderUnit.HOURS,
    val notifyVibrate: Boolean = true,
    val notifySound: Boolean = true,
    val alarmId: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isRepeating: Boolean get() = repeatType != RepeatType.NONE

    /** 实际用于展示/调度的时间：重复提醒显示"下一次"，非重复提醒显示原始时间。 */
    val effectiveTime: Long get() = nextTriggerTime ?: triggerTime
}
