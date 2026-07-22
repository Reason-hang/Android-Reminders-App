package com.reminder.local.domain.alarm

import com.reminder.local.domain.model.Reminder

interface AlarmScheduler {
    /** 是否已获得精确闹钟权限（Android 12+）。 */
    fun canScheduleExactAlarms(): Boolean

    /** 按 reminder.effectiveTime（即 nextTriggerTime ?: triggerTime）注册一次精确闹钟。 */
    fun scheduleExact(reminder: Reminder)

    /** 用新计划替换旧计划；实现必须在失败时尽力保留/恢复旧计划。 */
    fun replaceExact(previous: Reminder, updated: Reminder) {
        scheduleExact(updated)
    }

    /** 取消该提醒对应的闹钟；如果本来就没有注册，是安全的空操作。 */
    fun cancel(reminder: Reminder)

    /** "稍后提醒"：在 delayMillis 后重新触发一次通知，不改变数据库里的 triggerTime/重复规则。 */
    fun scheduleSnooze(reminder: Reminder, delayMillis: Long)
}
