package com.reminder.local.domain.usecase

import android.util.Log
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import javax.inject.Inject

/**
 * 开机广播 / App 冷启动时调用：
 * 1. 把"已过期但仍是 PENDING 的非重复提醒"标记为 EXPIRED；
 * 2. 给所有仍是 PENDING 的提醒重新注册 AlarmManager 闹钟（系统重启后闹钟全部失效，必须重新注册）；
 * 3. 重复提醒如果 nextTriggerTime 已经过去（比如手机关机了很久），要"追赶"到未来的下一个时间点，
 *    而不是拿一个已经过去的时间去注册闹钟。
 */
class RescheduleAllAlarmsUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke() {
        val now = System.currentTimeMillis()
        repository.markNonRepeatingExpired(now)

        val pendings = repository.getAllPending()
        logDebug("重建闹钟开始，待处理 ${pendings.size} 条 PENDING 提醒")
        if (!alarmScheduler.canScheduleExactAlarms()) {
            logWarn("精确闹钟权限未开启，本次不重建未来系统闹钟")
            return
        }
        for (reminder in pendings) {
            runCatching { rescheduleOne(reminder, now) }
                .onFailure { logError("单条提醒重建失败，继续处理后续 reminderId=${reminder.id}", it) }
        }
        logDebug("重建闹钟结束")
    }

    private suspend fun rescheduleOne(
        reminder: com.reminder.local.domain.model.Reminder,
        now: Long
    ) {
        if (reminder.repeatType == RepeatType.NONE) {
            if (reminder.effectiveTime > now) alarmScheduler.scheduleExact(reminder)
            return
        }

        var next = reminder.effectiveTime
        var shouldStop = reminder.repeatEndDate?.let { next > it } == true
        while (!shouldStop && next <= now) {
            val candidate = RepeatCalculator.computeNext(reminder.triggerTime, next, reminder.repeatType)
            if (candidate == null || reminder.repeatEndDate?.let { candidate > it } == true) {
                shouldStop = true
            } else {
                next = candidate
            }
        }

        if (shouldStop) {
            val finished =
                reminder.copy(
                    status = ReminderStatus.DONE,
                    completedAt = now,
                    updatedAt = now
                )
            if (repository.updateIfOccurrenceCurrent(finished, reminder.effectiveTime)) {
                runCatching { alarmScheduler.cancel(reminder) }
            }
            return
        }

        if (next == reminder.effectiveTime) {
            alarmScheduler.scheduleExact(reminder)
            return
        }

        val updated = reminder.copy(nextTriggerTime = next, updatedAt = now)
        if (!repository.updateIfOccurrenceCurrent(updated, reminder.effectiveTime)) return
        try {
            alarmScheduler.scheduleExact(updated)
        } catch (scheduleError: Exception) {
            runCatching { alarmScheduler.cancel(updated) }
            val rolledBack = runCatching {
                repository.updateIfOccurrenceCurrent(reminder, next)
            }.getOrDefault(false)
            if (!rolledBack) {
                logError(
                    "重建调度失败后数据库回滚未生效 reminderId=${reminder.id}",
                    IllegalStateException("occurrence 已被其他操作修改")
                )
            }
            throw scheduleError
        }
    }

    private companion object {
        const val TAG = "RescheduleAllAlarmsUseCase"
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }
}
