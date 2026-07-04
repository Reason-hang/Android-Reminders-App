package com.reminder.local.domain.usecase

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.ReminderStatus
import javax.inject.Inject

/**
 * 完成 / 撤销完成。重复提醒需要区分"仅本次"和"停止所有重复"。
 */
class CompleteReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {

    /** 勾选完成。 */
    suspend fun markDone(
        reminder: Reminder,
        scope: RepeatActionScope = RepeatActionScope.ALL
    ) {
        if (!reminder.isRepeating || scope == RepeatActionScope.ALL) {
            // 非重复提醒，或者重复提醒选择"停止所有重复"：彻底结束。
            if (reminder.status == ReminderStatus.PENDING) {
                alarmScheduler.cancel(reminder)
            }
            repository.update(
                reminder.copy(
                    status = ReminderStatus.DONE,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        // 重复提醒选择"仅完成本次"：跳到下一次触发时间，状态保持 PENDING。
        alarmScheduler.cancel(reminder)
        val from = reminder.effectiveTime
        val next = RepeatCalculator.computeNext(reminder.triggerTime, from, reminder.repeatType)
        val exceededEnd = reminder.repeatEndDate != null && next != null && next > reminder.repeatEndDate

        if (next == null || exceededEnd) {
            repository.update(
                reminder.copy(
                    status = ReminderStatus.DONE,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            val updated = reminder.copy(nextTriggerTime = next, updatedAt = System.currentTimeMillis())
            runCatching { alarmScheduler.scheduleExact(updated) }
                .onSuccess { repository.update(updated) }
        }
    }

    /** 撤销完成（从 DONE 变回 PENDING/EXPIRED）。 */
    suspend fun markPending(reminder: Reminder) {
        val now = System.currentTimeMillis()
        val effective = reminder.nextTriggerTime ?: reminder.triggerTime
        if (effective > now) {
            val updated = reminder.copy(
                status = ReminderStatus.PENDING,
                completedAt = null,
                updatedAt = now
            )
            runCatching { alarmScheduler.scheduleExact(updated) }
                .onSuccess { repository.update(updated) }
        } else {
            // 时间已经过去了，不能对着过去的时间重新触发通知，只把状态还原成"已过期"。
            repository.update(
                reminder.copy(status = ReminderStatus.EXPIRED, completedAt = null, updatedAt = now)
            )
        }
    }
}
