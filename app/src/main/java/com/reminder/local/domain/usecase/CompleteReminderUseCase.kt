package com.reminder.local.domain.usecase

import android.util.Log
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
            // 2026-07 第二轮复查修复：之前这里只有 onSuccess，调度失败时数据库不会更新
            // nextTriggerTime，但上面几行已经先 alarmScheduler.cancel() 把旧闹钟取消了——
            // 一旦 scheduleExact 失败，这条重复提醒就会变成"看起来还是 PENDING，实际上没有
            // 任何系统闹钟在等它"，且没有任何日志能发现。现在失败时至少打日志，方便定位。
            runCatching { alarmScheduler.scheduleExact(updated) }
                .onSuccess { repository.update(updated) }
                .onFailure { Log.e(TAG, "markDone(ONCE) 重新调度失败 reminderId=${reminder.id}", it) }
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
                .onFailure { Log.e(TAG, "markPending 重新调度失败 reminderId=${reminder.id}", it) }
        } else {
            // 时间已经过去了，不能对着过去的时间重新触发通知，只把状态还原成"已过期"。
            repository.update(
                reminder.copy(status = ReminderStatus.EXPIRED, completedAt = null, updatedAt = now)
            )
        }
    }

    private companion object {
        const val TAG = "CompleteReminderUseCase"
    }
}
