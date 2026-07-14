package com.reminder.local.domain.usecase

import android.util.Log
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import javax.inject.Inject

/**
 * 删除提醒：取消闹钟 + 删除数据库记录，三步（含清通知栏残留，交给调用方在 UI 层处理）缺一不可。
 * 重复提醒的"仅删除本次"语义等价于"跳到下一次触发"，不会真的删掉这条记录。
 */
class DeleteReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(reminder: Reminder, scope: RepeatActionScope = RepeatActionScope.ALL) {
        if (!reminder.isRepeating || scope == RepeatActionScope.ALL) {
            alarmScheduler.cancel(reminder)
            repository.delete(reminder)
            return
        }

        // 仅删除本次重复：跳到下一次触发时间，记录本身不删除。
        alarmScheduler.cancel(reminder)
        val next = RepeatCalculator.computeNext(
            reminder.triggerTime,
            reminder.effectiveTime,
            reminder.repeatType
        )
        val exceededEnd = reminder.repeatEndDate != null && next != null && next > reminder.repeatEndDate
        if (next == null || exceededEnd) {
            repository.delete(reminder)
        } else {
            val updated = reminder.copy(nextTriggerTime = next, updatedAt = System.currentTimeMillis())
            // 2026-07 第二轮复查修复：调度失败时补日志，避免"仅删除本次"之后这条重复提醒
            // 静默变成没有系统闹钟支撑的僵尸记录。
            runCatching { alarmScheduler.scheduleExact(updated) }
                .onSuccess { repository.update(updated) }
                .onFailure { Log.e(TAG, "删除本次后重新调度失败 reminderId=${reminder.id}", it) }
        }
    }

    private companion object {
        const val TAG = "DeleteReminderUseCase"
    }
}
