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
    suspend operator fun invoke(reminder: Reminder, scope: RepeatActionScope): Boolean {
        val current = repository.getById(reminder.id) ?: return false
        if (!current.isRepeating || scope == RepeatActionScope.ALL) {
            return runCatching { repository.delete(current) }
                .fold(
                    onSuccess = {
                        runCatching { alarmScheduler.cancel(current) }
                            .onFailure { logError("删除后取消系统闹钟失败 reminderId=${current.id}", it) }
                        true
                    },
                    onFailure = {
                        logError("删除提醒数据库操作失败 reminderId=${current.id}", it)
                        false
                    }
                )
        }

        // 仅删除本次重复：跳到下一次触发时间，记录本身不删除。
        val next = RepeatCalculator.computeNext(
            current.triggerTime,
            current.effectiveTime,
            current.repeatType
        )
        val exceededEnd = current.repeatEndDate != null && next != null && next > current.repeatEndDate
        if (next == null || exceededEnd) {
            return runCatching { repository.delete(current) }
                .fold(
                    onSuccess = {
                        runCatching { alarmScheduler.cancel(current) }
                        true
                    },
                    onFailure = { false }
                )
        }

        val from = current.effectiveTime
        val updated = current.copy(nextTriggerTime = next, updatedAt = System.currentTimeMillis())
        val persisted = runCatching { repository.updateIfOccurrenceCurrent(updated, from) }
            .onFailure { logError("删除本次条件更新失败 reminderId=${current.id}", it) }
            .getOrDefault(false)
        if (!persisted) return false
        return try {
            alarmScheduler.replaceExact(current, updated)
            true
        } catch (scheduleError: Exception) {
            rollbackOccurrence(current, next)
            logError("删除本次后重新调度失败 reminderId=${current.id}", scheduleError)
            false
        }
    }

    private suspend fun rollbackOccurrence(previous: Reminder, failedOccurrence: Long) {
        val rolledBack = runCatching {
            repository.updateIfOccurrenceCurrent(previous, failedOccurrence)
        }.getOrDefault(false)
        if (!rolledBack) {
            logError(
                "重新调度失败后数据库回滚未生效 reminderId=${previous.id}",
                IllegalStateException("occurrence 已被其他操作修改")
            )
        }
    }

    private companion object {
        const val TAG = "DeleteReminderUseCase"
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }
}
